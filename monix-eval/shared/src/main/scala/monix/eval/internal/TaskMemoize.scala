/*
 * Copyright (c) 2014-2018 by The Monix Project Developers.
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

package monix.eval.internal

import monix.eval.Task.{Context, Error, FrameIndex, Now}
import monix.eval.internal.TaskRunLoop.{restartAsync, startFull}
import monix.eval.{Callback, Coeval, Task}
import monix.execution.Scheduler
import monix.execution.atomic.{Atomic, AtomicAny}
import monix.execution.cancelables.StackedCancelable
import monix.execution.schedulers.TrampolinedRunnable
import scala.concurrent.Promise
import scala.util.{Failure, Success, Try}


private[eval] object TaskMemoize {
  /**
    * Implementation for `.memoize` and `.memoizeOnSuccess`.
    */
  def apply[A](source: Task[A], cacheErrors: Boolean): Task[A] =
    source match {
      case Now(_) | Error(_) =>
        source
      case Task.Eval(Coeval.Suspend(f: LazyVal[A @unchecked]))
        if !cacheErrors || f.cacheErrors =>
        source
      case Task.Async(r: Register[A] @unchecked)
        if !cacheErrors || r.cacheErrors =>
        source
      case _ =>
        Task.Async(new Register(source, cacheErrors))
    }

  private final class Register[A](source: Task[A], val cacheErrors: Boolean)
    extends ((Task.Context, Callback[A]) => Unit) { self =>

    var thunk = source
    val state = Atomic(null : AnyRef)

    def value: Option[Try[A]] =
      state.get match {
        case null => None
        case (p: Promise[_], _) =>
          p.asInstanceOf[Promise[A]].future.value
        case result: Try[_] =>
          Some(result.asInstanceOf[Try[A]])
      }

    def apply(ctx: Context, cb: Callback[A]): Unit = {
      implicit val sc = ctx.scheduler
      value match {
        case Some(result) =>
          cb.asyncApply(result)
        case None =>
          sc.executeTrampolined { () =>
            val ref = startMemoization(self, ctx, cb, ctx.frameRef())
            // Race condition happened
            if (ref ne null) {
              // $COVERAGE-OFF$
              cb(ref)
              // $COVERAGE-ON$
            }
          }
      }
    }
  }

  /** Starts the execution and memoization of a `Task.MemoizeSuspend` state. */
  private def startMemoization[A](
    self: Register[A],
    context: Context,
    cb: Callback[A],
    nextFrame: FrameIndex): Try[A] = {

    // Internal function that stores
    def cacheValue(state: AtomicAny[AnyRef], value: Try[A]): Unit = {
      // Should we cache everything, error results as well,
      // or only successful results?
      if (self.cacheErrors || value.isSuccess) {
        state.getAndSet(value) match {
          case (p: Promise[_], _) =>
            p.asInstanceOf[Promise[A]].complete(value)
          case _ =>
            () // do nothing
        }
        // GC purposes
        self.thunk = null
      } else {
        // Error happened and we are not caching errors!
        val current = state.get
        // Resetting the state to `null` will trigger the
        // execution again on next `runAsync`
        if (state.compareAndSet(current, null))
          current match {
            case (p: Promise[_], _) =>
              p.asInstanceOf[Promise[A]].complete(value)
            case _ =>
              () // do nothing
          }
        else
          cacheValue(state, value) // retry
      }
    }

    implicit val sc: Scheduler = context.scheduler
    self.state.get match {
      case null =>
        val p = Promise[A]()

        if (!self.state.compareAndSet(null, (p, context.connection))) {
          // $COVERAGE-OFF$
          startMemoization(self, context, cb, nextFrame) // retry
          // $COVERAGE-ON$
        } else {
          val underlying = self.thunk
          val callback = new Callback[A] {
            def onError(ex: Throwable): Unit = {
              cacheValue(self.state, Failure(ex))
              restartAsync(Error(ex), context, cb, null, null, null)
            }

            def onSuccess(value: A): Unit = {
              cacheValue(self.state, Success(value))
              restartAsync(Now(value), context, cb, null, null, null)
            }
          }

          // Asynchronous boundary to prevent stack-overflows!
          sc.execute(new TrampolinedRunnable {
            def run(): Unit = {
              startFull(underlying, context, callback, null, null, null, nextFrame)
            }
          })
          null
        }

      case (p: Promise[_], mainCancelable: StackedCancelable) =>
        // execution is pending completion
        context.connection push mainCancelable
        p.asInstanceOf[Promise[A]].future.onComplete { r =>
          context.connection.pop()
          context.frameRef.reset()
          startFull(Task.fromTry(r), context, cb, null, null, null, 1)
        }
        null

      case ref: Try[A] @unchecked =>
        // Race condition happened
        // $COVERAGE-OFF$
        ref
        // $COVERAGE-ON$
    }
  }
}
