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

import monix.eval.Task
import monix.eval.Task.{Async, Context, Error, Eval, FlatMap, Map, Now, Suspend}
import monix.eval.internal.TaskRunLoop._
import monix.execution.internal.collection.ChunkedArrayStack
import monix.execution.misc.Local
import monix.execution.{Callback, CancelableFuture, Scheduler}

import scala.concurrent.Promise
import scala.util.control.NonFatal

private[eval] object TaskRunToFutureWithLocal {
  /** A run-loop that attempts to complete a `CancelableFuture`
    * synchronously falling back to [[startFull]] and actual
    * asynchronous execution in case of an asynchronous boundary.
    *
    * Function gets invoked by `Task.runToFuture(implicit s: Scheduler)`.
    */
  def startFuture[A](source: Task[A], scheduler: Scheduler, opts: Task.Options): CancelableFuture[A] = {
    var current = source.asInstanceOf[Task[Any]]
    var bFirst: Bind = null
    var bRest: CallStack = null
    // Values from Now, Always and Once are unboxed in this var, for code reuse
    var hasUnboxed: Boolean = false
    var unboxed: AnyRef = null
    // Keeps track of the current frame, used for forced async boundaries
    val em = scheduler.executionModel
    var frameIndex = frameStart(em)

    val prev = Local.getContext()
    val isolated = prev.isolate()

    do {
      if (frameIndex != 0) {
        current match {
          case FlatMap(fa, bindNext) =>
            if (bFirst ne null) {
              if (bRest eq null) bRest = ChunkedArrayStack()
              bRest.push(bFirst)
            }
            /*_*/
            bFirst = bindNext /*_*/
            current = fa

          case Now(value) =>
            unboxed = value.asInstanceOf[AnyRef]
            hasUnboxed = true

          case Eval(thunk) =>
            Local.setContext(isolated)
            try {
              unboxed = thunk().asInstanceOf[AnyRef]
              hasUnboxed = true
              current = null
            } catch {
              case e if NonFatal(e) =>
                current = Error(e)
            }

          case bindNext @ Map(fa, _, _) =>
            if (bFirst ne null) {
              if (bRest eq null) bRest = ChunkedArrayStack()
              bRest.push(bFirst)
            }
            bFirst = bindNext
            current = fa

          case Suspend(thunk) =>
            Local.setContext(isolated)
            // Try/catch described as statement to prevent ObjectRef ;-)
            try {
              current = thunk()
            } catch {
              case ex if NonFatal(ex) => current = Error(ex)
            }

          case Error(error) =>
            findErrorHandler(bFirst, bRest) match {
              case null =>
                return CancelableFuture.failed(error)
              case bind =>
                // Try/catch described as statement to prevent ObjectRef ;-)
                try {
                  current = bind.recover(error)
                } catch { case e if NonFatal(e) => current = Error(e) }
                frameIndex = em.nextFrameIndex(frameIndex)
                bFirst = null
            }

          case async =>
            return goAsync4Future(
              async,
              scheduler,
              opts,
              bFirst,
              bRest,
              frameIndex,
              forceFork = false,
              prev,
              isolated
            )
        }

        if (hasUnboxed) {
          popNextBind(bFirst, bRest) match {
            case null =>
              // Restore Local on the current thread
              Local.setContext(prev)
              return CancelableFuture.successful(unboxed.asInstanceOf[A], isolated)

            case bind =>
              // Try/catch described as statement to prevent ObjectRef ;-)
              try {
                current = bind(unboxed)
              } catch {
                case ex if NonFatal(ex) => current = Error(ex)
              }
              frameIndex = em.nextFrameIndex(frameIndex)
              hasUnboxed = false
              unboxed = null
              bFirst = null
          }
        }
      } else {
        // Force async boundary
        return goAsync4Future(current, scheduler, opts, bFirst, bRest, frameIndex, forceFork = true, prev, isolated)
      }
    } while (true)
    // $COVERAGE-OFF$
    null
    // $COVERAGE-ON$
  }

  /** Called when we hit the first async boundary in [[startFuture]]. */
  private def goAsync4Future[A](
    source: Current,
    scheduler: Scheduler,
    opts: Task.Options,
    bFirst: Bind,
    bRest: CallStack,
    nextFrame: FrameIndex,
    forceFork: Boolean,
    previousCtx: Local.Context,
    isolatedCtx: Local.Context): CancelableFuture[A] = {

    Local.setContext(isolatedCtx)

    val p = Promise[A]()

    val cb = Callback.fromPromise(p).asInstanceOf[Callback[Throwable, Any]]
    val context = Context(scheduler, opts)

    if (!forceFork) source match {
      case async: Async[Any] =>
        executeAsyncTask(async, context, cb, null, bFirst, bRest, 1)
      case _ =>
        startFull(source, context, cb, null, bFirst, bRest, nextFrame)
    }
    else {
      restartAsync(source.asInstanceOf[Task[A]], context, cb, null, bFirst, bRest)
    }

    Local.setContext(previousCtx)

    CancelableFuture(p.future, context.connection.toCancelable(scheduler), isolatedCtx)
  }
}
