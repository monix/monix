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

import cats.effect.CancelToken
import monix.eval.Task
import monix.eval.Task.{Async, Context, ContextSwitch, Error, Eval, FlatMap, Map, Now, Suspend}
import monix.execution.internal.collection.ChunkedArrayStack
import monix.execution.misc.Local
import monix.execution.{Callback, CancelableFuture, ExecutionModel, Scheduler}

import scala.concurrent.Promise
import scala.util.control.NonFatal

private[eval] object TaskRunLoop {
  type Current = Task[Any]
  type Bind = Any => Task[Any]
  type CallStack = ChunkedArrayStack[Bind]

  /** Starts or resumes evaluation of the run-loop from where it left
    * off. This is the complete run-loop.
    *
    * Used for continuing a run-loop after an async boundary
    * happens from [[startFuture]] and [[startLight]].
    */
  def startFull[A](
    source: Task[A],
    contextInit: Context,
    cb: Callback[Throwable, A],
    rcb: TaskRestartCallback,
    bFirst: Bind,
    bRest: CallStack,
    frameIndex: FrameIndex): Unit = {

    val cba = cb.asInstanceOf[Callback[Throwable, Any]]
    var current: Current = source
    var bFirstRef = bFirst
    var bRestRef = bRest
    // Values from Now, Always and Once are unboxed in this var, for code reuse
    var hasUnboxed: Boolean = false
    var unboxed: AnyRef = null
    var currentIndex = frameIndex

    // Can change due to ContextSwitch
    var context = contextInit
    var em = context.scheduler.executionModel

    do {
      if (currentIndex != 0) {
        current match {
          case FlatMap(fa, bindNext) =>
            if (bFirstRef ne null) {
              if (bRestRef eq null) bRestRef = ChunkedArrayStack()
              bRestRef.push(bFirstRef)
            }
            bFirstRef = bindNext.asInstanceOf[Bind]
            current = fa

          case Now(value) =>
            unboxed = value.asInstanceOf[AnyRef]
            hasUnboxed = true

          case Eval(thunk) =>
            try {
              unboxed = thunk().asInstanceOf[AnyRef]
              hasUnboxed = true
              current = null
            } catch {
              case e if NonFatal(e) =>
                current = Error(e)
            }

          case bindNext @ Map(fa, _, _) =>
            if (bFirstRef ne null) {
              if (bRestRef eq null) bRestRef = ChunkedArrayStack()
              bRestRef.push(bFirstRef)
            }
            bFirstRef = bindNext.asInstanceOf[Bind]
            current = fa

          case Suspend(thunk) =>
            // Try/catch described as statement, otherwise ObjectRef happens ;-)
            try {
              current = thunk()
            } catch {
              case ex if NonFatal(ex) => current = Error(ex)
            }

          case Error(error) =>
            findErrorHandler(bFirstRef, bRestRef) match {
              case null =>
                cba.onError(error)
                return
              case bind =>
                // Try/catch described as statement, otherwise ObjectRef happens ;-)
                try {
                  current = bind.recover(error)
                } catch { case e if NonFatal(e) => current = Error(e) }
                currentIndex = em.nextFrameIndex(currentIndex)
                bFirstRef = null
            }

          case async @ Async(_, _, _, _) =>
            executeAsyncTask(async, context, cba, rcb, bFirstRef, bRestRef, currentIndex)
            return

          case ContextSwitch(next, modify, restore) =>
            // Construct for catching errors only from `modify`
            var catchError = true
            try {
              val old = context
              context = modify(context)
              catchError = false
              current = next
              if (context ne old) {
                em = context.scheduler.executionModel
                if (rcb ne null) rcb.contextSwitch(context)
                if (restore ne null) {
                  /*_*/
                  current = FlatMap(next, new RestoreContext(old, restore))
                  /*_*/
                }
              }
              // If LCP has changed to "enable", encapsulate local context
              val useLCP = context.options.localContextPropagation
              if (useLCP && useLCP != old.options.localContextPropagation) {
                Local.isolate {
                  startFull(
                    current,
                    context,
                    cba,
                    rcb,
                    bFirstRef,
                    bRestRef,
                    currentIndex
                  )
                }
                return
              }
            } catch {
              case e if NonFatal(e) && catchError =>
                current = Error(e)
            }
        }

        if (hasUnboxed) {
          popNextBind(bFirstRef, bRestRef) match {
            case null =>
              cba.onSuccess(unboxed)
              return
            case bind =>
              // Try/catch described as statement, otherwise ObjectRef happens ;-)
              try {
                current = bind(unboxed)
              } catch {
                case ex if NonFatal(ex) => current = Error(ex)
              }
              currentIndex = em.nextFrameIndex(currentIndex)
              hasUnboxed = false
              unboxed = null
              bFirstRef = null
          }
        }
      } else {
        // Force async boundary
        restartAsync(current, context, cba, rcb, bFirstRef, bRestRef)
        return
      }
    } while (true)
  }

  /** Internal utility, for forcing an asynchronous boundary in the
    * trampoline loop.
    */
  def restartAsync[A](
    source: Task[A],
    context: Context,
    cb: Callback[Throwable, A],
    rcb: TaskRestartCallback,
    bindCurrent: Bind,
    bindRest: CallStack): Unit = {

    val savedLocals =
      if (context.options.localContextPropagation) Local.getContext()
      else null

    context.scheduler.executeAsync { () =>
      // Checking for the cancellation status after the async boundary;
      // This is consistent with the behavior on `Async` tasks, i.e. check
      // is done *after* the evaluation and *before* signaling the result
      // or continuing the evaluation of the bind chain. It also makes sense
      // because it gives a chance to the caller to cancel and not wait for
      // another forced boundary to have actual cancellation happening.
      if (!context.shouldCancel) {
        // Resetting the frameRef, as a real asynchronous boundary happened
        context.frameRef.reset()

        // Transporting the current context if localContextPropagation == true.
        var prevLocals: Local.Context = null
        if (savedLocals != null) {
          prevLocals = Local.getContext()
          Local.setContext(savedLocals)
        }
        try {
          // Using frameIndex = 1 to ensure at least one cycle gets executed
          startFull(source, context, cb, rcb, bindCurrent, bindRest, 1)
        } finally {
          if (prevLocals != null)
            Local.setContext(prevLocals)
        }
      }
    }
  }

  /** A run-loop that attempts to evaluate a `Task` without
    * initializing a `Task.Context`, falling back to
    * [[startFull]] when the first `Async` boundary is hit.
    *
    * Function gets invoked by `Task.runAsync(cb: Callback)`.
    */
  def startLight[A](
    source: Task[A],
    scheduler: Scheduler,
    opts: Task.Options,
    cb: Callback[Throwable, A],
    isCancelable: Boolean = true): CancelToken[Task] = {

    var current = source.asInstanceOf[Task[Any]]
    var bFirst: Bind = null
    var bRest: CallStack = null
    // Values from Now, Always and Once are unboxed in this var, for code reuse
    var hasUnboxed: Boolean = false
    var unboxed: AnyRef = null
    // Keeps track of the current frame, used for forced async boundaries
    val em = scheduler.executionModel
    var frameIndex = frameStart(em)

    do {
      if (frameIndex != 0) {
        current match {
          case FlatMap(fa, bindNext) =>
            if (bFirst ne null) {
              if (bRest eq null) bRest = ChunkedArrayStack()
              bRest.push(bFirst)
            }
            bFirst = bindNext.asInstanceOf[Bind]
            current = fa

          case Now(value) =>
            unboxed = value.asInstanceOf[AnyRef]
            hasUnboxed = true

          case Eval(thunk) =>
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
            bFirst = bindNext.asInstanceOf[Bind]
            current = fa

          case Suspend(thunk) =>
            // Try/catch described as statement, otherwise ObjectRef happens ;-)
            try {
              current = thunk()
            } catch {
              case ex if NonFatal(ex) =>
                current = Error(ex)
            }

          case Error(error) =>
            findErrorHandler(bFirst, bRest) match {
              case null =>
                cb.onError(error)
                return Task.unit
              case bind =>
                // Try/catch described as statement, otherwise ObjectRef happens ;-)
                try {
                  current = bind.recover(error)
                } catch { case e if NonFatal(e) => current = Error(e) }
                frameIndex = em.nextFrameIndex(frameIndex)
                bFirst = null
            }

          case async =>
            return goAsyncForLightCB(
              async,
              scheduler,
              opts,
              cb.asInstanceOf[Callback[Throwable, Any]],
              bFirst,
              bRest,
              frameIndex,
              forceFork = false,
              isCancelable = isCancelable)
        }

        if (hasUnboxed) {
          popNextBind(bFirst, bRest) match {
            case null =>
              cb.onSuccess(unboxed.asInstanceOf[A])
              return Task.unit
            case bind =>
              // Try/catch described as statement, otherwise ObjectRef happens ;-)
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
        return goAsyncForLightCB(
          current,
          scheduler,
          opts,
          cb.asInstanceOf[Callback[Throwable, Any]],
          bFirst,
          bRest,
          frameIndex,
          forceFork = true,
          isCancelable = true)
      }
    } while (true)
    // $COVERAGE-OFF$
    null
    // $COVERAGE-ON$
  }

  /** A run-loop version that evaluates the given task until the
    * first async boundary or until completion.
    */
  def startStep[A](source: Task[A], scheduler: Scheduler, opts: Task.Options): Either[Task[A], A] = {
    var current = source.asInstanceOf[Task[Any]]
    var bFirst: Bind = null
    var bRest: CallStack = null
    // Values from Now, Always and Once are unboxed in this var, for code reuse
    var hasUnboxed: Boolean = false
    var unboxed: AnyRef = null
    // Keeps track of the current frame, used for forced async boundaries
    val em = scheduler.executionModel
    var frameIndex = frameStart(em)

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
            // Try/catch described as statement to prevent ObjectRef ;-)
            try {
              current = thunk()
            } catch {
              case ex if NonFatal(ex) => current = Error(ex)
            }

          case Error(error) =>
            findErrorHandler(bFirst, bRest) match {
              case null => throw error
              case bind =>
                // Try/catch described as statement to prevent ObjectRef ;-)
                try {
                  current = bind.recover(error)
                } catch { case e if NonFatal(e) => current = Error(e) }
                frameIndex = em.nextFrameIndex(frameIndex)
                bFirst = null
            }

          case async =>
            return goAsync4Step(async, scheduler, opts, bFirst, bRest, frameIndex, forceFork = false)
        }

        if (hasUnboxed) {
          popNextBind(bFirst, bRest) match {
            case null =>
              return Right(unboxed.asInstanceOf[A])
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
        return goAsync4Step(current, scheduler, opts, bFirst, bRest, frameIndex, forceFork = true)
      }
    } while (true)
    // $COVERAGE-OFF$
    null
    // $COVERAGE-ON$
  }

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
              forceFork = false
            )
        }

        if (hasUnboxed) {
          popNextBind(bFirst, bRest) match {
            case null =>
              return CancelableFuture.successful(unboxed.asInstanceOf[A])

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
        return goAsync4Future(current, scheduler, opts, bFirst, bRest, frameIndex, forceFork = true)
      }
    } while (true)
    // $COVERAGE-OFF$
    null
    // $COVERAGE-ON$
  }

  private[internal] def executeAsyncTask(
    task: Task.Async[Any],
    context: Context,
    cb: Callback[Throwable, Any],
    rcb: TaskRestartCallback,
    bFirst: Bind,
    bRest: CallStack,
    nextFrame: FrameIndex): Unit = {

    // We are going to resume the frame index from where we left,
    // but only if no real asynchronous execution happened. So in order
    // to detect asynchronous execution, we are reading a thread-local
    // variable that's going to be reset in case of a thread jump.
    // Obviously this doesn't work for Javascript or for single-threaded
    // thread-pools, but that's OK, as it only means that in such instances
    // we can experience more async boundaries and everything is fine for
    // as long as the implementation of `Async` tasks are triggering
    // a `frameRef.reset` on async boundaries.
    context.frameRef := nextFrame

    // rcb reference might be null, so initializing
    val restartCallback = if (rcb != null) rcb else TaskRestartCallback(context, cb)
    restartCallback.start(task, bFirst, bRest)
  }

  /** Called when we hit the first async boundary in
    * [[startLight]].
    */
  private def goAsyncForLightCB(
    source: Current,
    scheduler: Scheduler,
    opts: Task.Options,
    cb: Callback[Throwable, Any],
    bFirst: Bind,
    bRest: CallStack,
    nextFrame: FrameIndex,
    isCancelable: Boolean,
    forceFork: Boolean): CancelToken[Task] = {

    val context = Context(
      scheduler,
      opts,
      if (isCancelable) TaskConnection()
      else TaskConnection.uncancelable)

    if (!forceFork) source match {
      case async: Async[Any] =>
        executeAsyncTask(async, context, cb, null, bFirst, bRest, 1)
      case _ =>
        startFull(source, context, cb, null, bFirst, bRest, nextFrame)
    }
    else {
      restartAsync(source, context, cb, null, bFirst, bRest)
    }
    context.connection.cancel
  }

  /** Called when we hit the first async boundary in [[startFuture]]. */
  private def goAsync4Future[A](
    source: Current,
    scheduler: Scheduler,
    opts: Task.Options,
    bFirst: Bind,
    bRest: CallStack,
    nextFrame: FrameIndex,
    forceFork: Boolean): CancelableFuture[A] = {

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

    CancelableFuture(p.future, context.connection.toCancelable(scheduler))
  }

  /** Called when we hit the first async boundary in [[startStep]]. */
  private def goAsync4Step[A](
    source: Current,
    scheduler: Scheduler,
    opts: Task.Options,
    bFirst: Bind,
    bRest: CallStack,
    nextFrame: FrameIndex,
    forceFork: Boolean): Either[Task[A], A] = {

    val ctx = Context(scheduler, opts)
    val start: Start[Any] =
      if (!forceFork) {
        ctx.frameRef := nextFrame
        (ctx, cb) => startFull(source, ctx, cb, null, bFirst, bRest, ctx.frameRef())
      } else { (ctx, cb) =>
        ctx.scheduler.executeAsync(() => startFull(source, ctx, cb, null, bFirst, bRest, 1))
      }

    Left(
      Async(
        start.asInstanceOf[Start[A]],
        trampolineBefore = false,
        trampolineAfter = false
      ))
  }

  private[internal] def findErrorHandler(bFirst: Bind, bRest: CallStack): StackFrame[Any, Task[Any]] = {
    bFirst match {
      case ref: StackFrame[Any, Task[Any]] @unchecked => ref
      case _ =>
        if (bRest eq null) null
        else {
          do {
            val ref = bRest.pop()
            if (ref eq null)
              return null
            else if (ref.isInstanceOf[StackFrame[_, _]])
              return ref.asInstanceOf[StackFrame[Any, Task[Any]]]
          } while (true)
          // $COVERAGE-OFF$
          null
          // $COVERAGE-ON$
        }
    }
  }

  private[internal] def popNextBind(bFirst: Bind, bRest: CallStack): Bind = {
    if ((bFirst ne null) && !bFirst.isInstanceOf[StackFrame.ErrorHandler[_, _]])
      return bFirst

    if (bRest eq null) return null
    do {
      val next = bRest.pop()
      if (next eq null) {
        return null
      } else if (!next.isInstanceOf[StackFrame.ErrorHandler[_, _]]) {
        return next
      }
    } while (true)
    // $COVERAGE-OFF$
    null
    // $COVERAGE-ON$
  }

  private[internal] def frameStart(em: ExecutionModel): FrameIndex =
    em.nextFrameIndex(0)

  private final class RestoreContext(old: Context, restore: (Any, Throwable, Context, Context) => Context)
    extends StackFrame[Any, Task[Any]] {

    def apply(a: Any): Task[Any] =
      ContextSwitch(Now(a), current => restore(a, null, old, current), null)
    def recover(e: Throwable): Task[Any] =
      ContextSwitch(Error(e), current => restore(null, e, old, current), null)
  }
}
