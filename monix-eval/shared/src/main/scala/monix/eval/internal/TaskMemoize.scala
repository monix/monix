/*
 * Copyright (c) 2014-2020 by The Monix Project Developers.
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

import monix.eval.Task.{Context, Error, Now}
import monix.eval.internal.TaskRunLoop.startFull
import monix.execution.Callback
import monix.eval.{Coeval, Task}
import monix.execution.Scheduler
import monix.execution.atomic.Atomic
import scala.annotation.tailrec
import scala.concurrent.{ExecutionContext, Promise}
import scala.util.{Failure, Success, Try}

private[eval] object TaskMemoize {
  /**
    * Implementation for `.memoize` and `.memoizeOnSuccess`.
    */
  def apply[A](source: Task[A], cacheErrors: Boolean): Task[A] =
    source match {
      case Now(_) | Error(_) =>
        source
      case Task.Eval(Coeval.Suspend(f: LazyVal[A @unchecked])) if !cacheErrors || f.cacheErrors =>
        source
      case Task.Async(r: Register[A] @unchecked, _, _, _, _) if !cacheErrors || r.cacheErrors =>
        source
      case _ =>
        Task.Async(
          new Register(source, cacheErrors),
          trampolineBefore = false,
          trampolineAfter = true,
          restoreLocals = true)
    }

  /** Registration function, used in `Task.Async`. */
  private final class Register[A](source: Task[A], val cacheErrors: Boolean)
    extends ((Task.Context, Callback[Throwable, A]) => Unit) { self =>

    // N.B. keeps state!
    private[this] var thunk = source
    private[this] val state = Atomic(null: AnyRef)

    def apply(ctx: Context, cb: Callback[Throwable, A]): Unit =
      state.get() match {
        case result: Try[A] @unchecked =>
          cb(result)
        case _ =>
          start(ctx, cb)
      }

    /** Saves the final result on completion and triggers the registered
      * listeners.
      */
    @tailrec def cacheValue(value: Try[A])(implicit s: Scheduler): Unit = {
      // Should we cache everything, error results as well,
      // or only successful results?
      if (self.cacheErrors || value.isSuccess) {
        state.getAndSet(value) match {
          case p: Promise[A] @unchecked =>
            if (!p.tryComplete(value)) {
              // $COVERAGE-OFF$
              if (value.isFailure)
                s.reportFailure(value.failed.get)
              // $COVERAGE-ON$
            }
          case _ =>
            () // do nothing
        }
        // GC purposes
        self.thunk = null
      } else {
        // Error happened and we are not caching errors!
        state.get() match {
          case p: Promise[A] @unchecked =>
            // Resetting the state to `null` will trigger the
            // execution again on next `runAsync`
            if (state.compareAndSet(p, null)) {
              p.tryComplete(value)
              ()
            } else {
              // Race condition, retry
              // $COVERAGE-OFF$
              cacheValue(value)
              // $COVERAGE-ON$
            }
          case _ =>
            // $COVERAGE-OFF$
            () // Do nothing, as value is probably null already
          // $COVERAGE-ON$
        }
      }
    }

    /** Builds a callback that gets used to cache the result. */
    private def complete(implicit s: Scheduler): Callback[Throwable, A] =
      new Callback[Throwable, A] {
        def onSuccess(value: A): Unit =
          self.cacheValue(Success(value))
        def onError(ex: Throwable): Unit =
          self.cacheValue(Failure(ex))
      }

    /** While the task is pending completion, registers a new listener
      * that will receive the result once the task is complete.
      */
    private def registerListener(p: Promise[A], context: Context, cb: Callback[Throwable, A])(
      implicit ec: ExecutionContext): Unit = {

      p.future.onComplete { r =>
        // Listener is cancelable: we simply ensure that the result isn't streamed
        if (!context.connection.isCanceled) {
          context.frameRef.reset()
          startFull(Task.fromTry(r), context, cb, null, null, null, 1)
        }
      }
    }

    /**
      * Starts execution, eventually caching the value on completion.
      */
    @tailrec private def start(context: Context, cb: Callback[Throwable, A]): Unit = {
      implicit val sc: Scheduler = context.scheduler
      self.state.get() match {
        case null =>
          val update = Promise[A]()

          if (!self.state.compareAndSet(null, update)) {
            // $COVERAGE-OFF$
            start(context, cb) // retry
            // $COVERAGE-ON$
          } else {
            // Registering listener callback for when listener is ready
            self.registerListener(update, context, cb)

            // Running main task in `uncancelable` model
            val ctx2 = context
              .withOptions(context.options.disableAutoCancelableRunLoops)
              .withConnection(TaskConnection.uncancelable)

            // Start with light async boundary to prevent stack-overflows!
            Task.unsafeStartTrampolined(self.thunk, ctx2, self.complete)
          }

        case ref: Promise[A] @unchecked =>
          self.registerListener(ref, context, cb)

        case ref: Try[A] @unchecked =>
          // Race condition happened
          // $COVERAGE-OFF$
          cb(ref)
        // $COVERAGE-ON$
      }
    }
  }
}
