/*
 * Copyright (c) 2014-2016 by its authors. Some rights reserved.
 * See the project homepage at: https://monix.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package monix.eval

import monix.eval.Task._
import monix.execution.Ack.Stop
import monix.execution.cancelables.{CompositeCancelable, SingleAssignmentCancelable, StackedCancelable}
import monix.execution.rstreams.Subscription
import monix.execution.schedulers.ExecutionModel
import monix.execution.{Cancelable, CancelableFuture, Scheduler}
import monix.types.Evaluable
import org.reactivestreams.Subscriber
import monix.execution.atomic.{Atomic, AtomicAny, PaddingStrategy}
import scala.annotation.tailrec
import scala.collection.mutable
import scala.collection.generic.CanBuildFrom
import scala.collection.mutable.ListBuffer
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{Future, Promise, TimeoutException}
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

/** `Task` represents a specification for a possibly lazy or
  * asynchronous computation, which when executed will produce
  * an `A` as a result, along with possible side-effects.
  *
  * Compared with `Future` from Scala's standard library, `Task` does
  * not represent a running computation or a value detached from time,
  * as `Task` does not execute anything when working with its builders
  * or operators and it does not submit any work into any thread-pool,
  * the execution eventually taking place only after `runAsync`
  * is called and not before that.
  *
  * Note that `Task` is conservative in how it spawns logical threads.
  * Transformations like `map` and `flatMap` for example will default
  * to being executed on the logical thread on which the asynchronous
  * computation was started. But one shouldn't make assumptions about
  * how things will end up executed, as ultimately it is the
  * implementation's job to decide on the best execution model. All
  * you are guaranteed is asynchronous execution after executing
  * `runAsync`.
  */
sealed abstract class Task[+A] extends Serializable { self =>
  /** Triggers the asynchronous execution.
    *
    * @param cb is a callback that will be invoked upon completion.
    * @return a [[monix.execution.Cancelable Cancelable]] that can
    *         be used to cancel a running task
    */
  def runAsync(cb: Callback[A])(implicit s: Scheduler): Cancelable = {
    val conn = StackedCancelable()
    Task.unsafeStartNow[A](this, s, conn, Callback.safe(cb))
    conn
  }

  /** Triggers the asynchronous execution.
    *
    * @param f is a callback that will be invoked upon completion.
    * @return a [[monix.execution.Cancelable Cancelable]] that can
    *         be used to cancel a running task
    */
  def runAsync(f: Try[A] => Unit)(implicit s: Scheduler): Cancelable =
    runAsync(new Callback[A] {
      def onSuccess(value: A): Unit = f(Success(value))
      def onError(ex: Throwable): Unit = f(Failure(ex))
    })

  /** Triggers the asynchronous execution.
    *
    * @return a [[monix.execution.CancelableFuture CancelableFuture]]
    *         that can be used to extract the result or to cancel
    *         a running task.
    */
  def runAsync(implicit s: Scheduler): CancelableFuture[A] =
    Task.runAsCancelableFuture(s, this, Nil)

  /** Transforms a [[Task]] into a [[Coeval]] that tries to
    * execute the source synchronously, returning either `Right(value)`
    * in case a value is available immediately, or `Left(future)` in case
    * we have an asynchronous boundary.
    */
  def coeval(implicit s: Scheduler): Coeval[Either[CancelableFuture[A], A]] =
    Coeval.evalAlways {
      val f = this.runAsync(s)
      f.value match {
        case None => Left(f)
        case Some(Success(a)) => Right(a)
        case Some(Failure(ex)) => throw ex
      }
    }

  /** Creates a new Task by applying a function to the successful result
    * of the source Task, and returns a task equivalent to the result
    * of the function.
    */
  def flatMap[B](f: A => Task[B]): Task[B] =
    self match {
      case Now(a) =>
        Suspend(() => try f(a) catch { case NonFatal(ex) => Error(ex) })
      case eval @ EvalOnce(_) =>
        Suspend(() => eval.runAttempt match {
          case Now(a) => try f(a) catch { case NonFatal(ex) => Error(ex) }
          case error @ Error(_) => error
        })
      case EvalAlways(thunk) =>
        Suspend(() => try f(thunk()) catch {
          case NonFatal(ex) => Error(ex)
        })
      case Suspend(thunk) =>
        BindSuspend(thunk, f)
      case task @ MemoizeSuspend(_) =>
        BindSuspend(() => task, f)
      case BindSuspend(thunk, g) =>
        Suspend(() => BindSuspend(thunk, g andThen (_ flatMap f)))
      case Async(onFinish) =>
        BindAsync(onFinish, f)
      case BindAsync(listen, g) =>
        Suspend(() => BindAsync(listen, g andThen (_ flatMap f)))
      case error @ Error(_) =>
        error
    }

  /** Given a source Task that emits another Task, this function
    * flattens the result, returning a Task equivalent to the emitted
    * Task by the source.
    */
  def flatten[B](implicit ev: A <:< Task[B]): Task[B] =
    flatMap(a => a)

  /** Returns a task that waits for the specified `timespan` before
    * executing and mirroring the result of the source.
    */
  def delayExecution(timespan: FiniteDuration): Task[A] =
    Async { (scheduler, conn, cb) =>
      val c = SingleAssignmentCancelable()
      conn push c

      c := scheduler.scheduleOnce(timespan.length, timespan.unit, new Runnable {
        def run(): Unit = {
          conn.pop()
          Task.resume[A](scheduler, conn, self, cb, Nil)
        }
      })
    }

  /** Returns a task that waits for the specified `trigger` to succeed
    * before mirroring the result of the source.
    *
    * If the `trigger` ends in error, then the resulting task will also
    * end in error.
    */
  def delayExecutionWith(trigger: Task[Any]): Task[A] =
    Async { (scheduler, conn, cb) =>
      implicit val s = scheduler
      Task.unsafeStartNow(trigger, scheduler, conn, new Callback[Any] {
        def onSuccess(value: Any): Unit =
        // Async boundary forced, prevents stack-overflows
          Task.unsafeStartAsync(self, scheduler, conn, cb)
        def onError(ex: Throwable): Unit =
          cb.onError(ex)
      })
    }

  /** Returns a task that executes the source immediately on `runAsync`,
    * but before emitting the `onSuccess` result for the specified
    * duration.
    *
    * Note that if an error happens, then it is streamed immediately
    * with no delay.
    */
  def delayResult(timespan: FiniteDuration): Task[A] =
    Async { (scheduler, conn, cb) =>
      implicit val s = scheduler
      // Executing source
      Task.unsafeStartNow(self, scheduler, conn, new Callback[A] {
        def onSuccess(value: A): Unit = {
          val task = SingleAssignmentCancelable()
          conn push task

          // Delaying result
          task := scheduler.scheduleOnce(timespan.length, timespan.unit,
            new Runnable {
              def run(): Unit = {
                conn.pop()
                cb.onSuccess(value)
              }
            })
        }

        def onError(ex: Throwable): Unit =
          cb.onError(ex)
      })
    }

  /** Returns a task that executes the source immediately on `runAsync`,
    * but before emitting the `onSuccess` result for the specified
    * duration.
    *
    * Note that if an error happens, then it is streamed immediately
    * with no delay.
    */
  def delayResultBySelector[B](selector: A => Task[B]): Task[A] =
    Async { (scheduler, conn, cb) =>
      implicit val s = scheduler
      // Executing source
      Task.unsafeStartNow(self, scheduler, conn, new Callback[A] {
        def onSuccess(value: A): Unit = {
          var streamErrors = true
          try {
            val trigger = selector(value)
            streamErrors = false
            // Delaying result
            Task.unsafeStartAsync(trigger, scheduler, conn, new Callback[B] {
              def onSuccess(b: B): Unit = cb.onSuccess(value)
              def onError(ex: Throwable): Unit = cb.onError(ex)
            })
          } catch {
            case NonFatal(ex) if streamErrors =>
              cb.onError(ex)
          }
        }

        def onError(ex: Throwable): Unit =
          cb.onError(ex)
      })
    }

  /** Returns a failed projection of this task.
    *
    * The failed projection is a future holding a value of type
    * `Throwable`, emitting a value which is the throwable of the
    * original task in case the original task fails, otherwise if the
    * source succeeds, then it fails with a `NoSuchElementException`.
    */
  def failed: Task[Throwable] =
    materializeAttempt.flatMap {
      case Error(ex) => Now(ex)
      case Now(_) => Error(new NoSuchElementException("failed"))
    }

  /** Returns a new Task that applies the mapping function to
    * the element emitted by the source.
    */
  def map[B](f: A => B): Task[B] =
    flatMap(a => try Now(f(a)) catch { case NonFatal(ex) => Error(ex) })

  /** Returns a new `Task` in which `f` is scheduled to be run on completion.
    * This would typically be used to release any resources acquired by this
    * `Task`.
    *
    * The returned `Task` completes when both the source and the
    * task returned by `f` complete.
    */
  def doOnFinish(f: Option[Throwable] => Task[Unit]): Task[A] =
    materializeAttempt.flatMap {
      case Task.Now(value) =>
        f(None).map(_ => value)
      case Task.Error(ex) =>
        f(Some(ex)).flatMap(_ => Task.raiseError(ex))
    }

  /** Creates a new [[Task]] that will expose any triggered error from
    * the source.
    */
  def materialize: Task[Try[A]] =
    materializeAttempt.map(_.asScala)

  /** Creates a new [[Task]] that will expose any triggered error from
    * the source.
    */
  def materializeAttempt: Task[Attempt[A]] = {
    self match {
      case now @ Now(_) =>
        Now(now)
      case eval @ EvalOnce(_) =>
        Suspend(() => Now(eval.runAttempt))
      case EvalAlways(thunk) =>
        Suspend(() => Now(Attempt(thunk())))
      case Error(ex) =>
        Now(Error(ex))
      case Suspend(thunk) =>
        Suspend(() => try {
          thunk().materializeAttempt
        } catch { case NonFatal(ex) =>
          Now(Error(ex))
        })
      case task @ MemoizeSuspend(_) =>
        Async[Attempt[A]] { (s, conn, cb) =>
          Task.unsafeStartNow[A](task, s, conn, new Callback[A] {
            def onSuccess(value: A): Unit = cb.onSuccess(Now(value))
            def onError(ex: Throwable): Unit = cb.onSuccess(Error(ex))
          })
        }
      case BindSuspend(thunk, g) =>
        BindSuspend[Attempt[Any], Attempt[A]](
          () => try thunk().materializeAttempt catch {
            case NonFatal(ex) => Now(Error(ex))
          },
          result => result match {
            case Now(any) =>
              try {
                g.asInstanceOf[Any => Task[A]](any)
                  .materializeAttempt
              } catch {
                case NonFatal(ex) =>
                  Now(Error(ex))
              }
            case Error(ex) =>
              Now(Error(ex))
          })
      case Async(onFinish) =>
        Async((s, conn, cb) => onFinish(s, conn, new Callback[A] {
          def onSuccess(value: A): Unit = cb.onSuccess(Now(value))
          def onError(ex: Throwable): Unit = cb.onSuccess(Error(ex))
        }))
      case BindAsync(onFinish, g) =>
        BindAsync[Attempt[Any], Attempt[A]](
          (s, conn, cb) =>
            onFinish(s, conn, new Callback[Any] {
              def onSuccess(value: Any): Unit = cb.onSuccess(Now(value))
              def onError(ex: Throwable): Unit = cb.onSuccess(Error(ex))
            }),
          result => result match {
            case Now(any) =>
              try {
                g.asInstanceOf[Any => Task[A]](any)
                  .materializeAttempt
              }
              catch {
                case NonFatal(ex) =>
                  Now(Error(ex))
              }
            case Error(ex) =>
              Now(Error(ex))
          })
    }
  }

  /** Dematerializes the source's result from a `Try`. */
  def dematerialize[B](implicit ev: A <:< Try[B]): Task[B] =
    self.asInstanceOf[Task[Try[B]]].flatMap(Attempt.fromTry)

  /** Dematerializes the source's result from an `Attempt`. */
  def dematerializeAttempt[B](implicit ev: A <:< Attempt[B]): Task[B] =
    self.asInstanceOf[Task[Attempt[B]]].flatMap(identity)

  /** Creates a new task that will try recovering from an error by
    * matching it with another task using the given partial function.
    *
    * See [[onErrorHandleWith]] for the version that takes a total function.
    */
  def onErrorRecoverWith[B >: A](pf: PartialFunction[Throwable, Task[B]]): Task[B] =
    onErrorHandleWith(ex => pf.applyOrElse(ex, Task.raiseError))

  /** Creates a new task that will handle any matching throwable that
    * this task might emit by executing another task.
    *
    * See [[onErrorRecoverWith]] for the version that takes a partial function.
    */
  def onErrorHandleWith[B >: A](f: Throwable => Task[B]): Task[B] =
    self.materializeAttempt.flatMap {
      case now @ Now(_) => now
      case Error(ex) => try f(ex) catch { case NonFatal(err) => Error(err) }
    }

  /** Creates a new task that in case of error will fallback to the
    * given backup task.
    */
  def onErrorFallbackTo[B >: A](that: Task[B]): Task[B] =
    onErrorHandleWith(ex => that)

  /** Given a predicate function, keep retrying the
    * task until the function returns true.
    */
  def restartUntil(p: (A) => Boolean): Task[A] =
    self.flatMap(a => if (p(a)) Task.now(a) else self.restartUntil(p))

  /** Creates a new task that in case of error will retry executing the
    * source again and again, until it succeeds.
    *
    * In case of continuous failure the total number of executions
    * will be `maxRetries + 1`.
    */
  def onErrorRestart(maxRetries: Long): Task[A] =
    self.onErrorHandleWith(ex =>
      if (maxRetries > 0) self.onErrorRestart(maxRetries-1)
      else Error(ex))

  /** Creates a new task that in case of error will retry executing the
    * source again and again, until it succeeds.
    *
    * In case of continuous failure the total number of executions
    * will be `maxRetries + 1`.
    */
  def onErrorRestartIf(p: Throwable => Boolean): Task[A] =
    self.onErrorHandleWith(ex => if (p(ex)) self.onErrorRestartIf(p) else Error(ex))

  /** Creates a new task that will handle any matching throwable that
    * this task might emit.
    *
    * See [[onErrorRecover]] for the version that takes a partial function.
    */
  def onErrorHandle[U >: A](f: Throwable => U): Task[U] =
    onErrorHandleWith(ex => try Now(f(ex)) catch { case NonFatal(err) => Error(err) })

  /** Creates a new task that on error will try to map the error
    * to another value using the provided partial function.
    *
    * See [[onErrorHandle]] for the version that takes a total function.
    */
  def onErrorRecover[U >: A](pf: PartialFunction[Throwable, U]): Task[U] =
    onErrorRecoverWith(pf.andThen(Task.now))

  /** Memoizes the result on the computation and reuses it on subsequent
    * invocations of `runAsync`.
    */
  def memoize: Task[A] =
    self match {
      case ref @ Now(_) => ref
      case error @ Error(_) => error
      case EvalAlways(thunk) => new EvalOnce[A](thunk)
      case eval: EvalOnce[_] => self
      case Suspend(thunk) =>
        val evalOnce = EvalOnce(() => thunk().memoize)
        Suspend(evalOnce)
      case memoized: MemoizeSuspend[_] => self
      case other => new MemoizeSuspend[A](() => other)
    }

  /** Converts a [[Task]] to an `org.reactivestreams.Publisher` that
    * emits a single item on success, or just the error on failure.
    *
    * See [[http://www.reactive-streams.org/ reactive-streams.org]] for the
    * Reactive Streams specification.
    */
  def toReactivePublisher[B >: A](implicit s: Scheduler): org.reactivestreams.Publisher[B] =
    new org.reactivestreams.Publisher[B] {
      def subscribe(out: Subscriber[_ >: B]): Unit = {
        out.onSubscribe(new Subscription {
          private[this] var isActive = true
          private[this] val conn = StackedCancelable()

          def request(n: Long): Unit = {
            require(n > 0, "n must be strictly positive, according to " +
              "the Reactive Streams contract, rule 3.9")

            if (isActive) Task.unsafeStartNow[A](self, s, conn, Callback.safe(
              new Callback[A] {
                def onError(ex: Throwable): Unit =
                  out.onError(ex)

                def onSuccess(value: A): Unit = {
                  out.onNext(value)
                  out.onComplete()
                }
              }))
          }

          def cancel(): Unit = {
            isActive = false
            conn.cancel()
          }
        })
      }
    }

  /** Returns a Task that mirrors the source Task but that triggers a
    * `TimeoutException` in case the given duration passes without the
    * task emitting any item.
    */
  def timeout(after: FiniteDuration): Task[A] =
    timeoutTo(after, raiseError(new TimeoutException(s"Task timed-out after $after of inactivity")))

  /** Returns a Task that mirrors the source Task but switches to the
    * given backup Task in case the given duration passes without the
    * source emitting any item.
    */
  def timeoutTo[B >: A](after: FiniteDuration, backup: Task[B]): Task[B] =
    Task.chooseFirstOf(self, backup.delayExecution(after)).map {
      case Left(((a, futureB))) =>
        futureB.cancel()
        a
      case Right((futureA, b)) =>
        futureA.cancel()
        b
    }

  /** Zips the values of `this` and `that` task, and creates a new task
    * that will emit the tuple of their results.
    */
  def zip[B](that: Task[B]): Task[(A, B)] =
    Task.mapBoth(this, that)((a,b) => (a,b))

  /** Zips the values of `this` and `that` and applies the given
    * mapping function on their results.
    */
  def zipWith[B,C](that: Task[B])(f: (A,B) => C): Task[C] =
    Task.mapBoth(this, that)(f)
}


object Task extends TaskInstances {
  /** Returns a new task that, when executed, will emit the result of
    * the given function executed asynchronously.
    */
  def apply[A](f: => A): Task[A] =
    fork(evalAlways(f))

  /** Returns a `Task` that on execution is always successful, emitting
    * the given strict value.
    */
  def now[A](a: A): Task[A] = Now(a)

  /** Lifts a value into the task context. Alias for [[now]]. */
  def pure[A](a: A): Task[A] = Now(a)

  /** Returns a task that on execution is always finishing in error
    * emitting the specified exception.
    */
  def raiseError[A](ex: Throwable): Task[A] =
    Error(ex)

  /** Promote a non-strict value representing a Task to a Task of the
    * same type.
    */
  def defer[A](task: => Task[A]): Task[A] =
    Suspend(() => task)

  /** Promote a non-strict value to a Task that is memoized on the first
    * evaluation, the result being then available on subsequent evaluations.
    */
  def evalOnce[A](a: => A): Task[A] =
    EvalOnce(a _)

  /** Promote a non-strict value to a Task, catching exceptions in the
    * process.
    *
    * Note that since `Task` is not memoized, this will recompute the
    * value each time the `Task` is executed.
    */
  def evalAlways[A](a: => A): Task[A] =
    EvalAlways(a _)

  /** A [[Task]] instance that upon evaluation will never complete. */
  def never[A]: Task[A] = neverRef

  private[this] final val neverRef: Async[Nothing] =
    Async((_,_,_) => ())

  /** A `Task[Unit]` provided for convenience. */
  final val unit: Task[Unit] = Now(())

  /** Transforms a [[Coeval]] into a [[Task]]. */
  def eval[A](eval: Coeval[A]): Task[A] =
    eval.task

  /** Mirrors the given source `Task`, but upon execution ensure
    * that evaluation forks into a separate (logical) thread.
    */
  def fork[A](fa: Task[A]): Task[A] =
    fa match {
      case async @ Async(_) => async
      case async @ BindAsync(_,_) => async
      case Suspend(thunk) =>
        Suspend(() => fork(thunk()))

      case memoize: MemoizeSuspend[_] =>
        if (memoize.isStarted)
          Async { (s, conn, cb) => Task.unsafeStartNow(memoize, s, conn, cb) }
        else
          memoize

      case other =>
        Async { (s, conn, cb) => Task.unsafeStartNow(other, s, conn, cb) }
    }

  /** Create a `Task` from an asynchronous computation, which takes the
    * form of a function with which we can register a callback.
    *
    * This can be used to translate from a callback-based API to a
    * straightforward monadic version.
    *
    * Contract:
    *
    *  1. execution of the `register` callback is async, forking a (logical) thread
    *  2. execution of the `onSuccess` and `onError` callbacks, is async, forking another (logical) thread
    *
    * Point number 2 happens because [[create]] is supposed to be safe
    * or otherwise, depending on the executed logic, one can end up with
    * a stack overflow exception. So this contract happens in order to
    * guarantee safety. In order to bypass rule number 2, one can use
    * [[unsafeCreate]], but that's for people knowing what they are doing.
    *
    * @param register is a function that will be called when this `Task`
    *        is executed, receiving a callback as a parameter, a
    *        callback that the user is supposed to call in order to
    *        signal the desired outcome of this `Task`.
    */
  def create[A](register: (Scheduler, Callback[A]) => Cancelable): Task[A] = {
    // Wraps a callback into an implementation that pops the stack
    // before calling onSuccess/onError and that introduces an
    // asynchronous boundary to avoid stack overflows
    final class CreateCallback(conn: StackedCancelable, s: Scheduler, cb: Callback[A])
      extends Callback[A] {

      def onSuccess(value: A): Unit = {
        conn.pop()
        // Async boundary to avoid stack overflows
        cb.asyncOnSuccess(value)(s)
      }

      def onError(ex: Throwable): Unit = {
        conn.pop()
        // Async boundary to avoid stack overflows
        cb.asyncOnError(ex)(s)
      }
    }

    Async { (scheduler, conn, cb) =>
      try {
        val c = SingleAssignmentCancelable()
        conn push c
        c := register(scheduler, new CreateCallback(conn, scheduler, cb))
      } catch {
        case NonFatal(ex) =>
          // We cannot stream the error, because the callback might have
          // been called already and we'd be violating its contract,
          // hence the only thing possible is to log the error.
          scheduler.reportFailure(ex)
      }
    }
  }


  /** Constructs a lazy [[Task]] instance whose result
    * will be computed asynchronously.
    *
    * Unsafe to use directly, only use if you know what you're doing.
    * For building `Task` instances safely see [[create]].
    */
  def unsafeCreate[A](onFinish: OnFinish[A]): Task[A] =
    Async(onFinish)

  /** Converts the given Scala `Future` into a `Task`.
    *
    * NOTE: if you want to defer the creation of the future, use
    * in combination with [[defer]].
    */
  def fromFuture[A](f: Future[A]): Task[A] = {
    if (f.isCompleted) {
      // Ready result gets synchronous treatment
      Attempt.fromTry(f.value.get)
    }
    else f match {
      // Do we have a CancelableFuture?
      case c: Cancelable =>
        // Cancelable future, needs canceling
        Async { (s, conn, cb) =>
          // Already completed future avoids
          // forking another thread, as one
          // was already forked
          if (f.isCompleted) cb(f.value.get) else {
            conn.push(c)
            f.onComplete {
              case Success(a) =>
                conn.pop()
                cb.onSuccess(a)
              case Failure(ex) =>
                conn.pop()
                cb.onError(ex)
            }(s)
          }
        }
      case _ =>
        // Simple future, convert directly
        Async { (s, conn, cb) =>
          if (f.isCompleted) cb(f.value.get) else
            f.onComplete(cb)(s)
        }
    }
  }

  /** Creates a `Task` that upon execution will execute both given tasks
    * (possibly in parallel in case the tasks are asynchronous) and will
    * return the result of the task that manages to complete first,
    * along with a cancelable future of the other task.
    *
    * If the first task that completes
    */
  def chooseFirstOf[A,B](fa: Task[A], fb: Task[B]): Task[Either[(A, CancelableFuture[B]), (CancelableFuture[A], B)]] =
    Async { (scheduler, conn, cb) =>
      val pa = Promise[A]()
      val pb = Promise[B]()

      val isActive = Atomic(true)
      val connA = StackedCancelable()
      val connB = StackedCancelable()
      conn push CompositeCancelable(connA, connB)

      // First task: A
      Task.unsafeStartNow(fa, scheduler, connA, new Callback[A] {
        def onSuccess(valueA: A): Unit =
          if (isActive.getAndSet(false)) {
            val futureB = CancelableFuture(pb.future, connB)
            conn.pop()
            cb.onSuccess(Left((valueA, futureB)))
          } else {
            pa.success(valueA)
          }

        def onError(ex: Throwable): Unit =
          if (isActive.getAndSet(false)) {
            conn.pop()
            connB.cancel()
            cb.onError(ex)
          } else {
            pa.failure(ex)
          }
      })

      // Second task: B
      Task.unsafeStartNow(fb, scheduler, connB, new Callback[B] {
        def onSuccess(valueB: B): Unit =
          if (isActive.getAndSet(false)) {
            val futureA = CancelableFuture(pa.future, connA)
            conn.pop()
            cb.onSuccess(Right((futureA, valueB)))
          } else {
            pb.success(valueB)
          }

        def onError(ex: Throwable): Unit =
          if (isActive.getAndSet(false)) {
            conn.pop()
            connA.cancel()
            cb.onError(ex)
          } else {
            pb.failure(ex)
          }
      })
    }


  /** Creates a `Task` that upon execution will return the result of the
    * first completed task in the given list and then cancel the rest.
    */
  def chooseFirstOfList[A](tasks: TraversableOnce[Task[A]]): Task[A] =
    Async { (scheduler, conn, cb) =>
      val isActive = Atomic.withPadding(true, PaddingStrategy.LeftRight128)
      val composite = CompositeCancelable()
      conn.push(composite)

      var streamError = true
      try {
        val cursor = tasks.toIterator

        while (isActive.get && cursor.hasNext) {
          val task = cursor.next()
          val taskCancelable = StackedCancelable()
          composite += taskCancelable

          streamError = false
          Task.unsafeStartNow(task, scheduler, taskCancelable, new Callback[A] {
            def onSuccess(value: A): Unit =
              if (isActive.getAndSet(false)) {
                composite -= taskCancelable
                composite.cancel()
                conn.popAndCollapse(taskCancelable)
                cb.onSuccess(value)
              }

            def onError(ex: Throwable): Unit =
              if (isActive.getAndSet(false)) {
                composite -= taskCancelable
                composite.cancel()
                conn.popAndCollapse(taskCancelable)
                cb.onError(ex)
              } else {
                scheduler.reportFailure(ex)
              }
          })

          streamError = true
        }
      } catch {
        case NonFatal(ex) =>
          if (streamError && isActive.getAndSet(false)) {
            cb.asyncOnError(ex)(scheduler)
            composite.cancel()
          } else {
            scheduler.reportFailure(ex)
          }
      }
    }

  /** Given a `TraversableOnce` of tasks, transforms it to a task signaling
    * the collection, executing the tasks one by one and gathering their
    * results in the same collection.
    *
    * This operation will execute the tasks one by one, in order, which means that
    * both effects and results will be ordered. See [[gather]] and [[gatherUnordered]]
    * for unordered results or effects, and thus potential of running in parallel.
    *
    *  It's a simple version of [[traverse]].
    */
  def sequence[A, M[X] <: TraversableOnce[X]](in: M[Task[A]])
    (implicit cbf: CanBuildFrom[M[Task[A]], A, M[A]]): Task[M[A]] = {
    val init = evalAlways(cbf(in))
    val r = in.foldLeft(init)((acc,elem) => acc.flatMap(lb => elem.map(e => lb += e)))
    r.map(_.result())
  }

  /** Given a `TraversableOnce[A]` and a function `A => Task[B]`, sequentially
   *  apply the function to each element of the collection and gather their
   *  results in the same collection.
   *
   *  It's a generalized version of [[sequence]].
   */
  def traverse[A, B, M[X] <: TraversableOnce[X]](in: M[A])(f: A => Task[B])
    (implicit cbf: CanBuildFrom[M[A], B, M[B]]): Task[M[B]] = {
    val init = evalAlways(cbf(in))
    val r = in.foldLeft(init)((acc,elem) => acc.flatMap(lb => f(elem).map(e => lb += e)))
    r.map(_.result())
  }

  /** Nondeterministically gather results from the given collection of tasks,
    * returning a task that will signal the same type of collection of results
    * once all tasks are finished.
    *
    * This function is the nondeterministic analogue of `sequence` and should
    * behave identically to `sequence` so long as there is no interaction between
    * the effects being gathered. However, unlike `sequence`, which decides on
    * a total order of effects, the effects in a `gather` are unordered with
    * respect to each other.
    *
    * Although the effects are unordered, we ensure the order of results
    * matches the order of the input sequence. Also see [[gatherUnordered]]
    * for a more efficient alternative.
    *
    * In other words, the `gather` operation has the potential to start
    * the given tasks in parallel, whereas [[sequence]] does not. Note that
    * this doesn't necessarily mean that `gather` is more efficient. Don't
    * assume that running things in parallel can make your code more efficient.
    */
  def gather[A, M[X] <: TraversableOnce[X]](in: M[Task[A]])
    (implicit cbf: CanBuildFrom[M[Task[A]], A, M[A]]): Task[M[A]] = {
    val init = evalAlways(cbf(in))
    val r = in.foldLeft(init)((acc,elem) => Task.mapBoth(acc,elem)(_ += _))
    r.map(_.result())
  }

  /** Nondeterministically gather results from the given collection of tasks,
    * without keeping the original ordering of results.
    *
    * This function is similar to [[gather]], but neither the effects nor the
    * results will be ordered. Useful when you don't need ordering because it
    * can be dramatically more efficient than `gather`.
    */
  def gatherUnordered[A, M[X] <: TraversableOnce[X]](in: M[Task[A]])
    (implicit cbf: CanBuildFrom[M[Task[A]], A, M[A]]): Task[M[A]] = {

    // We are using OOP for initializing this `OnFinish` callback because we
    // need something to synchronize on and because it's better for making the
    // state explicit (e.g. the builder, remaining, isActive vars)
    Async(new OnFinish[M[A]] { self =>
      // Aggregates all results into a buffer.
      // MUST BE synchronized by `self`!
      private[this] var builder = cbf(in)

      // Keeps track of tasks remaining to be completed.
      // Is initialized by 1 because of the logic - tasks can run synchronously,
      // and we decrement this value whenever one finishes, so we must prevent
      // zero values before the loop is done.
      // MUST BE synchronized by `self`!
      private[this] var remaining = 1

      // If this variable is false, then a task ended in error.
      // MUST BE synchronized by `self`!
      private[this] var isActive = true

      // MUST BE synchronized by `self`!
      // MUST NOT BE called if isActive == false!
      @inline private
      def maybeSignalFinal(conn: StackedCancelable, finalCallback: Callback[M[A]])
        (implicit s: Scheduler): Unit = {

        remaining -= 1
        if (remaining == 0) {
          isActive = false
          conn.pop()
          val result = builder.result()
          builder = null // GC relief
          // OnSuccess must be async, or we can get a stack overflow
          finalCallback.asyncOnSuccess(result)
        }
      }

      def apply(scheduler: Scheduler, conn: StackedCancelable, finalCallback: Callback[M[A]]): Unit = {
        self.synchronized {
          // Represents the collection of cancelables for all started tasks
          val composite = CompositeCancelable()
          conn.push(composite)

          // Collecting all cancelables in a buffer, because adding
          // cancelables one by one in our `CompositeCancelable` is
          // expensive, so we do it at the end
          val allCancelables = ListBuffer.empty[StackedCancelable]
          val cursor = in.toIterator

          // The `isActive` check short-circuits the process in case
          // we have a synchronous task that just completed in error
          while (cursor.hasNext && isActive) {
            remaining += 1
            val task = cursor.next()
            val stacked = StackedCancelable()
            allCancelables += stacked

            // NOTE: Can run synchronously (immediately), depending
            // on the task's type. The user should `fork` to ensure async!
            Task.unsafeStartNow(task, scheduler, stacked,
              new Callback[A] {
                def onSuccess(value: A): Unit =
                  self.synchronized {
                    if (isActive) {
                      builder += value
                      maybeSignalFinal(conn, finalCallback)(scheduler)
                    }
                  }

                def onError(ex: Throwable): Unit =
                  self.synchronized {
                    if (isActive) {
                      isActive = false
                      // This should cancel our CompositeCancelable
                      conn.pop().cancel()
                      finalCallback.asyncOnError(ex)(scheduler)
                      builder = null // GC relief
                    } else {
                      scheduler.reportFailure(ex)
                    }
                  }
              })
          }

          // All tasks could have executed synchronously, so we might be
          // finished already. If so, then trigger the final callback.
          maybeSignalFinal(conn, finalCallback)(scheduler)

          // Note that if an error happened, this should cancel all
          // other active tasks.
          composite ++= allCancelables
        }
      }
    })
  }

  /** Obtain results from both `a` and `b`, nondeterministically ordering
    * their effects.
    *
    * The two tasks are both executed asynchronously. In a multi-threading
    * environment this means that the tasks will get executed in parallel and
    * their results synchronized.
    */
  def both[A,B](a: Task[A], b: Task[B]): Task[(A,B)] = mapBoth(a,b)((_,_))

  /** Apply a mapping functions to the results of two tasks, nondeterministically
    * ordering their effects.
    *
    * If the two tasks are synchronous, they'll get executed immediately, one
    * after the other, with the result being available synchronously.
    * If the two tasks are asynchronous, they'll get scheduled for execution
    * at the same time and in a multi-threading environment they'll execute
    * in parallel and have their results synchronized.
    */
  def mapBoth[A1,A2,R](fa1: Task[A1], fa2: Task[A2])(f: (A1,A2) => R): Task[R] = {
    /* For signaling the values after the successful completion of both tasks. */
    def sendSignal(conn: StackedCancelable, s: Scheduler, cb: Callback[R], a1: A1, a2: A2): Unit = {
      // Forcing an asynchronous boundary, because we can have
      // a whole chain of synchronous onSuccess calls (e.g. in Task.gather)
      // that can trigger a stack-overflow exception
      s.execute(new Runnable {
        def run() = {
          var streamErrors = true
          try {
            val r = f(a1,a2)
            streamErrors = false
            conn.pop()
            cb.onSuccess(r)
          } catch {
            case NonFatal(ex) if streamErrors =>
              // Both tasks completed by this point, so we don't need
              // to worry about the `state` being a `Stop`
              conn.pop()
              cb.onError(ex)
          }
        }
      })
    }

    /* For signaling an error. */
    @tailrec def sendError(conn: StackedCancelable, state: AtomicAny[AnyRef], s: Scheduler,
      cb: Callback[R], ex: Throwable): Unit = {
      // Guarding the contract of the callback, as we cannot send an error
      // if an error has already happened because of the other task
      state.get match {
        case Stop =>
          // We've got nowhere to send the error, so report it
          s.reportFailure(ex)
        case other =>
          if (!state.compareAndSet(other, Stop))
            sendError(conn, state, s, cb, ex) // retry
          else {
            // Forcing asynchronous boundary, mirroring `onSuccess`, in order
            // to have consistent behavior and because it is possible to have
            // a whole chain of `onError` callbacks executed synchronously
            // that could trigger a stack-overflow exception
            s.execute(new Runnable {
              def run(): Unit = {
                conn.pop().cancel()
                cb.onError(ex)
              }
            })
          }
      }
    }

    // The resulting task will be executed asynchronously
    Async { (scheduler, conn, cb) =>
      // for synchronizing the results
      val state = Atomic(null : AnyRef)
      val task1 = StackedCancelable()
      val task2 = StackedCancelable()
      conn push CompositeCancelable(task1, task2)

      Task.unsafeStartNow(fa1, scheduler, task1, new Callback[A1] {
        @tailrec def onSuccess(a1: A1): Unit =
          state.get match {
            case null => // null means this is the first task to complete
              if (!state.compareAndSet(null, Left(a1))) onSuccess(a1)
            case ref @ Right(a2) => // the other task completed, so we can send
              sendSignal(conn, scheduler, cb, a1, a2.asInstanceOf[A2])
            case Stop => // the other task triggered an error
              () // do nothing
            case s @ Left(_) =>
              // This task has triggered multiple onSuccess calls
              // violating the protocol. Should never happen.
              onError(new IllegalStateException(s.toString))
          }

        def onError(ex: Throwable): Unit =
          sendError(conn, state, scheduler, cb, ex)
      })

      Task.unsafeStartNow(fa2, scheduler, task2, new Callback[A2] {
        @tailrec def onSuccess(a2: A2): Unit =
          state.get match {
            case null => // null means this is the first task to complete
              if (!state.compareAndSet(null, Right(a2))) onSuccess(a2)
            case ref @ Left(a1) => // the other task completed, so we can send
              sendSignal(conn, scheduler, cb, a1.asInstanceOf[A1], a2)
            case Stop => // the other task triggered an error
              () // do nothing
            case s @ Right(_) =>
              // This task has triggered multiple onSuccess calls
              // violating the protocol. Should never happen.
              onError(new IllegalStateException(s.toString))
          }

        def onError(ex: Throwable): Unit =
          sendError(conn, state, scheduler, cb, ex)
      })
    }
  }

  /** Gathers the results from a sequence of tasks into a single list.
    * The effects are not ordered, but the results are.
    */
  def zipList[A](sources: Task[A]*): Task[List[A]] = {
    val init = evalAlways(mutable.ListBuffer.empty[A])
    val r = sources.foldLeft(init)((acc,elem) => Task.mapBoth(acc,elem)(_ += _))
    r.map(_.toList)
  }

  /** Pairs two [[Task]] instances. */
  def zip2[A1,A2,R](fa1: Task[A1], fa2: Task[A2]): Task[(A1,A2)] =
    Task.mapBoth(fa1, fa2)((_,_))

  /** Pairs two [[Task]] instances, creating a new instance that will apply
    * the given mapping function to the resulting pair. */
  def zipWith2[A1,A2,R](fa1: Task[A1], fa2: Task[A2])(f: (A1,A2) => R): Task[R] =
    Task.mapBoth(fa1, fa2)(f)

  /** Pairs three [[Task]] instances. */
  def zip3[A1,A2,A3](fa1: Task[A1], fa2: Task[A2], fa3: Task[A3]): Task[(A1,A2,A3)] =
    zipWith3(fa1,fa2,fa3)((a1,a2,a3) => (a1,a2,a3))
  /** Pairs four [[Task]] instances. */
  def zip4[A1,A2,A3,A4](fa1: Task[A1], fa2: Task[A2], fa3: Task[A3], fa4: Task[A4]): Task[(A1,A2,A3,A4)] =
    zipWith4(fa1,fa2,fa3,fa4)((a1,a2,a3,a4) => (a1,a2,a3,a4))
  /** Pairs five [[Task]] instances. */
  def zip5[A1,A2,A3,A4,A5](fa1: Task[A1], fa2: Task[A2], fa3: Task[A3], fa4: Task[A4], fa5: Task[A5]): Task[(A1,A2,A3,A4,A5)] =
    zipWith5(fa1,fa2,fa3,fa4,fa5)((a1,a2,a3,a4,a5) => (a1,a2,a3,a4,a5))
  /** Pairs six [[Task]] instances. */
  def zip6[A1,A2,A3,A4,A5,A6](fa1: Task[A1], fa2: Task[A2], fa3: Task[A3], fa4: Task[A4], fa5: Task[A5], fa6: Task[A6]): Task[(A1,A2,A3,A4,A5,A6)] =
    zipWith6(fa1,fa2,fa3,fa4,fa5,fa6)((a1,a2,a3,a4,a5,a6) => (a1,a2,a3,a4,a5,a6))

  /** Pairs three [[Task]] instances,
    * applying the given mapping function to the result.
    */
  def zipWith3[A1,A2,A3,R](fa1: Task[A1], fa2: Task[A2], fa3: Task[A3])(f: (A1,A2,A3) => R): Task[R] = {
    val fa12 = zip2(fa1, fa2)
    zipWith2(fa12, fa3) { case ((a1,a2), a3) => f(a1,a2,a3) }
  }

  /** Pairs four [[Task]] instances,
    * applying the given mapping function to the result.
    */
  def zipWith4[A1,A2,A3,A4,R](fa1: Task[A1], fa2: Task[A2], fa3: Task[A3], fa4: Task[A4])(f: (A1,A2,A3,A4) => R): Task[R] = {
    val fa123 = zip3(fa1, fa2, fa3)
    zipWith2(fa123, fa4) { case ((a1,a2,a3), a4) => f(a1,a2,a3,a4) }
  }

  /** Pairs five [[Task]] instances,
    * applying the given mapping function to the result.
    */
  def zipWith5[A1,A2,A3,A4,A5,R](fa1: Task[A1], fa2: Task[A2], fa3: Task[A3], fa4: Task[A4], fa5: Task[A5])(f: (A1,A2,A3,A4,A5) => R): Task[R] = {
    val fa1234 = zip4(fa1, fa2, fa3, fa4)
    zipWith2(fa1234, fa5) { case ((a1,a2,a3,a4), a5) => f(a1,a2,a3,a4,a5) }
  }

  /** Pairs six [[Task]] instances,
    * applying the given mapping function to the result.
    */
  def zipWith6[A1,A2,A3,A4,A5,A6,R](fa1: Task[A1], fa2: Task[A2], fa3: Task[A3], fa4: Task[A4], fa5: Task[A5], fa6: Task[A6])(f: (A1,A2,A3,A4,A5,A6) => R): Task[R] = {
    val fa12345 = zip5(fa1, fa2, fa3, fa4, fa5)
    zipWith2(fa12345, fa6) { case ((a1,a2,a3,a4,a5), a6) => f(a1,a2,a3,a4,a5,a6) }
  }

  /** Type alias representing callbacks for [[create]] tasks. */
  type OnFinish[+A] = (Scheduler, StackedCancelable, Callback[A]) => Unit

  /** The `Attempt` represents a strict, already evaluated result
    * of a [[Task]] that either resulted in success, wrapped in a
    * [[Now]], or in an error, wrapped in a [[Error]].
    *
    * It's the moral equivalent of `scala.util.Try`.
    */
  sealed abstract class Attempt[+A] extends Task[A] { self =>
    /** Evaluates the underlying computation and returns the result.
      *
      * NOTE: this can throw exceptions.
      */
    def value: A = this match {
      case Now(value) => value
      case Error(ex) => throw ex
    }

    /** Returns true if value is a successful one. */
    def isSuccess: Boolean = this match { case Now(_) => true; case _ => false }

    /** Returns true if result is an error. */
    def isFailure: Boolean = this match { case Error(_) => true; case _ => false }

    override def failed: Attempt[Throwable] =
      self match {
        case Now(_) => Error(new NoSuchElementException("failed"))
        case Error(ex) => Now(ex)
      }

    /** Converts this attempt into a `scala.util.Try`. */
    def asScala: Try[A] =
      this match {
        case Now(a) => Success(a)
        case Error(ex) => Failure(ex)
      }

    override def materializeAttempt: Attempt[Attempt[A]] =
      self match {
        case now @ Now(_) =>
          Now(now)
        case Error(ex) =>
          Now(Error(ex))
      }

    override def dematerializeAttempt[B](implicit ev: <:<[A, Attempt[B]]): Attempt[B] =
      self match {
        case Now(now) => now
        case error @ Error(_) => error
      }
  }

  object Attempt {
    /** Promotes a non-strict value to a [[Task.Attempt]]. */
    def apply[A](f: => A): Attempt[A] =
      try Now(f) catch { case NonFatal(ex) => Error(ex) }

    /** Builds a [[Task.Attempt]] from a `scala.util.Try` */
    def fromTry[A](value: Try[A]): Attempt[A] =
      value match {
        case Success(a) => Now(a)
        case Failure(ex) => Error(ex)
      }
  }

  /** Constructs an eager [[Task]] instance whose result is already known.
    *
    * `Now` is a [[Attempt]] task state that represents a strict
    * successful value.
    */
  final case class Now[+A](override val value: A) extends Attempt[A] {
    // Overriding runAsync for efficiency reasons
    override def runAsync(cb: Callback[A])(implicit s: Scheduler): Cancelable = {
      try cb.onSuccess(value) catch { case NonFatal(ex) => s.reportFailure(ex) }
      Cancelable.empty
    }
  }

  /** Constructs an eager [[Task]] instance for a result that represents
    * an error.
    *
    * `Error` is a [[Attempt]] task state that represents a
    * computation that terminated in error.
    */
  final case class Error(ex: Throwable) extends Attempt[Nothing] {
    override def value: Nothing = throw ex

    // Overriding runAsync for efficiency reasons
    override def runAsync(cb: Callback[Nothing])(implicit s: Scheduler): Cancelable = {
      try cb.onError(ex) catch { case NonFatal(err) => s.reportFailure(err) }
      Cancelable.empty
    }
  }

  /** Constructs a lazy [[Task]] instance that gets evaluated
    * only once.
    *
    * In some sense it is equivalent to using a lazy val.
    * When caching is not required or desired,
    * prefer [[EvalAlways]] or [[Now]].
    */
  final class EvalOnce[+A](f: () => A) extends Task[A] with (() => A) {
    private[this] var thunk: () => A = f

    def apply(): A = runAttempt match {
      case Now(a) => a
      case Error(ex) => throw ex
    }

    lazy val runAttempt: Attempt[A] = {
      try {
        Now(thunk())
      } catch {
        case NonFatal(ex) => Error(ex)
      } finally {
        // GC relief
        thunk = null
      }
    }

    override def toString =
      synchronized {
        if (thunk != null) s"EvalOnce($thunk)"
        else s"EvalOnce($runAttempt)"
      }

    override def equals(other: Any): Boolean = other match {
      case that: EvalOnce[_] => runAttempt == that.runAttempt
      case _ => false
    }

    override def hashCode(): Int =
      runAttempt.hashCode()

    def productArity: Int = 1
    def productElement(n: Int): Any = runAttempt
    def canEqual(that: Any): Boolean =
      that.isInstanceOf[EvalOnce[_]]
  }

  object EvalOnce {
    /** Builder for an [[EvalOnce]] instance. */
    def apply[A](a: () => A): EvalOnce[A] =
      new EvalOnce[A](a)

    /** Deconstructs an [[EvalOnce]] instance. */
    def unapply[A](eval: EvalOnce[A]): Some[() => A] =
      Some(eval)
  }

  /** Constructs a lazy [[Task]] instance.
    *
    * This type can be used for "lazy" values. In some sense it is
    * equivalent to using a Function0 value.
    */
  final case class EvalAlways[+A](f: () => A) extends Task[A]

  /** Constructs a lazy [[Task]] instance whose result will
    * be computed asynchronously.
    *
    * Unsafe to build directly, only use if you know what you're doing.
    * For building `Async` instances safely, see [[create]].
    */
  private final case class Async[+A](onFinish: OnFinish[A]) extends Task[A]

  /** Internal state, the result of [[Task.defer]] */
  private[eval] final case class Suspend[+A](thunk: () => Task[A]) extends Task[A]
  /** Internal [[Task]] state that is the result of applying `flatMap`. */
  private[eval] final case class BindSuspend[A,B](thunk: () => Task[A], f: A => Task[B]) extends Task[B]

  /** Internal [[Task]] state that is the result of applying `flatMap`
    * over an [[Async]] value.
    */
  private[eval] final case class BindAsync[A,B](onFinish: OnFinish[A], f: A => Task[B]) extends Task[B]

  /** Internal [[Task]] state that defers the evaluation of the
    * given [[Task]] and upon execution memoize its result to
    * be available for later evaluations.
    */
  private final class MemoizeSuspend[A](f: () => Task[A]) extends Task[A] {
    private[this] var thunk: () => Task[A] = f
    private[this] val state = Atomic(null : AnyRef)

    def isStarted: Boolean =
      state.get != null

    def value: Option[Attempt[A]] =
      state.get match {
        case null => None
        case (p: Promise[_], _) =>
          p.asInstanceOf[Promise[A]].future.value match {
            case None => None
            case Some(value) => Some(Attempt.fromTry(value))
          }
        case result: Try[_] =>
          Some(Attempt.fromTry(result.asInstanceOf[Try[A]]))
      }

    override def runAsync(implicit s: Scheduler): CancelableFuture[A] =
      state.get match {
        case null => super.runAsync(s)
        case (p: Promise[_], c: StackedCancelable) =>
          val f = p.asInstanceOf[Promise[A]].future
          CancelableFuture(f, c)
        case result: Try[_] =>
          CancelableFuture.fromTry(result.asInstanceOf[Try[A]])
      }

    private def memoizeValue(value: Try[A]): Unit = {
      state.getAndSet(value) match {
        case (p: Promise[_], _) =>
          p.asInstanceOf[Promise[A]].complete(value)
        case _ =>
          () // do nothing
      }

      // GC purposes
      thunk = null
    }

    def runnable(scheduler: Scheduler, active: StackedCancelable, cb: Callback[A], binds: List[Bind]): Runnable =
      new Runnable {
        @tailrec def run(): Unit = {
          implicit val s = scheduler

          state.get match {
            case null =>
              val p = Promise[A]()

              if (state.compareAndSet(null, (p, active))) {
                val underlying = try thunk() catch { case NonFatal(ex) => Error(ex) }
                val callback = new Callback[A] {
                  def onError(ex: Throwable): Unit = {
                    try cb.onError(ex) finally
                      memoizeValue(Failure(ex))
                  }

                  def onSuccess(value: A): Unit = {
                    try cb.onSuccess(value) finally
                      memoizeValue(Success(value))
                  }
                }

                Task.resume(scheduler, active, underlying, callback, binds)
              }
              else {
                run() // retry
              }

            case (p: Promise[_], cancelable: StackedCancelable) =>
              // execution is pending completion
              active push cancelable
              p.asInstanceOf[Promise[A]].future.onComplete { r =>
                active.pop()
                if (r.isSuccess) cb.onSuccess(r.get)
                else cb.onError(r.failed.get)
              }

            case result: Try[_] =>
              val r = result.asInstanceOf[Try[A]]
              if (r.isSuccess) cb.onSuccess(r.get)
              else cb.onError(r.failed.get)
          }
        }
      }


    def productArity: Int = 1
    def productElement(n: Int): Any = value
    def canEqual(that: Any): Boolean =
      that.isInstanceOf[MemoizeSuspend[_]]
  }

  object MemoizeSuspend {
    /** Extracts the memoized value, if available. */
    def unapply[A](source: Task.MemoizeSuspend[A]): Option[Option[Attempt[A]]] =
      Some(source.value)
  }

  private type Current = Task[Any]
  private type Bind = Any => Task[Any]

  /** Unsafe utility - starts the execution of a Task with a guaranteed
    * asynchronous boundary, by providing
    * the needed [[monix.execution.Scheduler Scheduler]],
    * [[monix.execution.cancelables.StackedCancelable StackedCancelable]]
    * and [[Callback]].
    *
    * DO NOT use directly, as it is UNSAFE to use, unless you know
    * what you're doing. Prefer [[Task.runAsync(cb* Task.runAsync]]
    * and [[Task.fork]].
    */
  def unsafeStartAsync[A](source: Task[A], scheduler: Scheduler, conn: StackedCancelable, cb: Callback[A]): Unit = {
    // Task is already known to execute asynchronously
    if (Task.isNextAsync(source))
      resume(scheduler, conn, source, cb, Nil)
    else
      scheduler.execute(new AsyncResumeRunnable(scheduler, conn, source, cb, Nil))
  }

  /** Unsafe utility - starts the execution of a Task, by providing
    * the needed [[monix.execution.Scheduler Scheduler]],
    * [[monix.execution.cancelables.StackedCancelable StackedCancelable]]
    * and [[Callback]].
    *
    * DO NOT use directly, as it is UNSAFE to use, unless you know
    * what you're doing. Prefer [[Task.runAsync(cb* Task.runAsync]].
    */
  def unsafeStartNow[A](source: Task[A], scheduler: Scheduler, conn: StackedCancelable, cb: Callback[A]): Unit =
    resume(scheduler, conn, source, cb, Nil)

  /** Internal utility, returns true if the current state
    * is known to be asynchronous.
    */
  private def isNextAsync[A](source: Task[A]): Boolean =
    source match {
      case Async(_) | BindAsync(_,_) => true
      case _ => false
    }

  /** Internal utility, resumes evaluation of the run-loop
    * from where it left off.
    */
  private def resume[A](
    scheduler: Scheduler,
    conn: StackedCancelable,
    source: Task[A],
    cb: Callback[A],
    binds: List[Bind]): Unit = {

    @tailrec def trampoline(
      scheduler: Scheduler,
      em: ExecutionModel,
      conn: StackedCancelable,
      source: Current,
      cb: Callback[Any],
      binds: List[Bind],
      frameIndex: Int): Runnable = {

      if (frameIndex == 0 && !Task.isNextAsync(source)) {
        // Asynchronous boundary is forced because of the Scheduler's ExecutionModel
        new AsyncResumeRunnable(scheduler, conn, source, cb, binds)
      }
      else source match {
        case Now(a) =>
          binds match {
            case Nil =>
              cb.onSuccess(a)
              null // we are done
            case f :: rest =>
              val fa = try f(a) catch { case NonFatal(ex) => Error(ex) }
              trampoline(scheduler, em, conn, fa, cb, rest, em.nextFrameIndex(frameIndex))
          }

        case eval: EvalOnce[_] =>
          eval.runAttempt match {
            case Now(a) =>
              binds match {
                case Nil =>
                  cb.onSuccess(a)
                  null // we are done
                case f :: rest =>
                  val fa = try f(a) catch { case NonFatal(ex) => Error(ex) }
                  trampoline(scheduler, em, conn, fa, cb, rest, em.nextFrameIndex(frameIndex))
              }
            case error @ Error(ex) =>
              cb.onError(ex)
              null // we are done
          }

        case EvalAlways(thunk) =>
          val fa = try Now(thunk()) catch { case NonFatal(ex) => Error(ex) }
          trampoline(scheduler, em, conn, fa, cb, binds, em.nextFrameIndex(frameIndex))

        case Error(ex) =>
          cb.onError(ex)
          null // we are done

        case Suspend(thunk) =>
          val fa = try thunk() catch { case NonFatal(ex) => Error(ex) }
          trampoline(scheduler, em, conn, fa, cb, binds, em.nextFrameIndex(frameIndex))

        case MemoizeSuspend(value) =>
          value match {
            case Some(materialized) =>
              trampoline(scheduler, em, conn, materialized, cb, binds, em.nextFrameIndex(frameIndex))
            case None =>
              source.asInstanceOf[MemoizeSuspend[Any]].runnable(scheduler, conn, cb, binds)
          }

        case BindSuspend(thunk, f) =>
          val fa = try thunk() catch { case NonFatal(ex) => Error(ex) }
          trampoline(scheduler, em, conn, fa, cb, f.asInstanceOf[Bind] :: binds,
            em.nextFrameIndex(frameIndex))

        case BindAsync(onFinish, f) =>
          new AsyncStateRunnable(scheduler, conn, cb, f.asInstanceOf[Bind] :: binds, onFinish)

        case Async(onFinish) =>
          new AsyncStateRunnable(scheduler, conn, cb, binds, onFinish)
      }
    }

    val r = trampoline(scheduler, scheduler.executionModel,
      conn, source, cb.asInstanceOf[Callback[Any]], binds,
      // value ensures that first cycle is not async
      frameIndex = 1)

    if (r != null) scheduler.execute(r)
  }

  /** A run-loop that attempts to complete a
    * [[monix.execution.CancelableFuture CancelableFuture]] synchronously ,
    * falling back to [[resume]] and actual asynchronous execution
    * in case of an asynchronous boundary.
    */
  private def runAsCancelableFuture[A](
    scheduler: Scheduler,
    source: Task[A],
    binds: List[Bind]): CancelableFuture[A] = {

    def goAsync(scheduler: Scheduler, source: Current, binds: List[Bind], isNextAsync: Boolean): CancelableFuture[Any] = {
      val p = Promise[Any]()
      val cb: Callback[Any] = new Callback[Any] {
        def onSuccess(value: Any): Unit = p.trySuccess(value)
        def onError(ex: Throwable): Unit = p.tryFailure(ex)
      }

      val conn = StackedCancelable()
      if (!isNextAsync)
        scheduler.execute(new AsyncResumeRunnable(scheduler, conn, source, cb, binds))
      else
        resume(scheduler, conn, source, cb, binds)

      CancelableFuture(p.future, conn)
    }

    @tailrec def trampoline(
      scheduler: Scheduler,
      em: ExecutionModel,
      source: Current,
      binds: List[Bind],
      frameIndex: Int): CancelableFuture[Any] = {

      if (frameIndex == 0 && !Task.isNextAsync(source)) {
        // Asynchronous boundary is forced
        goAsync(scheduler, source, binds, isNextAsync = false)
      }
      else source match {
        case Now(a) =>
          binds match {
            case Nil =>
              CancelableFuture.successful(a)
            case f :: rest =>
              val fa = try f(a) catch { case NonFatal(ex) => Error(ex) }
              trampoline(scheduler, em, fa, rest, em.nextFrameIndex(frameIndex))
          }

        case eval: EvalOnce[_] =>
          eval.runAttempt match {
            case Now(a) =>
              binds match {
                case Nil =>
                  CancelableFuture.successful(a)
                case f :: rest =>
                  val fa = try f(a) catch { case NonFatal(ex) => Error(ex) }
                  trampoline(scheduler, em, fa, rest, em.nextFrameIndex(frameIndex))
              }
            case error @ Error(ex) =>
              CancelableFuture.failed(ex)
          }

        case EvalAlways(thunk) =>
          val fa = try Now(thunk()) catch { case NonFatal(ex) => Error(ex) }
          trampoline(scheduler, em, fa, binds, em.nextFrameIndex(frameIndex))

        case Error(ex) =>
          CancelableFuture.failed(ex)

        case Suspend(thunk) =>
          val fa = try thunk() catch { case NonFatal(ex) => Error(ex) }
          trampoline(scheduler, em, fa, binds, em.nextFrameIndex(frameIndex))

        case BindSuspend(thunk, f) =>
          val fa = try thunk() catch { case NonFatal(ex) => Error(ex) }
          trampoline(scheduler, em, fa, f.asInstanceOf[Bind] :: binds,
            em.nextFrameIndex(frameIndex))

        case source @ MemoizeSuspend(value) =>
          value match {
            case Some(materialized) =>
              trampoline(scheduler, em, materialized, binds, em.nextFrameIndex(frameIndex))
            case None =>
              goAsync(scheduler, source, binds, isNextAsync = true)
          }

        case async =>
          goAsync(scheduler, async, binds, isNextAsync = true)
      }
    }

    trampoline(scheduler, scheduler.executionModel, source, binds, frameIndex = 1)
      .asInstanceOf[CancelableFuture[A]]
  }

  private final class AsyncResumeRunnable[A](
    scheduler: Scheduler,
    conn: StackedCancelable,
    source: Task[A],
    cb: Callback[A],
    binds: List[Bind])
    extends Runnable {

    def run(): Unit = {
      resume(scheduler, conn, source, cb, binds)
    }
  }

  private final class AsyncStateRunnable(
    scheduler: Scheduler,
    conn: StackedCancelable,
    cb: Callback[Any],
    fs: List[Bind],
    onFinish: OnFinish[Any])
    extends Runnable {

    def run(): Unit =
      if (!conn.isCanceled) {
        onFinish(scheduler, conn, new Callback[Any] {
          def onSuccess(value: Any): Unit =
          // resuming loop
            resume(scheduler, conn, Now(value), cb, fs)
          def onError(ex: Throwable): Unit =
            cb.onError(ex)
        })
      }
  }

  /** Type-class instances for [[Task]]. */
  implicit val typeClassInstances: TypeClassInstances = new TypeClassInstances
}

private[eval] trait TaskInstances {
  /** Type-class instances for [[Task]] that have
    * nondeterministic effects for [[monix.types.Applicative Applicative]].
    *
    * It can be optionally imported in scope to make `map2` and `ap` to
    * potentially run tasks in parallel.
    */
  implicit val nondeterminism: TypeClassInstances =
    new TypeClassInstances {
      override def ap[A, B](fa: Task[A])(ff: Task[(A) => B]): Task[B] =
        Task.mapBoth(ff,fa)(_(_))
      override def map2[A, B, Z](fa: Task[A], fb: Task[B])(f: (A, B) => Z): Task[Z] =
        Task.mapBoth(fa, fb)(f)
    }

  /** Groups the implementation for the type-classes defined in [[monix.types]]. */
  class TypeClassInstances extends Evaluable[Task] {
    override def flatMap[A, B](fa: Task[A])(f: (A) => Task[B]): Task[B] =
      fa.flatMap(f)
    override def flatten[A](ffa: Task[Task[A]]): Task[A] =
      ffa.flatten
    override def coflatMap[A, B](fa: Task[A])(f: (Task[A]) => B): Task[B] =
      Task.evalAlways(f(fa))
    override def pure[A](a: A): Task[A] =
      Task.now(a)
    override def pureEval[A](a: => A): Task[A] =
      Task.evalAlways(a)
    override def ap[A, B](fa: Task[A])(ff: Task[(A) => B]): Task[B] =
      for (f <- ff; a <- fa) yield f(a)
    override def map2[A, B, Z](fa: Task[A], fb: Task[B])(f: (A, B) => Z): Task[Z] =
      for (a <- fa; b <- fb) yield f(a,b)
    override def map[A, B](fa: Task[A])(f: (A) => B): Task[B] =
      fa.map(f)
    override def raiseError[A](e: Throwable): Task[A] =
      Task.raiseError(e)
    override def handleError[A](fa: Task[A])(f: (Throwable) => A): Task[A] =
      fa.onErrorHandle(f)
    override def handleErrorWith[A](fa: Task[A])(f: (Throwable) => Task[A]): Task[A] =
      fa.onErrorHandleWith(f)
    override def recover[A](fa: Task[A])(pf: PartialFunction[Throwable, A]): Task[A] =
      fa.onErrorRecover(pf)
    override def recoverWith[A](fa: Task[A])(pf: PartialFunction[Throwable, Task[A]]): Task[A] =
      fa.onErrorRecoverWith(pf)
  }
}
