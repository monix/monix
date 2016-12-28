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

import monix.eval.Coeval.Attempt
import monix.eval.internal._
import monix.execution.atomic.Atomic
import monix.execution.cancelables.StackedCancelable
import monix.execution.internal.Platform
import monix.execution.internal.collection.ArrayStack
import monix.execution.misc.ThreadLocal
import monix.execution.schedulers.ExecutionModel
import monix.execution.schedulers.ExecutionModel.AlwaysAsyncExecution
import monix.execution.{Cancelable, CancelableFuture, Scheduler}
import monix.types._

import scala.annotation.tailrec
import scala.collection.generic.CanBuildFrom
import scala.collection.mutable
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{Future, Promise, TimeoutException}
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

/** `Task` represents a specification for a possibly lazy or
  * asynchronous computation, which when executed will produce an `A`
  * as a result, along with possible side-effects.
  *
  * Compared with `Future` from Scala's standard library, `Task` does
  * not represent a running computation or a value detached from time,
  * as `Task` does not execute anything when working with its builders
  * or operators and it does not submit any work into any thread-pool,
  * the execution eventually taking place only after `runAsync` is
  * called and not before that.
  *
  * Note that `Task` is conservative in how it spawns logical threads.
  * Transformations like `map` and `flatMap` for example will default
  * to being executed on the logical thread on which the asynchronous
  * computation was started. But one shouldn't make assumptions about
  * how things will end up executed, as ultimately it is the
  * implementation's job to decide on the best execution model. All
  * you are guaranteed is asynchronous execution after executing
  * `runAsync`.
  *
  * @define runAsyncDesc Triggers the asynchronous execution.
  *
  *         Without invoking `runAsync` on a `Task`, nothing
  *         gets evaluated, as a `Task` has lazy behavior.
  */
sealed abstract class Task[+A] extends Serializable { self =>
  import monix.eval.Task._

  /** $runAsyncDesc
    *
    * @param cb is a callback that will be invoked upon completion.
    *
    * @param s is an injected [[monix.execution.Scheduler Scheduler]]
    *        that gets used whenever asynchronous boundaries are needed
    *        when evaluating the task
    *
    * @return a [[monix.execution.Cancelable Cancelable]] that can
    *         be used to cancel a running task
    */
  def runAsync(cb: Callback[A])(implicit s: Scheduler): Cancelable = {
    val conn = StackedCancelable()
    // Is the first execution asynchronous?
    val frameStart = if (!s.executionModel.isAlwaysAsync) Task.frameStart else 0
    val context = Context(s, conn, startFrameRef(), defaultOptions)
    internalStartTrampolineRunLoop(self, context, Callback.safe(cb), null, frameStart)
    conn
  }

  /** $runAsyncDesc
    *
    * @param f is a callback that will be invoked upon completion.
    *
    * @param s is an injected [[monix.execution.Scheduler Scheduler]]
    *        that gets used whenever asynchronous boundaries are needed
    *        when evaluating the task
    *
    * @return a [[monix.execution.Cancelable Cancelable]] that can
    *         be used to cancel a running task
    */
  def runAsync(f: Try[A] => Unit)(implicit s: Scheduler): Cancelable =
    runAsync(new Callback[A] {
      def onSuccess(value: A): Unit = f(Success(value))
      def onError(ex: Throwable): Unit = f(Failure(ex))
    })

  /** $runAsyncDesc
    *
    * @param s is an injected [[monix.execution.Scheduler Scheduler]]
    *        that gets used whenever asynchronous boundaries are needed
    *        when evaluating the task
    *
    * @return a [[monix.execution.CancelableFuture CancelableFuture]]
    *         that can be used to extract the result or to cancel
    *         a running task.
    */
  def runAsync(implicit s: Scheduler): CancelableFuture[A] = {
    // Is the first execution asynchronous?
    val frameStart = if (!s.executionModel.isAlwaysAsync) Task.frameStart else 0
    val context = Context(s, StackedCancelable(), startFrameRef(), defaultOptions)
    Task.internalStartTrampolineForFuture(this, context, null, frameStart)
  }

  /** Transforms a [[Task]] into a [[Coeval]] that tries to execute the
    * source synchronously, returning either `Right(value)` in case a
    * value is available immediately, or `Left(future)` in case we
    * have an asynchronous boundary.
    */
  def coeval(implicit s: Scheduler): Coeval[Either[CancelableFuture[A], A]] =
    Coeval.eval {
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
      case Now(value) =>
        Suspend(() => try f(value) catch { case NonFatal(ex) => raiseError(ex) })
      case Delay(coeval) =>
        Suspend(() => try f(coeval.value) catch { case NonFatal(ex) => raiseError(ex) })
      case Suspend(thunk) =>
        BindSuspend(thunk, f)
      case ref: MemoizeSuspend[_] =>
        val task = ref.asInstanceOf[MemoizeSuspend[A]]
        BindSuspend(() => task, f)
      case BindSuspend(thunk, g) =>
        BindSuspend(thunk, (v: Any) => g(v).flatMap(f))
      case Async(onFinish) =>
        BindAsync(onFinish, f)
      case BindAsync(listen, g) =>
        BindAsync(listen, (v: Any) => g(v).flatMap(f))
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
    TaskDelayExecution(self, timespan)

  /** Returns a task that waits for the specified `trigger` to succeed
    * before mirroring the result of the source.
    *
    * If the `trigger` ends in error, then the resulting task will
    * also end in error.
    */
  def delayExecutionWith(trigger: Task[Any]): Task[A] =
    TaskDelayExecutionWith(self, trigger)

  /** Returns a task that executes the source immediately on `runAsync`,
    * but before emitting the `onSuccess` result for the specified
    * duration.
    *
    * Note that if an error happens, then it is streamed immediately
    * with no delay.
    */
  def delayResult(timespan: FiniteDuration): Task[A] =
    TaskDelayResult(self, timespan)

  /** Returns a task that executes the source immediately on `runAsync`,
    * but before emitting the `onSuccess` result for the specified
    * duration.
    *
    * Note that if an error happens, then it is streamed immediately
    * with no delay.
    */
  def delayResultBySelector[B](selector: A => Task[B]): Task[A] =
    TaskDelayResultBySelector(self, selector)

  /** Mirrors the given source `Task`, but upon execution ensure that
    * evaluation forks into a separate (logical) thread spawned by the
    * given `scheduler`.
    *
    * The given [[monix.execution.Scheduler Scheduler]] will be used
    * for execution of the [[Task]], effectively overriding the
    * `Scheduler` that's passed in `runAsync`. Thus you can execute a
    * whole `Task` on a separate thread-pool, useful for example in
    * case of doing I/O.
    *
    * NOTE: the logic one cares about won't necessarily end up
    * executed on the given scheduler, or for transformations that
    * happen from here on. It all depends on what overrides or
    * asynchronous boundaries happen. But this function guarantees
    * that the this `Task` run-loop begins executing on the given
    * scheduler.
    *
    * Alias for
    * [[Task.fork[A](fa:monix\.eval\.Task[A],scheduler* Task.fork(fa,scheduler)]].
    *
    * @param s is the scheduler to use for execution
    */
  def executeOn(s: Scheduler): Task[A] =
    Task.fork(this, s)

  /** Mirrors the given source `Task`, but upon execution ensure
    * that evaluation forks into a separate (logical) thread.
    *
    * The [[monix.execution.Scheduler Scheduler]] used will be
    * the one that is used to start the run-loop in `runAsync`.
    *
    * Alias for [[Task.fork[A](fa:monix\.eval\.Task[A])* Task.fork(fa)]].
    */
  def executeWithFork: Task[A] =
    Task.fork(this)

  /** Returns a new task that will execute the source with a different
    * [[monix.execution.schedulers.ExecutionModel ExecutionModel]].
    *
    * This allows fine-tuning the options injected by the scheduler
    * locally. Example:
    *
    * {{{
    *   import monix.execution.schedulers.ExecutionModel.AlwaysAsyncExecution
    *   task.executeWithModel(AlwaysAsyncExecution)
    * }}}
    *
    * @param em is the
    *        [[monix.execution.schedulers.ExecutionModel ExecutionModel]]
    *        with which the source will get evaluated on `runAsync`
    */
  def executeWithModel(em: ExecutionModel): Task[A] =
    TaskExecuteWithModel(self, em)

  /** Returns a new task that will execute the source with a different
    * set of [[Task.Options Options]].
    *
    * This allows fine-tuning the default options. Example:
    *
    * {{{
    *   task.executeWithOptions(_.enableAutoCancelableRunLoops)
    * }}}
    *
    * @param f is a function that takes the source's current set of
    *        [[Task.Options options]] and returns a modified set of
    *        options that will be used to execute the source
    *        upon `runAsync`
    */
  def executeWithOptions(f: Options => Options): Task[A] =
    TaskExecuteWithOptions(self, f)

  /** Introduces an asynchronous boundary at the current stage in the
    * asynchronous processing pipeline.
    *
    * Consider the following example:
    *
    * {{{
    *   import monix.execution.Scheduler
    *   val io = Scheduler.io()
    *
    *   val source = Task(1).executeOn(io).map(_ + 1)
    * }}}
    *
    * That task is being forced to execute on the `io` scheduler,
    * including the `map` transformation that follows after
    * `executeOn`. But what if we want to jump with the execution
    * run-loop on the default scheduler for the following
    * transformations?
    *
    * Then we can do:
    *
    * {{{
    *   source.asyncBoundary.map(_ + 2)
    * }}}
    *
    * In this sample, whatever gets evaluated by the `source` will
    * happen on the `io` scheduler, however the `asyncBoundary` call
    * will make all subsequent operations to happen on the default
    * scheduler.
    */
  def asyncBoundary: Task[A] =
    self.flatMap(r => Task.forkedUnit.map(_ => r))

  /** Introduces an asynchronous boundary at the current stage in the
    * asynchronous processing pipeline, making processing to jump on
    * the given [[monix.execution.Scheduler Scheduler]] (until the
    * next async boundary).
    *
    * Consider the following example:
    * {{{
    *   import monix.execution.Scheduler
    *   val io = Scheduler.io()
    *
    *   val source = Task(1).executeOn(io).map(_ + 1)
    * }}}
    *
    * That task is being forced to execute on the `io` scheduler,
    * including the `map` transformation that follows after
    * `executeOn`. But what if we want to jump with the execution
    * run-loop on another scheduler for the following transformations?
    *
    * Then we can do:
    * {{{
    *   import monix.execution.Scheduler.global
    *
    *   source.asyncBoundary(global).map(_ + 2)
    * }}}
    *
    * In this sample, whatever gets evaluated by the `source` will
    * happen on the `io` scheduler, however the `asyncBoundary` call
    * will make all subsequent operations to happen on the specified
    * `global` scheduler.
    *
    * @param s is the scheduler triggering the asynchronous boundary
    */
  def asyncBoundary(s: Scheduler): Task[A] =
    self.flatMap(r => Task.forkedUnit.executeOn(s).map(_ => r))

  /** Returns a failed projection of this task.
    *
    * The failed projection is a future holding a value of type
    * `Throwable`, emitting a value which is the throwable of the
    * original task in case the original task fails, otherwise if the
    * source succeeds, then it fails with a `NoSuchElementException`.
    */
  def failed: Task[Throwable] =
    materializeAttempt.flatMap {
      case Coeval.Error(ex) => now(ex)
      case Coeval.Now(_) => raiseError(new NoSuchElementException("failed"))
    }

  /** Returns a new task that upon evaluation will execute the given
    * function for the generated element, transforming the source into
    * a `Task[Unit]`.
    *
    * Similar in spirit with normal [[foreach]], but lazy, as
    * obviously nothing gets executed at this point.
    */
  def foreachL(f: A => Unit): Task[Unit] =
    self.map { a => f(a); () }

  /** Triggers the evaluation of the source, executing the given
    * function for the generated element.
    *
    * The application of this function has strict behavior, as the
    * task is immediately executed.
    */
  def foreach(f: A => Unit)(implicit s: Scheduler): CancelableFuture[Unit] =
    foreachL(f).runAsync(s)

  /** Returns a new Task that applies the mapping function to the
    * element emitted by the source.
    */
  def map[B](f: A => B): Task[B] =
    flatMap(a => try now(f(a)) catch { case NonFatal(ex) => raiseError(ex) })

  /** Returns a new `Task` in which `f` is scheduled to be run on
    * completion.  This would typically be used to release any
    * resources acquired by this `Task`.
    *
    * The returned `Task` completes when both the source and the task
    * returned by `f` complete.
    *
    * NOTE: The given function is only called when the task is
    * complete.  However the function does not get called if the task
    * gets canceled.  Cancellation is a process that's concurrent with
    * the execution of a task and hence needs special handling.
    *
    * See [[doOnCancel]] for specifying a callback to call on
    * canceling a task.
    */
  def doOnFinish(f: Option[Throwable] => Task[Unit]): Task[A] =
    materializeAttempt.flatMap {
      case Coeval.Now(value) =>
        f(None).map(_ => value)
      case Coeval.Error(ex) =>
        f(Some(ex)).flatMap(_ => raiseError(ex))
    }

  /** Returns a new `Task` that will mirror the source, but that will
    * execute the given `callback` if the task gets canceled before
    * completion.
    *
    * This only works for premature cancellation. See [[doOnFinish]]
    * for triggering callbacks when the source finishes.
    *
    * @param callback is the callback to execute if the task gets
    *        canceled prematurely
    */
  def doOnCancel(callback: Task[Unit]): Task[A] =
    TaskDoOnCancel(self, callback)

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
      case Now(value) =>
        Task.now(Coeval.Now(value))
      case Delay(coeval) =>
        Suspend(() => Delay(coeval.materializeAttempt))

      case Suspend(thunk) =>
        Suspend(() => try {
          thunk().materializeAttempt
        } catch { case NonFatal(ex) =>
          now(Coeval.Error(ex))
        })

      case ref: MemoizeSuspend[_] =>
        val task = ref.asInstanceOf[MemoizeSuspend[A]]
        Async[Attempt[A]] { (ctx, cb) =>
          // Light asynchronous boundary
          Task.unsafeStartTrampolined[A](task, ctx, new Callback[A] {
            def onSuccess(value: A): Unit = cb.onSuccess(Coeval.Now(value))
            def onError(ex: Throwable): Unit = cb.onSuccess(Coeval.Error(ex))
          })
        }

      case BindSuspend(thunk, g) =>
        BindSuspend[Attempt[Any], Attempt[A]](
          () => Suspend(() =>
            try thunk().materializeAttempt catch {
              case NonFatal(ex) => Now(Coeval.Error(ex))
            }),
          result => result match {
            case Coeval.Now(any) =>
              try {
                g.asInstanceOf[Any => Task[A]](any)
                  .materializeAttempt
              } catch {
                case NonFatal(ex) =>
                  Now(Coeval.Error(ex))
              }
            case error @ Coeval.Error(_) =>
              Now(error)
          })

      case Async(onFinish) =>
        Async { (context, cb) =>
          import context.{scheduler => s}
          s.executeTrampolined(() => onFinish(context, new Callback[A] {
            def onSuccess(value: A): Unit =
              cb.onSuccess(Coeval.Now(value))
            def onError(ex: Throwable): Unit =
              cb.onSuccess(Coeval.Error(ex))
          }))
        }

      case BindAsync(onFinish, g) =>
        BindAsync[Attempt[Any], Attempt[A]](
          (context, cb) => {
            import context.{scheduler => s}
            s.executeTrampolined(() => onFinish(context, new Callback[Any] {
              def onSuccess(value: Any): Unit =
                cb.onSuccess(Coeval.Now(value))
              def onError(ex: Throwable): Unit =
                cb.onSuccess(Coeval.Error(ex))
            }))
          },
          result => result match {
            case Coeval.Now(any) =>
              try {
                g.asInstanceOf[Any => Task[A]](any)
                  .materializeAttempt
              }
              catch {
                case NonFatal(ex) =>
                  Now(Coeval.Error(ex))
              }
            case Coeval.Error(ex) =>
              Now(Coeval.Error(ex))
          })
    }
  }

  /** Dematerializes the source's result from a `Try`. */
  def dematerialize[B](implicit ev: A <:< Try[B]): Task[B] =
    self.asInstanceOf[Task[Try[B]]].flatMap(fromTry)

  /** Dematerializes the source's result from an `Attempt`. */
  def dematerializeAttempt[B](implicit ev: A <:< Attempt[B]): Task[B] =
    self.asInstanceOf[Task[Attempt[B]]].flatMap(Delay.apply)

  /** Creates a new task that will try recovering from an error by
    * matching it with another task using the given partial function.
    *
    * See [[onErrorHandleWith]] for the version that takes a total function.
    */
  def onErrorRecoverWith[B >: A](pf: PartialFunction[Throwable, Task[B]]): Task[B] =
    onErrorHandleWith(ex => pf.applyOrElse(ex, raiseError))

  /** Creates a new task that will handle any matching throwable that
    * this task might emit by executing another task.
    *
    * See [[onErrorRecoverWith]] for the version that takes a partial function.
    */
  def onErrorHandleWith[B >: A](f: Throwable => Task[B]): Task[B] =
    self.materializeAttempt.flatMap {
      case now @ Coeval.Now(_) => Delay(now)
      case Coeval.Error(ex) => try f(ex) catch { case NonFatal(err) => raiseError(err) }
    }

  /** Creates a new task that in case of error will fallback to the
    * given backup task.
    */
  def onErrorFallbackTo[B >: A](that: Task[B]): Task[B] =
    onErrorHandleWith(_ => that)

  /** Given a predicate function, keep retrying the
    * task until the function returns true.
    */
  def restartUntil(p: (A) => Boolean): Task[A] =
    self.flatMap(a => if (p(a)) now(a) else self.restartUntil(p))

  /** Creates a new task that in case of error will retry executing the
    * source again and again, until it succeeds.
    *
    * In case of continuous failure the total number of executions
    * will be `maxRetries + 1`.
    */
  def onErrorRestart(maxRetries: Long): Task[A] =
    self.onErrorHandleWith(ex =>
      if (maxRetries > 0) self.onErrorRestart(maxRetries-1)
      else raiseError(ex))

  /** Creates a new task that in case of error will retry executing the
    * source again and again, until it succeeds.
    *
    * In case of continuous failure the total number of executions
    * will be `maxRetries + 1`.
    */
  def onErrorRestartIf(p: Throwable => Boolean): Task[A] =
    self.onErrorHandleWith(ex => if (p(ex)) self.onErrorRestartIf(p) else raiseError(ex))

  /** Creates a new task that will handle any matching throwable that
    * this task might emit.
    *
    * See [[onErrorRecover]] for the version that takes a partial function.
    */
  def onErrorHandle[U >: A](f: Throwable => U): Task[U] =
    onErrorHandleWith(ex => try now(f(ex)) catch { case NonFatal(err) => raiseError(err) })

  /** Creates a new task that on error will try to map the error
    * to another value using the provided partial function.
    *
    * See [[onErrorHandle]] for the version that takes a total function.
    */
  def onErrorRecover[U >: A](pf: PartialFunction[Throwable, U]): Task[U] =
    onErrorRecoverWith(pf.andThen(now))

  /** Memoizes the result on the computation and reuses it on subsequent
    * invocations of `runAsync`.
    */
  def memoize: Task[A] =
    self match {
      case Now(_) => self
      case Delay(value) => Delay(value.memoize)
      case _: MemoizeSuspend[_] => self
      case other => new MemoizeSuspend[A](() => other)
    }

  /** Converts a [[Task]] to an `org.reactivestreams.Publisher` that
    * emits a single item on success, or just the error on failure.
    *
    * See [[http://www.reactive-streams.org/ reactive-streams.org]] for the
    * Reactive Streams specification.
    */
  def toReactivePublisher[B >: A](implicit s: Scheduler): org.reactivestreams.Publisher[B] =
    TaskToReactivePublisher[B](self)(s)

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
  def zipMap[B,C](that: Task[B])(f: (A,B) => C): Task[C] =
    Task.mapBoth(this, that)(f)
}

object Task extends TaskInstances {
  /** Returns a new task that, when executed, will emit the result of
    * the given function, executed asynchronously.
    *
    * @param f is the callback to execute asynchronously
    */
  def apply[A](f: => A): Task[A] =
    fork(eval(f))

  /** Returns a `Task` that on execution is always successful, emitting
    * the given strict value.
    */
  def now[A](a: A): Task[A] =
    Task.Now(a)

  /** Lifts a value into the task context. Alias for [[now]]. */
  def pure[A](a: A): Task[A] = now(a)

  /** Returns a task that on execution is always finishing in error
    * emitting the specified exception.
    */
  def raiseError[A](ex: Throwable): Task[A] =
    Delay(Coeval.Error(ex))

  /** Promote a non-strict value representing a Task to a Task of the
    * same type.
    */
  def defer[A](fa: => Task[A]): Task[A] =
    Suspend(fa _)

  /** Alias for [[defer]]. */
  def suspend[A](fa: => Task[A]): Task[A] =
    Suspend(fa _)

  /** Promote a non-strict value to a Task that is memoized on the first
    * evaluation, the result being then available on subsequent evaluations.
    */
  def evalOnce[A](a: => A): Task[A] =
    Delay(Coeval.Once(a _))

  /** Promote a non-strict value to a Task, catching exceptions in the
    * process.
    *
    * Note that since `Task` is not memoized, this will recompute the
    * value each time the `Task` is executed.
    */
  def eval[A](a: => A): Task[A] =
    Delay(Coeval.Always(a _))

  /** Alias for [[eval]]. */
  def delay[A](a: => A): Task[A] = eval(a)

  /** A [[Task]] instance that upon evaluation will never complete. */
  def never[A]: Task[A] = neverRef

  /** Builds a [[Task]] instance out of a Scala `Try`. */
  def fromTry[A](a: Try[A]): Task[A] =
    Delay(Coeval.fromTry(a))

  /** Keeps calling `f` until it returns a `Right` result.
    *
    * Based on Phil Freeman's
    * [[http://functorial.com/stack-safety-for-free/index.pdf Stack Safety for Free]].
    */
  def tailRecM[A,B](a: A)(f: A => Task[Either[A,B]]): Task[B] =
    Task.defer(f(a)).flatMap {
      case Left(continueA) => tailRecM(continueA)(f)
      case Right(b) => Task.now(b)
    }

  private[this] final val neverRef: Async[Nothing] =
    Async((_,_) => ())

  /** A `Task[Unit]` provided for convenience. */
  final val unit: Task[Unit] = Now(())

  /** Reusable task instance used in `Task#asyncBoundary` */
  private final val forkedUnit = Task.fork(Task.unit)

  /** Transforms a [[Coeval]] into a [[Task]]. */
  def coeval[A](a: Coeval[A]): Task[A] = Delay(a)

  /** Mirrors the given source `Task`, but upon execution ensure
    * that evaluation forks into a separate (logical) thread.
    *
    * The [[monix.execution.Scheduler Scheduler]] used will be
    * the one that is used to start the run-loop in `runAsync`.
    *
    * @param fa is the task that will get executed asynchronously
    */
  def fork[A](fa: Task[A]): Task[A] =
    Async { (context, cb) =>
      // Asynchronous boundary
      implicit val s = context.scheduler
      Task.unsafeStartAsync(fa, context, cb)
    }

  /** Mirrors the given source `Task`, but upon execution ensure
    * that evaluation forks into a separate (logical) thread.
    *
    * The given [[monix.execution.Scheduler Scheduler]]  will be
    * used for execution of the [[Task]], effectively overriding the
    * `Scheduler` that's passed in `runAsync`. Thus you can
    * execute a whole `Task` on a separate thread-pool, useful for
    * example in case of doing I/O.
    *
    * @param fa is the task that will get executed asynchronously
    * @param scheduler is the scheduler to use for execution
    */
  def fork[A](fa: Task[A], scheduler: Scheduler): Task[A] =
    Async { (context, cb) =>
      // Asynchronous boundary
      val newContext = context.copy(scheduler = scheduler)
      Task.unsafeStartAsync(fa, newContext, cb)
    }

  /** Create a `Task` from an asynchronous computation.
    *
    * Alias for [[create]].
    */
  def async[A](register: (Scheduler, Callback[A]) => Cancelable): Task[A] =
    create(register)

  /** Create a `Task` from an asynchronous computation, which takes the
    * form of a function with which we can register a callback.
    *
    * This can be used to translate from a callback-based API to a
    * straightforward monadic version.
    *
    * Contract:
    *
    *  - execution of the `register` callback is asynchronous,
    *    always forking a (logical) thread
    *  - execution of the `onSuccess` and `onError` callbacks, is also
    *    async, however they are executed on the current thread /
    *    call-stack if the scheduler is enhanced for execution of
    *    [[monix.execution.schedulers.TrampolinedRunnable trampolined runnables]]
    *
    * This asynchrony is needed because [[create]] is supposed to be
    * safe or otherwise, depending on the executed logic, one can end
    * up with a stack overflow exception. So this contract happens in
    * order to guarantee safety. In order to bypass this, one can use
    * [[unsafeCreate]], but that's more difficult and meant for people
    * knowing what they are doing.
    *
    * @param register is a function that will be called when this `Task`
    *        is executed, receiving a callback as a parameter, a
    *        callback that the user is supposed to call in order to
    *        signal the desired outcome of this `Task`.
    */
  def create[A](register: (Scheduler, Callback[A]) => Cancelable): Task[A] =
    TaskCreate(register)

  /** Constructs a lazy [[Task]] instance whose result
    * will be computed asynchronously.
    *
    * **WARNING:** Unsafe to use directly, only use if you know
    * what you're doing. For building `Task` instances safely
    * see [[create Task.create]].
    *
    * Rules of usage:
    *
    *  - the received `StackedCancelable` can be used to store
    *    cancelable references that will be executed upon cancel;
    *    every `push` must happen at the beginning, before any
    *    execution happens and `pop` must happen afterwards
    *    when the processing is finished, before signaling the
    *    result
    *  - the received `FrameRef` indicates the current frame
    *    index and must be reset on real asynchronous boundaries
    *    (which avoids doing extra async boundaries in batched
    *    execution mode)
    *  - before execution, an asynchronous boundary is recommended,
    *    to avoid stack overflow errors, but can happen using the
    *    scheduler's facilities for trampolined execution
    *  - on signaling the result (`onSuccess`, `onError`),
    *    another async boundary is necessary, but can also
    *    happen with the scheduler's facilities for trampolined
    *    execution (e.g. `asyncOnSuccess` and `asyncOnError`)
    *
    * **WARNING:** note that not only is this builder unsafe, but also
    * unstable, as the [[OnFinish]] callback type is exposing volatile
    * internal implementation details. This builder is meant to create
    * optimized asynchronous tasks, but for normal usage prefer
    * [[Task.create]].
    */
  def unsafeCreate[A](onFinish: OnFinish[A]): Task[A] =
    Async(onFinish)

  /** Converts the given Scala `Future` into a `Task`.
    *
    * NOTE: if you want to defer the creation of the future, use
    * in combination with [[defer]].
    */
  def fromFuture[A](f: Future[A]): Task[A] =
    TaskFromFuture(f)

  /** Creates a `Task` that upon execution will execute both given tasks
    * (possibly in parallel in case the tasks are asynchronous) and will
    * return the result of the task that manages to complete first,
    * along with a cancelable future of the other task.
    *
    * If the first task that completes
    */
  def chooseFirstOf[A,B](fa: Task[A], fb: Task[B]): Task[Either[(A, CancelableFuture[B]), (CancelableFuture[A], B)]] =
    TaskChooseFirstOf(fa, fb)

  /** Creates a `Task` that upon execution will return the result of the
    * first completed task in the given list and then cancel the rest.
    */
  def chooseFirstOfList[A](tasks: TraversableOnce[Task[A]]): Task[A] =
    TaskChooseFirstOfList(tasks)

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
    val init = eval(cbf(in))
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
    val init = eval(cbf(in))
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
    * for the more efficient alternative.
    */
  def gather[A, M[X] <: TraversableOnce[X]](in: M[Task[A]])
    (implicit cbf: CanBuildFrom[M[Task[A]], A, M[A]]): Task[M[A]] =
    TaskGather(in, () => cbf(in))

   /** Given a `TraversableOnce[A]` and a function `A => Task[B]`,
   * nondeterministically apply the function to each element of the collection
   * and return a task that will signal a collection of the results once all
   * tasks are finished.
   *
   * This function is the nondeterministic analogue of `traverse` and should
   * behave identically to `traverse` so long as there is no interaction between
   * the effects being gathered. However, unlike `traverse`, which decides on
   * a total order of effects, the effects in a `wander` are unordered with
   * respect to each other.
   *
   * Although the effects are unordered, we ensure the order of results
   * matches the order of the input sequence. Also see [[wanderUnordered]]
   * for the more efficient alternative.
   *
   * It's a generalized version of [[gather]].
   */
  def wander[A, B, M[X] <: TraversableOnce[X]](in: M[A])(f: A => Task[B])
    (implicit cbf: CanBuildFrom[M[A], B, M[B]]): Task[M[B]] =
    Task.eval(in.map(f)).flatMap(col => TaskGather(col, () => cbf(in)))

  /** Nondeterministically gather results from the given collection of tasks,
    * without keeping the original ordering of results.
    *
    * If the tasks in the list are set to execute asynchronously, forking
    * logical threads, then the tasks will execute in parallel.
    *
    * This function is similar to [[gather]], but neither the effects nor the
    * results will be ordered. Useful when you don't need ordering because:
    *
    *  - it has non-blocking behavior (but not wait-free)
    *  - it can be more efficient (compared with [[gather]]), but not
    *    necessarily (if you care about performance, then test)
    *
    * @param in is a list of tasks to execute
    */
  def gatherUnordered[A](in: TraversableOnce[Task[A]]): Task[List[A]] =
    TaskGatherUnordered(in)

  /** Given a `TraversableOnce[A]` and a function `A => Task[B]`,
    * nondeterministically apply the function to each element of the collection
    * without keeping the original ordering of the results.
    *
    * This function is similar to [[wander]], but neither the effects nor the
    * results will be ordered. Useful when you don't need ordering because:
    *
    *  - it has non-blocking behavior (but not wait-free)
    *  - it can be more efficient (compared with [[wander]]), but not
    *    necessarily (if you care about performance, then test)
    *
    * It's a generalized version of [[gatherUnordered]].
    */
  def wanderUnordered[A, B, M[X] <: TraversableOnce[X]](in: M[A])(f: A => Task[B]): Task[List[B]] =
    Task.eval(in.map(f)).flatMap(gatherUnordered)

  /** Apply a mapping functions to the results of two tasks, nondeterministically
    * ordering their effects.
    *
    * If the two tasks are synchronous, they'll get executed one
    * after the other, with the result being available asynchronously.
    * If the two tasks are asynchronous, they'll get scheduled for execution
    * at the same time and in a multi-threading environment they'll execute
    * in parallel and have their results synchronized.
    */
  def mapBoth[A1,A2,R](fa1: Task[A1], fa2: Task[A2])(f: (A1,A2) => R): Task[R] =
    TaskMapBoth(fa1, fa2)(f)

  /** Gathers the results from a sequence of tasks into a single list.
    * The effects are not ordered, but the results are.
    */
  def zipList[A](sources: Task[A]*): Task[List[A]] = {
    val init = eval(mutable.ListBuffer.empty[A])
    val r = sources.foldLeft(init)((acc,elem) => Task.mapBoth(acc,elem)(_ += _))
    r.map(_.toList)
  }

  /** Pairs two [[Task]] instances. */
  def zip2[A1,A2,R](fa1: Task[A1], fa2: Task[A2]): Task[(A1,A2)] =
    Task.mapBoth(fa1, fa2)((_,_))

  /** Pairs two [[Task]] instances, creating a new instance that will apply
    * the given mapping function to the resulting pair. */
  def zipMap2[A1,A2,R](fa1: Task[A1], fa2: Task[A2])(f: (A1,A2) => R): Task[R] =
    Task.mapBoth(fa1, fa2)(f)

  /** Pairs three [[Task]] instances. */
  def zip3[A1,A2,A3](fa1: Task[A1], fa2: Task[A2], fa3: Task[A3]): Task[(A1,A2,A3)] =
    zipMap3(fa1,fa2,fa3)((a1,a2,a3) => (a1,a2,a3))
  /** Pairs four [[Task]] instances. */
  def zip4[A1,A2,A3,A4](fa1: Task[A1], fa2: Task[A2], fa3: Task[A3], fa4: Task[A4]): Task[(A1,A2,A3,A4)] =
    zipMap4(fa1,fa2,fa3,fa4)((a1,a2,a3,a4) => (a1,a2,a3,a4))
  /** Pairs five [[Task]] instances. */
  def zip5[A1,A2,A3,A4,A5](fa1: Task[A1], fa2: Task[A2], fa3: Task[A3], fa4: Task[A4], fa5: Task[A5]): Task[(A1,A2,A3,A4,A5)] =
    zipMap5(fa1,fa2,fa3,fa4,fa5)((a1,a2,a3,a4,a5) => (a1,a2,a3,a4,a5))
  /** Pairs six [[Task]] instances. */
  def zip6[A1,A2,A3,A4,A5,A6](fa1: Task[A1], fa2: Task[A2], fa3: Task[A3], fa4: Task[A4], fa5: Task[A5], fa6: Task[A6]): Task[(A1,A2,A3,A4,A5,A6)] =
    zipMap6(fa1,fa2,fa3,fa4,fa5,fa6)((a1,a2,a3,a4,a5,a6) => (a1,a2,a3,a4,a5,a6))

  /** Pairs three [[Task]] instances,
    * applying the given mapping function to the result.
    */
  def zipMap3[A1,A2,A3,R](fa1: Task[A1], fa2: Task[A2], fa3: Task[A3])(f: (A1,A2,A3) => R): Task[R] = {
    val fa12 = zip2(fa1, fa2)
    zipMap2(fa12, fa3) { case ((a1,a2), a3) => f(a1,a2,a3) }
  }

  /** Pairs four [[Task]] instances,
    * applying the given mapping function to the result.
    */
  def zipMap4[A1,A2,A3,A4,R](fa1: Task[A1], fa2: Task[A2], fa3: Task[A3], fa4: Task[A4])(f: (A1,A2,A3,A4) => R): Task[R] = {
    val fa123 = zip3(fa1, fa2, fa3)
    zipMap2(fa123, fa4) { case ((a1,a2,a3), a4) => f(a1,a2,a3,a4) }
  }

  /** Pairs five [[Task]] instances,
    * applying the given mapping function to the result.
    */
  def zipMap5[A1,A2,A3,A4,A5,R](fa1: Task[A1], fa2: Task[A2], fa3: Task[A3], fa4: Task[A4], fa5: Task[A5])(f: (A1,A2,A3,A4,A5) => R): Task[R] = {
    val fa1234 = zip4(fa1, fa2, fa3, fa4)
    zipMap2(fa1234, fa5) { case ((a1,a2,a3,a4), a5) => f(a1,a2,a3,a4,a5) }
  }

  /** Pairs six [[Task]] instances,
    * applying the given mapping function to the result.
    */
  def zipMap6[A1,A2,A3,A4,A5,A6,R](fa1: Task[A1], fa2: Task[A2], fa3: Task[A3], fa4: Task[A4], fa5: Task[A5], fa6: Task[A6])(f: (A1,A2,A3,A4,A5,A6) => R): Task[R] = {
    val fa12345 = zip5(fa1, fa2, fa3, fa4, fa5)
    zipMap2(fa12345, fa6) { case ((a1,a2,a3,a4,a5), a6) => f(a1,a2,a3,a4,a5,a6) }
  }

  /** A frame index is a number representing the current run-loop
    * cycle. It gets used for automatically forcing asynchronous
    * boundaries, according to the
    * [[monix.execution.schedulers.ExecutionModel ExecutionModel]]
    * injected by the
    * [[monix.execution.Scheduler Scheduler]].
    */
  type FrameIndex = Int

  /** Type alias representing callbacks for
    * [[unsafeCreate asynchronous]] tasks.
    */
  type OnFinish[+A] = (Context, Callback[A]) => Unit

  /** Set of options for customizing the task's behavior.
    *
    * @param autoCancelableRunLoops should be set to `true` in
    *        case you want `flatMap` driven loops to be
    *        auto-cancelable. Defaults to `false`.
    */
  final case class Options(
    autoCancelableRunLoops: Boolean) {

    /** Creates a new set of options from the source, but with
      * the [[autoCancelableRunLoops]] value set to `true`.
      */
    def enableAutoCancelableRunLoops: Options =
      copy(autoCancelableRunLoops = true)

    /** Creates a new set of options from the source, but with
      * the [[autoCancelableRunLoops]] value set to `false`.
      */
    def disableAutoCancelableRunLoops: Options =
      copy(autoCancelableRunLoops = false)
  }

  /** Default [[Options]] to use for [[Task]] evaluation,
    * thus:
    *
    *  - `autoCancelableRunLoops` is `false` by default
    *
    * On top of the JVM the default can be overridden by
    * setting the following system properties:
    *
    *  - `monix.environment.autoCancelableRunLoops`
    *    (`true`, `yes` or `1` for enabling)
    *
    * @see [[Task.Options]]
    */
  val defaultOptions: Options = {
    if (Platform.isJS)
      Options(autoCancelableRunLoops = false)
    else
      Options(
        autoCancelableRunLoops =
          Option(System.getProperty("monix.environment.autoCancelableRunLoops", ""))
            .map(_.toLowerCase)
            .exists(v => v == "yes" || v == "true" || v == "1")
      )
  }

  /** The `Context` under which [[Task]] is supposed to
    * be executed.
    *
    * @param scheduler is the [[monix.execution.Scheduler Scheduler]]
    *        in charge of evaluation on `runAsync`.
    *
    * @param connection is the
    *        [[monix.execution.cancelables.StackedCancelable StackedCancelable]]
    *        that handles the cancellation on `runAsync`
    *
    * @param frameRef is a thread-local counter that keeps track of the current
    *        frame index of the run-loop. The run-loop is supposed to
    *        force an asynchronous boundary upon reaching a certain
    *        threshold, when the task is evaluated with
    *        [[monix.execution.schedulers.ExecutionModel.BatchedExecution]].
    *        And this `frameIndexRef` should be reset whenever a real
    *        asynchronous boundary happens.
    *
    * @param options is a set of options for customizing the task's behavior
    *        upon evaluation.
    */
  final case class Context(
    scheduler: Scheduler,
    connection: StackedCancelable,
    frameRef: ThreadLocal[FrameIndex],
    options: Options) {

    /** Helper that returns the
      * [[monix.execution.schedulers.ExecutionModel ExecutionModel]]
      * specified by the [[scheduler]].
      */
    @inline def executionModel: ExecutionModel =
      scheduler.executionModel

    /** Helper that returns `true` if the current `Task` run-loop
      * should be canceled or `false` otherwise.
      */
    @inline def shouldCancel: Boolean =
      options.autoCancelableRunLoops &&
      connection.isCanceled
  }

  // We always start from 1
  private final val frameStart: FrameIndex = 1

  /** Creates a `ThreadLocal[FrameIndex]` reference to use as
    * the default. The starting frame index should always be `1`.
    */
  private[eval] def startFrameRef(): ThreadLocal[FrameIndex] =
    ThreadLocal(frameStart)

  /** Creates a new [[CallStack]] */
  private def createCallStack(): CallStack = ArrayStack(16)

  /** [[Task]] state describing an immediate synchronous value. */
  private final case class Now[A](value: A) extends Task[A] {
    // Optimization to avoid the run-loop
    override def runAsync(cb: Callback[A])(implicit s: Scheduler): Cancelable = {
      if (s.executionModel == AlwaysAsyncExecution)
        s.executeAsync(() => cb.onSuccess(value))
      else
        cb.onSuccess(value)

      Cancelable.empty
    }

    // Optimization to avoid the run-loop
    override def runAsync(implicit s: Scheduler): CancelableFuture[A] =
      CancelableFuture.successful(value)

    override def toString: String =
      s"Task.Now($value)"
  }

  /** [[Task]] state that evaluates a [[Coeval]]. */
  private final case class Delay[A](coeval: Coeval[A]) extends Task[A] {
    // Optimization to avoid the run-loop
    override def runAsync(cb: Callback[A])(implicit s: Scheduler): Cancelable = {
      if (s.executionModel == AlwaysAsyncExecution)
        s.executeAsync(() => cb(coeval))
      else
        cb(coeval)

      Cancelable.empty
    }

    // Optimization to avoid the run-loop
    override def runAsync(implicit s: Scheduler): CancelableFuture[A] = {
      if (s.executionModel != AlwaysAsyncExecution)
        coeval.runAttempt match {
          case Coeval.Now(value) =>
            CancelableFuture.successful(value)
          case Coeval.Error(ex) =>
            CancelableFuture.failed(ex)
        }
      else {
        val p = Promise[A]()
        s.executeAsync(() => p.complete(coeval.runAttempt.asScala))
        CancelableFuture(p.future, Cancelable.empty)
      }
    }

    override def toString: String =
      s"Task.Delay($coeval)"
  }

  /** Constructs a lazy [[Task]] instance whose result will
    * be computed asynchronously.
    *
    * Unsafe to build directly, only use if you know what you're doing.
    * For building `Async` instances safely, see [[create]].
    */
  private final case class Async[+A](onFinish: OnFinish[A]) extends Task[A] {
    override def toString: String =
      s"Task.Async($onFinish)"
  }

  /** Internal state, the result of [[Task.defer]] */
  private final case class Suspend[+A](thunk: () => Task[A]) extends Task[A] {
    override def toString: String =
      s"Task.Suspend($thunk)"
  }

  /** Internal [[Task]] state that is the result of applying `flatMap`. */
  private final case class BindSuspend[A,B](thunk: () => Task[A], f: A => Task[B]) extends Task[B] {
    override def toString: String =
      s"Task.BindSuspend($thunk, $f)"
  }

  /** Internal [[Task]] state that is the result of applying `flatMap`
    * over an [[Async]] value.
    */
  private final case class BindAsync[A,B](onFinish: OnFinish[A], f: A => Task[B]) extends Task[B] {
    override def toString: String =
      s"Task.BindAsync($onFinish, $f)"
  }

  /** Internal [[Task]] state that defers the evaluation of the
    * given [[Task]] and upon execution memoize its result to
    * be available for later evaluations.
    */
  private final class MemoizeSuspend[A](f: () => Task[A]) extends Task[A] {
    private[this] var thunk: () => Task[A] = f
    private[this] val state = Atomic(null : AnyRef)

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

    override def runAsync(cb: Callback[A])(implicit s: Scheduler): Cancelable =
      state.get match {
        case null =>
          super.runAsync(cb)(s)
        case (p: Promise[_], conn: StackedCancelable) =>
          val f = p.asInstanceOf[Promise[A]].future
          f.onComplete(cb)
          conn
        case result: Try[_] =>
          cb(result.asInstanceOf[Try[A]])
          Cancelable.empty
      }

    override def runAsync(implicit s: Scheduler): CancelableFuture[A] =
      state.get match {
        case null =>
          super.runAsync(s)
        case (p: Promise[_], conn: StackedCancelable) =>
          val f = p.asInstanceOf[Promise[A]].future
          CancelableFuture(f, conn)
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

    @tailrec def execute(context: Context, cb: Callback[A], stack: CallStack, nextFrame: FrameIndex): Boolean = {
      implicit val s = context.scheduler
      state.get match {
        case null =>
          val p = Promise[A]()

          if (!state.compareAndSet(null, (p, context.connection)))
            execute(context, cb, stack, nextFrame) // retry
          else {
            val underlying = try thunk() catch { case NonFatal(ex) => raiseError(ex) }

            val callback = new Callback[A] {
              def onError(ex: Throwable): Unit = {
                memoizeValue(Failure(ex))
                if ((stack eq null) || stack.isEmpty) cb.onError(ex) else
                  // Resuming trampoline with the rest of the binds
                  Task.internalRestartTrampolineAsync(raiseError(ex), context, cb, stack)
              }

              def onSuccess(value: A): Unit = {
                memoizeValue(Success(value))
                if ((stack eq null) || stack.isEmpty) cb.onSuccess(value) else
                  // Resuming trampoline with the rest of the binds
                  Task.internalRestartTrampolineAsync(now(value), context, cb, stack)
              }
            }

            // Asynchronous boundary to prevent stack-overflows!
            s.executeTrampolined { () =>
              internalStartTrampolineRunLoop(
                underlying, context, callback, null, nextFrame)
            }

            true
          }

        case (p: Promise[_], mainCancelable: StackedCancelable) =>
          // execution is pending completion
          context.connection push mainCancelable
          p.asInstanceOf[Promise[A]].future.onComplete { r =>
            context.connection.pop()
            context.frameRef.reset()
            Task.internalStartTrampolineRunLoop(fromTry(r), context, cb, stack, Task.frameStart)
          }
          true

        case _: Try[_] =>
          // Race condition happened
          false
      }
    }

    override def toString: String =
      s"Task.MemoizeSuspend(${state.get})"
  }

  private type Current = Task[Any]
  private type Bind = Any => Task[Any]
  private type CallStack = ArrayStack[Bind]

  /** Unsafe utility - starts the execution of a Task with a guaranteed
    * asynchronous boundary, by providing
    * the needed [[monix.execution.Scheduler Scheduler]],
    * [[monix.execution.cancelables.StackedCancelable StackedCancelable]]
    * and [[Callback]].
    *
    * DO NOT use directly, as it is UNSAFE to use, unless you know
    * what you're doing. Prefer [[Task.runAsync(cb* Task.runAsync]]
    * and `Task.fork`.
    */
  def unsafeStartAsync[A](source: Task[A], context: Context, cb: Callback[A]): Unit =
    internalRestartTrampolineAsync(source, context, cb, null)

  /** Unsafe utility - starts the execution of a Task with a guaranteed
    * [[monix.execution.schedulers.TrampolinedRunnable trampolined asynchronous boundary]],
    * by providing the needed [[monix.execution.Scheduler Scheduler]],
    * [[monix.execution.cancelables.StackedCancelable StackedCancelable]]
    * and [[Callback]].
    *
    * DO NOT use directly, as it is UNSAFE to use, unless you know
    * what you're doing. Prefer [[Task.runAsync(cb* Task.runAsync]]
    * and `Task.fork`.
    */
  def unsafeStartTrampolined[A](source: Task[A], context: Context, cb: Callback[A]): Unit =
    context.scheduler.executeTrampolined { () =>
      internalStartTrampolineRunLoop(source, context, cb, null, context.frameRef.get())
    }

  /** Unsafe utility - starts the execution of a Task, by providing
    * the needed [[monix.execution.Scheduler Scheduler]],
    * [[monix.execution.cancelables.StackedCancelable StackedCancelable]]
    * and [[Callback]].
    *
    * DO NOT use directly, as it is UNSAFE to use, unless you know
    * what you're doing. Prefer [[Task.runAsync(cb* Task.runAsync]].
    */
  def unsafeStartNow[A](source: Task[A], context: Context, cb: Callback[A]): Unit =
    internalStartTrampolineRunLoop(source, context, cb, null, context.frameRef.get())

  /** Internal utility, for forcing an asynchronous boundary in the
    * trampoline loop.
    */
  @inline private def internalRestartTrampolineAsync[A](
    source: Task[A],
    context: Context,
    cb: Callback[A],
    stack: CallStack): Unit = {

    if (!context.shouldCancel)
      context.scheduler.executeAsync { () =>
        // Resetting the frameRef, as a real asynchronous boundary happened
        context.frameRef.reset()
        internalStartTrampolineRunLoop(source, context, cb, stack, frameStart)
      }
  }

  /** Internal utility, starts or resumes evaluation of
    * the run-loop from where it left off.
    *
    * The `frameIndex=1` default value ensures that the
    * first cycle of the trampoline gets executed regardless of
    * the `ExecutionModel`.
    */
  private[eval] def internalStartTrampolineRunLoop[A](
    source: Task[A],
    context: Context,
    cb: Callback[A],
    stack: CallStack,
    frameIndex: FrameIndex): Unit = {

    final class RestartCallback(context: Context, cb: Callback[Any]) extends Callback[Any] {
      private[this] var stack: CallStack = _
      private[this] val frameRef = context.frameRef

      def prepare(stack: CallStack): Unit = {
        this.stack = stack
      }

      def onSuccess(value: Any): Unit = {
        val stack = this.stack
        this.stack = null
        runLoop(Now(value), cb, this, stack, frameRef.get())
      }

      def onError(ex: Throwable): Unit =
        cb.onError(ex)
    }

    @inline def executeOnFinish(
      em: ExecutionModel,
      cb: Callback[Any],
      rcb: RestartCallback,
      stack: CallStack,
      onFinish: OnFinish[Any],
      nextFrame: FrameIndex): Unit = {

      if (!context.shouldCancel) {
        // We are going to resume the frame index from where we left,
        // but only if no real asynchronous execution happened. So in order
        // to detect asynchronous execution, we are reading a thread-local
        // variable that's going to be reset in case of a thread jump.
        // Obviously this doesn't work for Javascript or for single-threaded
        // thread-pools, but that's OK, as it only means that in such instances
        // we can experience more async boundaries and everything is fine for
        // as long as the implementation of `Async` tasks are triggering
        // a `frameRef.reset` on async boundaries.
        context.frameRef.set(nextFrame)

        // Optimization: no need to restart the run-loop if we know that
        // this is going to be the final iteration
        if ((stack eq null) || stack.isEmpty) onFinish(context, cb) else {
          rcb.prepare(stack)
          onFinish(context, rcb)
        }
      }
    }

    def runLoop(
      state: Current,
      cb: Callback[Any],
      rcb: RestartCallback,
      initialStack: CallStack,
      startFrame: FrameIndex): Unit = {

      var stack = initialStack
      val em = context.executionModel
      var cursor = state
      var frameIndex = startFrame

      while (true) {
        if (frameIndex == 0) {
          internalRestartTrampolineAsync(cursor, context, cb, stack)
          return
        }
        else cursor match {
          case Now(value) =>
            if ((stack eq null) || stack.isEmpty) {
              cb.onSuccess(value)
              return
            }
            else {
              // Next iteration please
              val f = stack.pop()
              cursor = try f(value) catch { case NonFatal(ex) => raiseError(ex) }
              frameIndex = em.nextFrameIndex(frameIndex)
            }

          case Delay(coeval) =>
            val result = coeval.runAttempt
            if (result.isFailure || (stack eq null) || stack.isEmpty) {
              cb(result)
              return
            }
            else {
              // Next iteration please
              val f = stack.pop()
              cursor = try f(result.get) catch { case NonFatal(ex) => raiseError(ex) }
              frameIndex = em.nextFrameIndex(frameIndex)
            }

          case Suspend(thunk) =>
            // Next iteration please
            cursor = try thunk() catch { case NonFatal(ex) => raiseError(ex) }
            frameIndex = em.nextFrameIndex(frameIndex)

          case ref: MemoizeSuspend[_] =>
            val nextFrame = em.nextFrameIndex(frameIndex)
            // Already processed?
            ref.value match {
              case Some(materialized) =>
                // Next iteration please
                cursor = Delay(materialized)
                frameIndex = nextFrame
              case None =>
                val anyRef = ref.asInstanceOf[MemoizeSuspend[Any]]
                val isSuccess = anyRef.execute(context, cb, stack, nextFrame)
                if (isSuccess) return else {
                  // retry?
                  frameIndex = nextFrame
                }
            }

          case BindSuspend(thunk, f) =>
            // Next iteration please
            cursor = try thunk() catch { case NonFatal(ex) => raiseError(ex) }
            if (stack eq null) stack = createCallStack()
            stack.push(f.asInstanceOf[Bind])
            frameIndex = em.nextFrameIndex(frameIndex)

          case BindAsync(onFinish, f) =>
            if (stack eq null) stack = createCallStack()
            stack.push(f.asInstanceOf[Bind])
            frameIndex = em.nextFrameIndex(frameIndex)
            executeOnFinish(em, cb, rcb, stack, onFinish, frameIndex)
            return

          case Async(onFinish) =>
            frameIndex = em.nextFrameIndex(frameIndex)
            executeOnFinish(em, cb, rcb, stack, onFinish, frameIndex)
            return
        }
      }
    }

    val callback = cb.asInstanceOf[Callback[Any]]
    val rcb = new RestartCallback(context, callback)
    runLoop(source, callback, rcb, stack, frameIndex)
  }

  /** A run-loop that attempts to complete a
    * [[monix.execution.CancelableFuture CancelableFuture]] synchronously ,
    * falling back to [[internalStartTrampolineRunLoop]] and actual asynchronous execution
    * in case of an asynchronous boundary.
    */
  private def internalStartTrampolineForFuture[A](
    source: Task[A],
    context: Context,
    stack: CallStack,
    frameStart: FrameIndex): CancelableFuture[A] = {

    /* Called when we hit the first async boundary. */
    def goAsync(
      source: Current,
      context: Context,
      stack: CallStack,
      nextFrame: FrameIndex,
      forceAsync: Boolean): CancelableFuture[Any] = {

      val p = Promise[Any]()
      val cb: Callback[Any] = new Callback[Any] {
        def onSuccess(value: Any): Unit = p.trySuccess(value)
        def onError(ex: Throwable): Unit = p.tryFailure(ex)
      }

      if (forceAsync)
        internalRestartTrampolineAsync(source, context, cb, stack)
      else
        internalStartTrampolineRunLoop(source, context, cb, stack, nextFrame)

      CancelableFuture(p.future, context.connection)
    }

    /* Loop that evaluates a Task until the first async boundary is hit,
     * or until the evaluation is finished, whichever comes first.
     */
    @tailrec def runLoop(
      source: Current,
      context: Context,
      em: ExecutionModel,
      stack: CallStack,
      frameIndex: Int): CancelableFuture[Any] = {

      if (frameIndex == 0) {
        // Asynchronous boundary is forced
        goAsync(source, context, stack,
          em.nextFrameIndex(frameIndex),
          forceAsync = true)
      }
      else source match {
        case Now(value) =>
          if ((stack eq null) || stack.isEmpty) {
            CancelableFuture.successful(value)
          } else {
            val f = stack.pop()
            val fa = try f(value) catch { case NonFatal(ex) => raiseError(ex) }
            runLoop(fa, context, em, stack, em.nextFrameIndex(frameIndex))
          }

        case Delay(coeval) =>
          coeval.runAttempt match {
            case Coeval.Error(ex) =>
              CancelableFuture.failed(ex)

            case Coeval.Now(a) =>
              if ((stack eq null) || stack.isEmpty) {
                CancelableFuture.successful(a)
              } else {
                val f = stack.pop()
                val fa = try f(a) catch { case NonFatal(ex) => raiseError(ex) }
                runLoop(fa, context, em, stack, em.nextFrameIndex(frameIndex))
              }
          }

        case Suspend(thunk) =>
          val fa = try thunk() catch { case NonFatal(ex) => raiseError(ex) }
          runLoop(fa, context, em, stack, em.nextFrameIndex(frameIndex))

        case BindSuspend(thunk, f) =>
          val fa = try thunk() catch { case NonFatal(ex) => raiseError(ex) }
          val callStack = if (stack eq null) createCallStack() else stack
          callStack.push(f.asInstanceOf[Bind])
          runLoop(fa, context, em, callStack, em.nextFrameIndex(frameIndex))

        case ref: MemoizeSuspend[_] =>
          ref.asInstanceOf[MemoizeSuspend[A]].value match {
            case Some(materialized) =>
              runLoop(Delay(materialized), context, em, stack, em.nextFrameIndex(frameIndex))
            case None =>
              goAsync(source, context, stack, em.nextFrameIndex(frameIndex), forceAsync = false)
          }

        case async =>
          goAsync(async, context, stack, em.nextFrameIndex(frameIndex), forceAsync = false)
      }
    }

    runLoop(source, context, context.executionModel, stack, frameStart)
      .asInstanceOf[CancelableFuture[A]]
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
      override def ap[A, B](ff: Task[(A) => B])(fa: Task[A]): Task[B] =
        Task.mapBoth(ff,fa)(_(_))
      override def map2[A, B, Z](fa: Task[A], fb: Task[B])(f: (A, B) => Z): Task[Z] =
        Task.mapBoth(fa, fb)(f)
    }

  /** Groups the implementation for the type-classes defined in [[monix.types]]. */
  class TypeClassInstances
    extends Memoizable.Instance[Task]
    with MonadError.Instance[Task,Throwable]
    with Cobind.Instance[Task]
    with MonadRec.Instance[Task] {

    override def pure[A](a: A): Task[A] = Task.now(a)
    override def suspend[A](fa: => Task[A]): Task[A] = Task.defer(fa)
    override def evalOnce[A](a: => A): Task[A] = Task.evalOnce(a)
    override def eval[A](a: => A): Task[A] = Task.eval(a)
    override def memoize[A](fa: Task[A]): Task[A] = fa.memoize
    override val unit: Task[Unit] = Task.now(())

    override def flatMap[A, B](fa: Task[A])(f: (A) => Task[B]): Task[B] =
      fa.flatMap(f)
    override def flatten[A](ffa: Task[Task[A]]): Task[A] =
      ffa.flatten
    override def tailRecM[A, B](a: A)(f: (A) => Task[Either[A, B]]): Task[B] =
      Task.tailRecM(a)(f)
    override def coflatMap[A, B](fa: Task[A])(f: (Task[A]) => B): Task[B] =
      Task.eval(f(fa))
    override def ap[A, B](ff: Task[(A) => B])(fa: Task[A]): Task[B] =
      for (f <- ff; a <- fa) yield f(a)
    override def map2[A, B, Z](fa: Task[A], fb: Task[B])(f: (A, B) => Z): Task[Z] =
      for (a <- fa; b <- fb) yield f(a,b)
    override def map[A, B](fa: Task[A])(f: (A) => B): Task[B] =
      fa.map(f)
    override def raiseError[A](e: Throwable): Task[A] =
      Task.raiseError(e)
    override def onErrorHandle[A](fa: Task[A])(f: (Throwable) => A): Task[A] =
      fa.onErrorHandle(f)
    override def onErrorHandleWith[A](fa: Task[A])(f: (Throwable) => Task[A]): Task[A] =
      fa.onErrorHandleWith(f)
    override def onErrorRecover[A](fa: Task[A])(pf: PartialFunction[Throwable, A]): Task[A] =
      fa.onErrorRecover(pf)
    override def onErrorRecoverWith[A](fa: Task[A])(pf: PartialFunction[Throwable, Task[A]]): Task[A] =
      fa.onErrorRecoverWith(pf)
  }
}
