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

/*
package monix.async

import monix.async.Task.MemoizedTask
import monix.execution.RunLoop.FrameId
import monix.execution._
import monix.execution.cancelables._
import org.sincron.atomic.Atomic

import scala.annotation.tailrec
import scala.collection.generic.CanBuildFrom
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{Future, Promise, TimeoutException}
import scala.language.higherKinds
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}


/** `Task` represents a specification for an asynchronous computation,
  * which when executed will produce an `A` as a result, along with
  * possible side-effects.
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
sealed abstract class Task[+A] { self =>
  /** Characteristic function for our [[monix.async.Task Task]]. Never use this directly.
    *
    * @param isActive is a `Cancelable`
    *        that can either be used to check if the task is canceled
    *        or can be assigned to something that can eventually
    *        cancel the running computation
    * @param frameId represents the current stack depth
    * @param callback is the pair of `onSuccess` and `onError` methods that will
    *        be called when the execution completes

    * @param s is the [[monix.execution.Scheduler Scheduler]]
    *        under that the `Task` will use to fork threads, schedule
    *        with delay and to report errors
    * @return a [[monix.execution.Cancelable Cancelable]] that can be used to
    *         cancel the running computation
    */
  private[monix] def unsafeRun(
    isActive: StackedCancelable,
    frameId: FrameId,
    callback: Callback[A])
    (implicit s: Scheduler): Unit

  /** Triggers the asynchronous execution.
    *
    * @param cb is a callback that will be invoked upon completion.
    * @return a [[monix.execution.Cancelable Cancelable]] that can
    *         be used to cancel a running task
    */
  def runAsync(cb: Callback[A])(implicit s: Scheduler): Cancelable = {
    val isActive = StackedCancelable()
    val safe = Callback.safe(cb)
    // Schedule stack frame to run, prevents stack-overflows.
    RunLoop.start(frameId => self.unsafeRun(isActive, frameId, safe)(s))(s)
    isActive
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
    * @return a [[monix.async.CancelableFuture CancelableFuture]]
    *         that can be used to extract the result or to cancel
    *         a running task.
    */
  def runAsync(implicit s: Scheduler): CancelableFuture[A] = {
    val p = Promise[A]()
    val cancelable = runAsync(new Callback[A] {
      def onSuccess(value: A): Unit = p.trySuccess(value)
      def onError(ex: Throwable): Unit = p.tryFailure(ex)
    })

    CancelableFuture(p.future, cancelable)
  }

  /** Returns a new Task that applies the mapping function to
    * the element emitted by the source.
    */
  def map[B](f: A => B): Task[B] =
    new Task[B] {
      def unsafeRun(active: StackedCancelable, frameId: FrameId, cb: Callback[B])
        (implicit s: Scheduler): Unit = {

        println(s"map -> $frameId")
        RunLoop.stepInterruptibly(active, frameId)(frameId =>
          self.unsafeRun(active, frameId,
            new Callback[A] {
              def onError(ex: Throwable): Unit =
                cb.onError(ex)

              def onSuccess(value: A): Unit = {
                println(s"map onSuccess -> $frameId")
                var streamError = true
                try {
                  val b = f(value)
                  streamError = false
                  cb.onSuccess(b)
                } catch {
                  case NonFatal(ex) if streamError =>
                    cb.onError(ex)
                }
              }
            }))
      }
    }

  /** Materializes the source's result into a `Try`. */
  def materialize: Task[Try[A]] =
    new Task[Try[A]] {
      def unsafeRun(active: StackedCancelable, frameId: FrameId, callback: Callback[Try[A]])
        (implicit s: Scheduler): Unit = {

        RunLoop.stepInterruptibly(active, frameId) { frameId =>
          self.unsafeRun(active, frameId, new Callback[A] {
            def onError(ex: Throwable): Unit = callback.onSuccess(Failure(ex))
            def onSuccess(value: A): Unit = callback.onSuccess(Success(value))
          })
        }
      }
    }

  /** Dematerializes the source's result from a `Try`. */
  def dematerialize[B](implicit ev: A <:< Try[B]): Task[B] =
    new Task[B] {
      def unsafeRun(active: StackedCancelable, frameId: FrameId, callback: Callback[B])
        (implicit s: Scheduler): Unit = {

        RunLoop.stepInterruptibly(active, frameId) { frameId =>
          self.unsafeRun(active, frameId, new Callback[A] {
            def onError(ex: Throwable): Unit = callback.onError(ex)
            def onSuccess(value: A): Unit =
              value.asInstanceOf[Try[B]] match {
                case Success(b) => callback.onSuccess(b)
                case Failure(ex) => callback.onError(ex)
              }
          })
        }
      }
    }

  /** Returns a new Task that applies the mapping function to
    * the element emitted by the source.
    */
  def mapTry[B](f: Try[A] => Try[B]): Task[B] =
    materialize.map(f).dematerialize

  /** Given a source Task that emits another Task, this function
    * flattens the result, returning a Task equivalent to the emitted
    * Task by the source.
    */
  def flatten[B](implicit ev: A <:< Task[B]): Task[B] =
    flatMap(t => t)

  /** Creates a new Task by applying a function to the successful result
    * of the source Task, and returns a task equivalent to the result
    * of the function.
    */
  def flatMap[B](f: A => Task[B]): Task[B] =
    new Task[B] {
      def unsafeRun(active: StackedCancelable, frameId: FrameId, cb: Callback[B])
        (implicit s: Scheduler): Unit = {

        println(s"flatMap -> $frameId")
        RunLoop.stepInterruptibly(active, frameId)(frameId =>
          self.unsafeRun(active, frameId, new Callback[A] {
            def onError(ex: Throwable): Unit =
              cb.onError(ex)

            def onSuccess(value: A): Unit = {
              println(s"flatMap onSuccess -> $frameId")
              var streamError = true
              try {
                val taskU = f(value)
                streamError = false
                RunLoop.stepInterruptibly(active, frameId)(frameId =>
                  taskU.unsafeRun(active, frameId, cb))
              }
              catch {
                case NonFatal(ex) if streamError =>
                  cb.onError(ex)
              }
            }
          }))
      }
    }

  /** Returns a task that waits for the specified `timespan` before
    * executing and mirroring the result of the source.
    */
  def delayExecution(timespan: FiniteDuration): Task[A] =
    new Task[A] { delayed =>
      def unsafeRun(active: StackedCancelable, frameId: FrameId, cb: Callback[A])
        (implicit s: Scheduler): Unit = {

        val task = SingleAssignmentCancelable()
        active push task

        task := s.scheduleOnce(timespan.length, timespan.unit,
          new Runnable {
            def run(): Unit = {
              // At this point it's OK to restart the runLoop.
              RunLoop.startNow { frameId =>
                active.pop()
                self.unsafeRun(active, frameId, cb)
              }
            }
          })
      }
    }

  /** Returns a task that waits for the specified `trigger` to succeed
    * before mirroring the result of the source.
    *
    * If the `trigger` ends in error, then the resulting task will also
    * end in error.
    */
  def delayExecutionWith(trigger: Task[Any]): Task[A] =
    new Task[A] { delayed =>
      def unsafeRun(active: StackedCancelable, frameId: FrameId, cb: Callback[A])
        (implicit s: Scheduler): Unit = delayed.synchronized {

        // Unfortunately we have to synchronize, otherwise we can have a race
        // condition on assignment (the cancelable for scheduleOnce to be assigned
        // after whatever happens in unsafeRun)
        trigger.unsafeRun(active, frameId, new Callback[Any] {
          override def onError(ex: Throwable): Unit =
            cb.onError(ex)

          override def onSuccess(value: Any): Unit = {
            // Forced synchronous execution.
            RunLoop.startAsync { frameId =>
              // Must synchronize because of a possible race condition on
              // the assignment of `active`.
              delayed.synchronized(self.unsafeRun(active, frameId, cb))
            }
          }
        })
      }
    }

  /** Returns a task that executes the source immediately on `runAsync`,
    * but before emitting the `onSuccess` result for the specified
    * duration.
    *
    * Note that if an error happens, then it is streamed immediately
    * with no delay.
    */
  def delayResult(timespan: FiniteDuration): Task[A] =
    new Task[A] {
      def unsafeRun(active: StackedCancelable, frameId: FrameId, cb: Callback[A])
        (implicit s: Scheduler): Unit = {

        RunLoop.stepInterruptibly(active, frameId)(frameId =>
          self.unsafeRun(active, frameId,
            new Callback[A] { self =>
              def onSuccess(value: A): Unit = {
                val task = SingleAssignmentCancelable()
                active push task

                task := s.scheduleOnce(timespan.length, timespan.unit,
                  new Runnable {
                    def run(): Unit = {
                      active.pop()
                      cb.onSuccess(value)
                    }
                  })
              }


              def onError(ex: Throwable): Unit =
                cb.onError(ex)
            }))
      }
    }

  /** Returns a task that executes the source immediately on `runAsync`,
    * but before emitting the `onSuccess` result for the specified
    * duration.
    *
    * Note that if an error happens, then it is streamed immediately
    * with no delay.
    */
  def delayResultBySelector[B](selector: A => Task[B]): Task[A] =
    new Task[A] {
      def unsafeRun(active: StackedCancelable, frameId: FrameId, cb: Callback[A])
        (implicit s: Scheduler): Unit = {

        RunLoop.stepInterruptibly(active, frameId)(frameId =>
          self.unsafeRun(active, frameId,
            new Callback[A] {
              def onSuccess(valueA: A): Unit = {
                var streamErrors = true
                try {
                  val trigger = selector(valueA)
                  streamErrors = false
                  // Forced synchronous execution.
                  RunLoop.startAsync { frameId =>
                    trigger.unsafeRun(active, frameId, new Callback[B] {
                      def onSuccess(value: B): Unit =
                        cb.onSuccess(valueA)
                      def onError(ex: Throwable): Unit =
                        cb.onError(ex)
                    })
                  }
                } catch {
                  case NonFatal(ex) if streamErrors =>
                    cb.onError(ex)
                }
              }

              def onError(ex: Throwable): Unit =
                cb.onError(ex)
            }))
      }
    }

  /** Returns a failed projection of this task.
    *
    * The failed projection is a future holding a value of type
    * `Throwable`, emitting a value which is the throwable of the
    * original task in case the original task fails, otherwise if the
    * source succeeds, then it fails with a `NoSuchElementException`.
    */
  def failed: Task[Throwable] =
    new Task[Throwable] {
      def unsafeRun(active: StackedCancelable, frameId: FrameId, cb: Callback[Throwable])
        (implicit s: Scheduler): Unit = {

        RunLoop.stepInterruptibly(active, frameId)(frameId =>
          // RunLoop step, prevents stack-overflows
          self.unsafeRun(active, frameId, new Callback[A] {
            def onError(ex: Throwable): Unit =
              cb.onSuccess(ex)
            def onSuccess(value: A): Unit =
              cb.onError(new NoSuchElementException("Task.failed"))
          }))
      }
    }

  /** Memoizes the result on the computation and reuses it on subsequent
    * invocations of `runAsync`.
    */
  def memoize: Task[A] =
    new MemoizedTask[A](this)

  /** Creates a new task that will handle any matching throwable that
    * this task might emit.
    *
    * See [[onErrorRecover]] for the version that takes a partial function.
    */
  def onErrorHandle[U >: A](f: Throwable => U): Task[U] =
    new Task[U] {
      def unsafeRun(active: StackedCancelable, frameId: FrameId, cb: Callback[U])
        (implicit s: Scheduler): Unit = {

        RunLoop.stepInterruptibly(active, frameId)(frameId =>
          self.unsafeRun(active, frameId, new Callback[A] {
            def onSuccess(v: A): Unit =
              cb.onSuccess(v)

            def onError(ex: Throwable): Unit = {
              var streamError = true
              try {
                val r = f(ex)
                streamError = false
                cb.onSuccess(r)
              } catch {
                case NonFatal(err) if streamError =>
                  s.reportFailure(ex)
                  cb.onError(err)
              }
            }
          }))
      }
    }

  /** Creates a new task that on error will try to map the error
    * to another value using the provided partial function.
    *
    * See [[onErrorHandle]] for the version that takes a total function.
    */
  def onErrorRecover[U >: A](pf: PartialFunction[Throwable, U]): Task[U] =
    onErrorRecoverWith(pf.andThen(Task.now))

  /** Creates a new task that will handle any matching throwable that
    * this task might emit by executing another task.
    *
    * See [[onErrorRecoverWith]] for the version that takes a partial function.
    */
  def onErrorHandleWith[B >: A](f: Throwable => Task[B]): Task[B] =
    new Task[B] {
      def unsafeRun(active: StackedCancelable, frameId: FrameId, cb: Callback[B])
        (implicit s: Scheduler): Unit = {

        RunLoop.stepInterruptibly(active, frameId)(frameId =>
          // RunLoop step, prevents stack-overflows
          self.unsafeRun(active, frameId, new Callback[A] {
            def onSuccess(v: A): Unit =
              cb.onSuccess(v)

            def onError(ex: Throwable): Unit = {
              var streamError = true
              try {
                val newTask = f(ex)
                streamError = false
                // Fallback to produced task
                RunLoop.stepInterruptibly(active, frameId)(frameId =>
                  newTask.unsafeRun(active, frameId, cb))
              } catch {
                case NonFatal(err) if streamError =>
                  s.reportFailure(ex)
                  cb.onError(err)
              }
            }
          }))
      }
    }

  /** Creates a new task that will try recovering from an error by
    * matching it with another task using the given partial function.
    *
    * See [[onErrorHandleWith]] for the version that takes a total function.
    */
  def onErrorRecoverWith[B >: A](pf: PartialFunction[Throwable, Task[B]]): Task[B] =
    onErrorHandleWith(ex => pf.applyOrElse(ex, Task.error))

  /** Creates a new task that in case of error will fallback to the
    * given backup task.
    */
  def onErrorFallbackTo[B >: A](that: Task[B]): Task[B] =
    new Task[B] {
      def unsafeRun(active: StackedCancelable, frameId: FrameId, cb: Callback[B])
        (implicit s: Scheduler): Unit = {

        RunLoop.stepInterruptibly(active, frameId)(frameId =>
          self.unsafeRun(active, frameId, new Callback[A] {
            def onSuccess(v: A): Unit =
              cb.onSuccess(v)

            def onError(ex: Throwable): Unit = {
              // RunLoop step, prevents stack-overflows
              var streamError = true
              try {
                val newTask = that
                streamError = false
                RunLoop.stepInterruptibly(active, frameId)(frameId =>
                  newTask.unsafeRun(active, frameId, cb))
              } catch {
                case NonFatal(err) if streamError =>
                  s.reportFailure(ex)
                  cb.onError(err)
              }
            }
          }))
      }
    }

  /** Creates a new task that in case of error will retry executing the
    * source again and again, until it succeeds.
    *
    * In case of continuous failure the total number of executions
    * will be `maxRetries + 1`.
    */
  def onErrorRetry(maxRetries: Long): Task[A] =
    new Task[A] {
      def unsafeRun(active: StackedCancelable, frameId: FrameId, cb: Callback[A])
        (implicit s: Scheduler): Unit = {

        def loop(frameId: FrameId, runIdx: Long, ex: Throwable): Unit = {
          if (runIdx <= maxRetries || ex == null)
            RunLoop.stepInterruptibly(active, frameId)(frameId =>
              self.unsafeRun(active, frameId, new Callback[A] {
                def onSuccess(v: A) =
                  cb.onSuccess(v)
                def onError(ex: Throwable): Unit =
                  loop(frameId, runIdx+1, ex)
              }))
          else
            cb.onError(ex)
        }

        loop(frameId, runIdx=0, ex=null)
      }
    }

  /** Creates a new task that in case of error will retry executing the
    * source again and again, until it succeeds.
    *
    * In case of continuous failure the total number of executions
    * will be `maxRetries + 1`.
    */
  def onErrorRetryIf(p: Throwable => Boolean): Task[A] =
    new Task[A] {
      def unsafeRun(active: StackedCancelable, frameId: FrameId, cb: Callback[A])
        (implicit s: Scheduler): Unit = {

        def loop(frameId: FrameId, ex: Throwable): Unit =
          RunLoop.stepInterruptibly(active, frameId) { frameId =>
            self.unsafeRun(active, frameId, new Callback[A] {
              def onSuccess(v: A): Unit = cb.onSuccess(v)

              def onError(ex: Throwable): Unit = {
                var toReport = ex
                val shouldContinue = (ex == null) || (
                  try p(ex) catch {
                    case NonFatal(err) =>
                      toReport = err
                      s.reportFailure(ex)
                      false
                  })

                if (shouldContinue)
                  loop(frameId, ex)
                else
                  cb.onError(toReport)
              }
            })
          }

        loop(frameId, ex=null)
      }
    }

  /** Returns a Task that mirrors the source Task but that triggers a
    * `TimeoutException` in case the given duration passes without the
    * task emitting any item.
    */
  def timeout(after: FiniteDuration): Task[A] =
    new Task[A] {
      def unsafeRun(active: StackedCancelable, frameId: FrameId, cb: Callback[A])
        (implicit s: Scheduler): Unit = {

        val activeGate = Atomic(true)
        val activeGateTask = Cancelable(() => activeGate.set(false))

        val timeoutTask = s.scheduleOnce(after.length, after.unit,
          new Runnable {
            def run(): Unit =
              if (activeGate.getAndSet(false)) {
                active.cancel()
                val ex = new TimeoutException(s"Task timed-out after $after of inactivity")
                cb.onError(ex)
              }
          })

        val mainTask = StackedCancelable()
        active push CompositeCancelable(timeoutTask, mainTask, activeGateTask)

        RunLoop.stepInterruptibly(active, frameId) { frameId =>
          // RunLoop step, prevents stack-overflows
          self.unsafeRun(mainTask, frameId, new Callback[A] {
            def onSuccess(v: A): Unit =
              if (activeGate.getAndSet(false)) {
                timeoutTask.cancel()
                active.popAndCollapse(mainTask)
                cb.onSuccess(v)
              }

            def onError(ex: Throwable): Unit =
              if (activeGate.getAndSet(false)) {
                timeoutTask.cancel()
                active.popAndCollapse(mainTask)
                cb.onError(ex)
              }
          })
        }
      }
    }

  /** Returns a Task that mirrors the source Task but switches to the
    * given backup Task in case the given duration passes without the
    * source emitting any item.
    */
  def timeoutTo[B >: A](after: FiniteDuration, backup: Task[B]): Task[B] =
    new Task[B] {
      def unsafeRun(active: StackedCancelable, frameId: FrameId, cb: Callback[B])
        (implicit s: Scheduler): Unit = {

        val mainTask = StackedCancelable()
        val gate = Atomic(true)
        val gateTask = Cancelable(() => gate.set(false))

        val timeoutTask = s.scheduleOnce(after.length, after.unit,
          new Runnable {
            def run(): Unit =
              if (gate.getAndSet(false)) {
                mainTask.cancel()
                RunLoop.startNow(frameId =>
                  backup.unsafeRun(active, frameId, cb))
              }
          })

        active push CompositeCancelable(gateTask, timeoutTask, mainTask)

        RunLoop.stepInterruptibly(active, frameId) { frameId =>
          // RunLoop step, prevents stack-overflows
          self.unsafeRun(mainTask, frameId, new Callback[A] {
            def onSuccess(v: A): Unit =
              if (gate.getAndSet(false)) {
                timeoutTask.cancel()
                active.popAndCollapse(mainTask)
                cb.onSuccess(v)
              }

            def onError(ex: Throwable): Unit =
              if (gate.getAndSet(false)) {
                timeoutTask.cancel()
                active.popAndCollapse(mainTask)
                cb.onError(ex)
              }
          })
        }
      }
    }

  /** Creates a new task that upon execution will return the result of
    * the first task that completes, while canceling the other.
    */
  def ambWith[B >: A](other: Task[B]): Task[B] =
    new Task[B] {
      def unsafeRun(active: StackedCancelable, frameId: FrameId, cb: Callback[B])
        (implicit s: Scheduler): Unit = {

        val isActive = Atomic(true)
        val firstTask = StackedCancelable()
        val secondTask = StackedCancelable()

        active push CompositeCancelable(firstTask, secondTask, Cancelable(() => isActive.set(false)))

        RunLoop.stepInterruptibly(firstTask, frameId)(frameId =>
          self.unsafeRun(firstTask, frameId, new Callback[A] {
            def onSuccess(value: A): Unit =
              if (isActive.getAndSet(false)) {
                secondTask.cancel()
                active.popAndCollapse(firstTask)
                cb.onSuccess(value)
              }

            def onError(ex: Throwable): Unit =
              if (isActive.getAndSet(false)) {
                secondTask.cancel()
                active.popAndCollapse(firstTask)
                cb.onError(ex)
              }
          }))

        RunLoop.stepInterruptibly(secondTask, frameId)(frameId =>
          other.unsafeRun(secondTask, frameId, new Callback[B] {
            def onSuccess(value: B): Unit =
              if (isActive.getAndSet(false)) {
                firstTask.cancel()
                active.popAndCollapse(secondTask)
                cb.onSuccess(value)
              }

            def onError(ex: Throwable): Unit =
              if (isActive.getAndSet(false)) {
                firstTask.cancel()
                active.popAndCollapse(secondTask)
                cb.onError(ex)
              }
          }))
      }
    }


  /** Zips the values of `this` and `that` task, and creates a new task
    * that will emit the tuple of their results.
    */
  def zip[B](that: Task[B]): Task[(A, B)] =
    Task.map2(this, that)((a,b) => (a,b))

  /** Zips the values of `this` and `that` and applies the given
    * mapping function on their results.
    */
  def zipWith[B,C](that: Task[B])(f: (A,B) => C): Task[C] =
    Task.map2(this, that)(f)
}

object Task {
  /** Returns a new task that, when executed, will emit the result of
    * the given function executed asynchronously.
    */
  def apply[A](f: => A): Task[A] =
    Task.fork(Task.evalAlways(f))

  /** Returns a `Task` that on execution is always successful, emitting
    * the given strict value.
    */
  def now[A](elem: A): Task[A] =
    new Now(elem)

  /** A `Task[Unit]` provided for convenience. */
  val unit: Task[Unit] = now(())

  /** A [[Task]] instance that upon evaluation will never complete. */
  def never[A]: Task[A] = Never

  /** Promote a non-strict value to a Task, catching exceptions in the
    * process.
    *
    * Note that since `Task` is not memoized, this will recompute the
    * value each time the `Task` is executed.
    */
  def evalAlways[A](f: => A): Task[A] =
    new Task[A] {
      def unsafeRun(c: StackedCancelable, fid: FrameId, cb: Callback[A])
        (implicit s: Scheduler): Unit = {
        // protecting against user code errors
        var streamErrors = true
        try {
          val result = f
          streamErrors = false
          cb.onSuccess(result)
        } catch {
          case NonFatal(ex) if streamErrors =>
            cb.onError(ex)
        }
      }
    }

  /** Promote a non-strict value to a Task that is memoized on the first
    * evaluation, the result being then available on subsequent evaluations.
    */
  def evalOnce[A](f: => A): Task[A] =
    evalAlways(f).memoize

  /** Promotes a non-strict value to a Task, but upon the Task evaluation
    * delay its execution by the specified timespan.
    */
  def evalDelayed[A](delay: FiniteDuration, f: => A): Task[A] =
    evalAlways(f).delayExecution(delay)

  /** Promote a non-strict value representing a Task to a Task of the
    * same type.
    */
  def defer[A](task: => Task[A]): Task[A] =
    Task.evalAlways(task).flatten

  /** Mirrors the given source `Task`, but upon execution it forks its
    * evaluation off into a separate (logical) thread.
    */
  def fork[A](task: Task[A]): Task[A] =
    new Task[A] {
      def unsafeRun(active: StackedCancelable, frameId: FrameId, cb: Callback[A])
        (implicit s: Scheduler): Unit = {

        if (RunLoop.isAlwaysAsync)
          RunLoop.startNow(frameId => task.unsafeRun(active, frameId, cb))
        else
          RunLoop.startAsync(frameId => task.unsafeRun(active, frameId, cb))
      }
    }

  /** Returns a task that on execution is always finishing in error
    * emitting the specified exception.
    */
  def error[A](ex: Throwable): Task[A] =
    new Error(ex)

  /** Builder for [[monix.async.Task Task]] instances. For usage on implementing
    * operators or builders. Internal to Monix.
    */
  private[monix] def unsafeCreate[A](f: (Scheduler, StackedCancelable, FrameId, Callback[A]) => Unit): Task[A] =
    new Task[A] {
      def unsafeRun(active: StackedCancelable, frameId: FrameId, cb: Callback[A])
        (implicit s: Scheduler): Unit = f(s, active, frameId, cb)
    }

  /** Create a `Task` from an asynchronous computation, which takes the
    * form of a function with which we can register a callback. This
    * can be used to translate from a callback-based API to a
    * straightforward monadic version.
    *
    * @param register is a function that will be called when this `Task`
    *        is executed, receiving a callback as a parameter, a
    *        callback that the user is supposed to call in order to
    *        signal the desired outcome of this `Task`.
    */
  def async[A](register: (Callback[A], Scheduler) => Cancelable): Task[A] =
    new Task[A] {
      def unsafeRun(active: StackedCancelable, frameId: FrameId, cb: Callback[A])
        (implicit s: Scheduler): Unit = {

        try {
          val callback = new Callback[A] {
            def onSuccess(value: A) = {
              active.pop()
              cb.onSuccess(value)
            }

            def onError(ex: Throwable): Unit = {
              active.pop()
              cb.onError(ex)
            }
          }

          active push register(callback, s)
        } catch {
          case NonFatal(ex) =>
            cb.onError(ex)
        }
      }
    }

  /** Converts the given Scala `Future` into a `Task`.
    *
    * NOTE: if you want to defer the creation of the future, use
    * in combination with [[defer]].
    */
  def fromFuture[A](f: Future[A]): Task[A] =
    new Task[A] {
      def unsafeRun(active: StackedCancelable, frameId: FrameId, cb: Callback[A])
        (implicit s: Scheduler): Unit = {

        f.onComplete {
          case Success(value) =>
            if (!active.isCanceled)
              cb.onSuccess(value)
          case Failure(ex) =>
            if (!active.isCanceled)
              cb.onError(ex)
        }(s)
      }
    }

  /** Given two tasks and a mapping function, returns a new Task that will
    * be the result of the mapping function applied to their results.
    */
  def map2[A,B,R](taskA: Task[A], taskB: Task[B])(f: (A,B) => R): Task[R] = {
    def sendSignal(active: StackedCancelable, cb: Callback[R], a: A, b: B): Unit = {
      var streamErrors = true
      try {
        val r = f(a,b)
        streamErrors = false
        active.pop()
        cb.onSuccess(r)
      } catch {
        case NonFatal(ex) if streamErrors =>
          active.pop().cancel()
          cb.onError(ex)
      }
    }

    new Task[R] {
      def unsafeRun(active: StackedCancelable, frameId: FrameId, cb: Callback[R])
        (implicit scheduler: Scheduler): Unit = {

        val state = Atomic(null : Either[A,B])
        val thisTask = StackedCancelable()
        val thatTask = StackedCancelable()

        val gate = Atomic(true)
        val gateTask = Cancelable(() => gate.getAndSet(false))
        active push CompositeCancelable(thisTask, thatTask, gateTask)

        RunLoop.stepInterruptibly(active, frameId)(frameId =>
          taskA.unsafeRun(thisTask, frameId, new Callback[A] {
            def onError(ex: Throwable): Unit =
              if (gate.getAndSet(false)) {
                active.pop().cancel()
                cb.onError(ex)
              }

            @tailrec def onSuccess(a: A): Unit =
              state.get match {
                case null =>
                  if (!state.compareAndSet(null, Left(a))) onSuccess(a)
                case Right(b) =>
                  sendSignal(active, cb, a, b)
                case s @ Left(_) =>
                  onError(new IllegalStateException(s.toString))
              }
          }))

        RunLoop.stepInterruptibly(active, frameId)(frameId =>
          // RunLoop step, prevents stack-overflows
          taskB.unsafeRun(thatTask, frameId, new Callback[B] {
            def onError(ex: Throwable): Unit =
              if (gate.getAndSet(false)) {
                active.pop().cancel()
                cb.onError(ex)
              }

            @tailrec def onSuccess(b: B): Unit =
              state.get match {
                case null =>
                  if (!state.compareAndSet(null, Right(b))) onSuccess(b)
                case Left(a) =>
                  sendSignal(active, cb, a, b)
                case s @ Right(_) =>
                  onError(new IllegalStateException(s.toString))
              }
          }))
      }
    }
  }

  /** Creates a `Task` that upon execution will return the result of the
    * first completed task in the given list and then cancel the rest.
    */
  def firstCompletedOf[A](tasks: TraversableOnce[Task[A]]): Task[A] =
    new Task[A] {
      def unsafeRun(active: StackedCancelable, frameId: FrameId, cb: Callback[A])
        (implicit s: Scheduler): Unit = {

        val isActive = Atomic(true)
        val composite = CompositeCancelable()
        active push composite

        for (task <- tasks) {
          val taskCancelable = StackedCancelable()
          composite += taskCancelable

          RunLoop.stepInterruptibly(taskCancelable, frameId)(frameId =>
            task.unsafeRun(taskCancelable, frameId, new Callback[A] {
              def onSuccess(value: A): Unit =
                if (isActive.compareAndSet(expect=true, update=false)) {
                  composite -= taskCancelable
                  composite.cancel()
                  active popAndCollapse taskCancelable
                  cb.onSuccess(value)
                }

              def onError(ex: Throwable): Unit =
                if (isActive.compareAndSet(expect=true, update=false)) {
                  composite -= taskCancelable
                  composite.cancel()
                  active popAndCollapse taskCancelable
                  cb.onError(ex)
                }
            }))
        }
      }
    }

  /** Transforms a `TraversableOnce[Task[A]]` into a
    * `Task[TraversableOnce[A]]`.  Useful for reducing many `Task`s
    * into a single `Task`.
    */
  def sequence[A, M[X] <: TraversableOnce[X]](in: M[Task[A]])
    (implicit cbf: CanBuildFrom[M[Task[A]], A, M[A]]): Task[M[A]] = {

    new Task[M[A]] {
      def unsafeRun(cancelable: StackedCancelable, frameId: FrameId, cb: Callback[M[A]])
        (implicit scheduler: Scheduler): Unit = {

        val cancelables = ArrayBuffer.empty[Cancelable]
        var builder = Future.successful(cbf(in))

        for (task <- in) {
          val p = Promise[A]()
          val cancelable = task.runAsync(new Callback[A] {
            def onSuccess(value: A): Unit = p.trySuccess(value)
            def onError(ex: Throwable): Unit = p.tryFailure(ex)
          })

          cancelables += cancelable
          builder = for (r <- builder; a <- p.future) yield r += a
        }

        cancelable push CompositeCancelable(cancelables:_*)
        builder.onComplete {
          case Success(r) =>
            cancelable.pop()
            cb.onSuccess(r.result())
          case Failure(ex) =>
            cancelable.pop()
            cb.onError(ex)
        }
      }
    }
  }

  /** Optimized task for already known strict values. Internal to Monix,
    * not for public consumption.
    *
    * See [[Task.now]] instead.
    */
  private final class Now[+A](value: A) extends Task[A] {
    def unsafeRun(c: StackedCancelable, fid: FrameId, cb: Callback[A])
      (implicit s: Scheduler): Unit =
      cb.onSuccess(value)

    override def runAsync(implicit s: Scheduler): CancelableFuture[A] =
      CancelableFuture(Future.successful(value), Cancelable.empty)
  }

  /** Optimized task for failed outcomes. Internal to Monix, not for
    * public consumption.
    *
    * See [[Task.error]] instead.
    */
  private final class Error(ex: Throwable) extends Task[Nothing] {
    def unsafeRun(c: StackedCancelable, fid: FrameId, cb: Callback[Nothing])
      (implicit s: Scheduler): Unit =
      cb.onError(ex)

    override def runAsync(implicit s: Scheduler): CancelableFuture[Nothing] =
      CancelableFuture(Future.failed(ex), Cancelable.empty)
  }

  /** A task that upon evaluation will never complete. */
  private object Never extends Task[Nothing] {
    def unsafeRun(a: StackedCancelable, fid: FrameId, cb: Callback[Nothing])
      (implicit s: Scheduler): Unit = ()
  }

  /** A task implementation that stores the result on the first `unsafeRun` call
    * and reuses it on subsequent invocations.
    */
  private final class MemoizedTask[A](underlying: Task[A]) extends Task[A] {
    private[this] val state = Atomic(null : AnyRef)

    override def runAsync(implicit s: Scheduler): CancelableFuture[A] =
      state.get match {
        case null => super.runAsync(s)
        case (p: Promise[_], c: StackedCancelable) =>
          val f = p.asInstanceOf[Promise[A]].future
          CancelableFuture(f, c)
        case result: Try[_] =>
          CancelableFuture.fromTry(result.asInstanceOf[Try[A]])
      }

    def unsafeRun(active: StackedCancelable, frameId: FrameId, cb: Callback[A])
      (implicit s: Scheduler): Unit = {

      state.get match {
        case null =>
          val p = Promise[A]()

          if (state.compareAndSet(null, (p, active)))
            RunLoop.stepInterruptibly(active, frameId) { frameId =>
              underlying.unsafeRun(active, frameId, new Callback[A] {
                def onError(ex: Throwable): Unit = {
                  try cb.onError(ex) finally
                    memoizeValue(Failure(ex))
                }

                def onSuccess(value: A): Unit = {
                  try cb.onSuccess(value) finally
                    memoizeValue(Success(value))
                }
              })
            }
          else {
            unsafeRun(active, frameId, cb) // retry
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

    private def memoizeValue(value: Try[A]): Unit = {
      state.getAndSet(value) match {
        case (p: Promise[_], _) =>
          p.asInstanceOf[Promise[A]].complete(value)
        case _ =>
          () // do nothing
      }
    }
  }
}
*/