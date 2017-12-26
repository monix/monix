/*
 * Copyright (c) 2014-2017 by The Monix Project Developers.
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

import monix.eval.Task.{Async, Context, Error, Eval, FlatMap, FrameIndex, Map, MemoizeSuspend, Now, Suspend, fromTry}
import monix.eval.{Callback, Task}
import monix.execution.atomic.AtomicAny
import monix.execution.cancelables.StackedCancelable
import monix.execution.internal.collection.ArrayStack
import monix.execution.misc.{Local, NonFatal}
import monix.execution.schedulers.TrampolinedRunnable
import monix.execution.{Cancelable, CancelableFuture, ExecutionModel, Scheduler}
import scala.concurrent.Promise
import scala.util.{Failure, Success, Try}

private[eval] object TaskRunLoop {
  private type Current = Task[Any]
  private type Bind = Any => Task[Any]
  private type CallStack = ArrayStack[Bind]

  // We always start from 1
  final def frameStart(em: ExecutionModel): FrameIndex =
    em.nextFrameIndex(0)

  /** Creates a new [[CallStack]] */
  private def createCallStack(): CallStack =
    ArrayStack(8)

  /** Internal utility, for forcing an asynchronous boundary in the
    * trampoline loop.
    */
  def restartAsync[A](
    source: Task[A],
    context: Context,
    cb: Callback[A],
    bindCurrent: Bind,
    bindRest: CallStack): Unit = {

    val savedLocals =
      if (context.options.localContextPropagation) Local.getContext()
      else null

    if (!context.shouldCancel) {
      context.scheduler.executeAsync { () =>
        // Resetting the frameRef, as a real asynchronous boundary happened
        context.frameRef.reset()
        // Transporting the current context if localContextPropagation == true.
        Local.bind(savedLocals) {
          // Using frameIndex = 1 to ensure at least one cycle gets executed
          startWithCallback(source, context, cb, bindCurrent, bindRest, 1)
        }
      }
    }
  }

  /** Starts or resumes evaluation of the run-loop from where it left
    * off. This is the complete run-loop.
    *
    * Used for continuing a run-loop after an async boundary
    * happens from [[startAsFuture]] and [[startLightWithCallback]].
    */
  def startWithCallback[A](
    source: Task[A],
    context: Context,
    cb: Callback[A],
    bFirst: Bind,
    bRest: CallStack,
    frameIndex: FrameIndex): Unit = {

    final class RestartCallback(context: Context, callback: Callback[Any]) extends Callback[Any] {
      private[this] var canCall = false
      private[this] var bFirst: Bind = _
      private[this] var bRest: CallStack = _
      private[this] val runLoopIndex = context.frameRef
      private[this] val withLocal = context.options.localContextPropagation
      private[this] var savedLocals: Local.Context = _

      def prepare(bindCurrent: Bind, bindRest: CallStack): Unit = {
        canCall = true
        this.bFirst = bindCurrent
        this.bRest = bindRest
        if (withLocal)
          savedLocals = Local.getContext()
      }

      def onSuccess(value: Any): Unit =
        if (canCall) {
          canCall = false
          Local.bind(savedLocals) {
            loop(Now(value), context, callback, this, bFirst, bRest, runLoopIndex())
          }
        }

      def onError(ex: Throwable): Unit = {
        if (canCall) {
          canCall = false
          Local.bind(savedLocals) {
            loop(Error(ex), context, callback, this, bFirst, bRest, runLoopIndex())
          }
        } else {
          // $COVERAGE-OFF$
          context.scheduler.reportFailure(ex)
          // $COVERAGE-ON$
        }
      }
    }

    def executeOnFinish(
      context: Context,
      cb: Callback[Any],
      rcb: RestartCallback,
      bFirst: Bind,
      bRest: CallStack,
      register: (Context, Callback[Any]) => Unit,
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
        context.frameRef := nextFrame

        // rcb reference might be null, so initializing
        val restartCallback = if (rcb != null) rcb else new RestartCallback(context, cb)
        restartCallback.prepare(bFirst, bRest)
        register(context, restartCallback)
      }
    }

    def loop(
      start: Current,
      context: Context,
      cb: Callback[Any],
      rcb: RestartCallback,
      bFirstInit: Bind,
      bRestInit: CallStack,
      frameIndexInit: FrameIndex): Unit = {

      val em = context.executionModel
      var current = start
      var bFirst = bFirstInit
      var bRest = bRestInit
      // Values from Now, Always and Once are unboxed in this var, for code reuse
      var hasUnboxed: Boolean = false
      var unboxed: AnyRef = null
      var frameIndex = frameIndexInit

      do {
        if (frameIndex != 0) {
          current match {
            case FlatMap(fa, bindNext) =>
              if (bFirst ne null) {
                if (bRest eq null) bRest = createCallStack()
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
              } catch { case NonFatal(e) =>
                current = Error(e)
              }

            case bindNext @ Map(fa, _, _) =>
              if (bFirst ne null) {
                if (bRest eq null) bRest = createCallStack()
                bRest.push(bFirst)
              }
              bFirst = bindNext.asInstanceOf[Bind]
              current = fa

            case Suspend(thunk) =>
              // Try/catch described as statement, otherwise ObjectRef happens ;-)
              try { current = thunk() }
              catch { case NonFatal(ex) => current = Error(ex) }

            case Error(error) =>
              findErrorHandler(bFirst, bRest) match {
                case null =>
                  cb.onError(error)
                  return
                case bind =>
                  // Try/catch described as statement, otherwise ObjectRef happens ;-)
                  try { current = bind.recover(error) }
                  catch { case NonFatal(e) => current = Error(e) }
                  frameIndex = em.nextFrameIndex(frameIndex)
                  bFirst = null
              }

            case Async(onFinish) =>
              executeOnFinish(context, cb, rcb, bFirst, bRest, onFinish, frameIndex)
              return

            case ref: MemoizeSuspend[_] =>
              // Already processed?
              ref.value match {
                case Some(materialized) =>
                  materialized match {
                    case Success(value) =>
                      unboxed = value.asInstanceOf[AnyRef]
                      hasUnboxed = true
                      current = null
                    case Failure(error) =>
                      current = Error(error)
                  }
                case None =>
                  val anyRef = ref.asInstanceOf[MemoizeSuspend[Any]]
                  val isSuccess = startMemoization(anyRef, context, cb, bFirst, bRest, frameIndex)
                  if (isSuccess) return
                  current = ref
              }
          }

          if (hasUnboxed) {
            popNextBind(bFirst, bRest) match {
              case null =>
                cb.onSuccess(unboxed)
                return
              case bind =>
                // Try/catch described as statement, otherwise ObjectRef happens ;-)
                try { current = bind(unboxed) }
                catch { case NonFatal(ex) => current = Error(ex) }
                frameIndex = em.nextFrameIndex(frameIndex)
                hasUnboxed = false
                unboxed = null
                bFirst = null
            }
          }
        }
        else {
          // Force async boundary
          restartAsync(current, context, cb, bFirst, bRest)
          return
        }
      } while (true)
    }

    // Can happen to receive a `RestartCallback` (e.g. from Task.fork),
    // in which case we should unwrap it
    val cba = cb.asInstanceOf[Callback[Any]]
    loop(source, context, cba, null, bFirst, bRest, frameIndex)
  }

  /** A run-loop that attempts to evaluate a `Task` without
    * initializing a `Task.Context`, falling back to
    * [[startWithCallback]] when the first `Async` boundary is hit.
    *
    * Function gets invoked by `Task.runAsync(cb: Callback)`.
    */
  def startLightWithCallback[A](
    source: Task[A],
    scheduler: Scheduler,
    opts: Task.Options,
    cb: Callback[A]): Cancelable = {

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
              if (bRest eq null) bRest = createCallStack()
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
            } catch { case NonFatal(e) =>
              current = Error(e)
            }

          case bindNext @ Map(fa, _, _) =>
            if (bFirst ne null) {
              if (bRest eq null) bRest = createCallStack()
              bRest.push(bFirst)
            }
            bFirst = bindNext.asInstanceOf[Bind]
            current = fa

          case Suspend(thunk) =>
            // Try/catch described as statement, otherwise ObjectRef happens ;-)
            try { current = thunk() }
            catch { case NonFatal(ex) => current = Error(ex) }

          case Error(error) =>
            findErrorHandler(bFirst, bRest) match {
              case null =>
                cb.onError(error)
                return Cancelable.empty
              case bind =>
                // Try/catch described as statement, otherwise ObjectRef happens ;-)
                try { current = bind.recover(error) }
                catch { case NonFatal(e) => current = Error(e) }
                frameIndex = em.nextFrameIndex(frameIndex)
                bFirst = null
            }

          case Async(_) =>
            return goAsyncForLightCB(
              current, scheduler, opts,
              cb.asInstanceOf[Callback[Any]],
              bFirst, bRest, frameIndex,
              forceAsync = false)

          case ref: MemoizeSuspend[_] =>
            // Already processed?
            ref.value match {
              case Some(materialized) =>
                materialized match {
                  case Success(value) =>
                    unboxed = value.asInstanceOf[AnyRef]
                    hasUnboxed = true
                    current = null
                  case Failure(error) =>
                    current = Error(error)
                }
              case None =>
                return goAsyncForLightCB(
                  current, scheduler, opts,
                  cb.asInstanceOf[Callback[Any]],
                  bFirst, bRest, frameIndex,
                  forceAsync = false)
            }
        }

        if (hasUnboxed) {
          popNextBind(bFirst, bRest) match {
            case null =>
              cb.onSuccess(unboxed.asInstanceOf[A])
              return Cancelable.empty
            case bind =>
              // Try/catch described as statement, otherwise ObjectRef happens ;-)
              try { current = bind(unboxed) }
              catch { case NonFatal(ex) => current = Error(ex) }
              frameIndex = em.nextFrameIndex(frameIndex)
              hasUnboxed = false
              unboxed = null
              bFirst = null
          }
        }
      }
      else {
        // Force async boundary
        return goAsyncForLightCB(
          current, scheduler, opts,
          cb.asInstanceOf[Callback[Any]],
          bFirst, bRest, frameIndex,
          forceAsync = true)
      }
    } while (true)
    // $COVERAGE-OFF$
    null
    // $COVERAGE-ON$
  }

  /** A run-loop that attempts to complete a `CancelableFuture`
    * synchronously falling back to [[startWithCallback]] and actual
    * asynchronous execution in case of an asynchronous boundary.
    *
    * Function gets invoked by `Task.runAsync(implicit s: Scheduler)`.
    */
  def startAsFuture[A](source: Task[A], scheduler: Scheduler, opts: Task.Options): CancelableFuture[A] = {
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
              if (bRest eq null) bRest = createCallStack()
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
              case NonFatal(e) =>
                current = Error(e)
            }

          case bindNext@Map(fa, _, _) =>
            if (bFirst ne null) {
              if (bRest eq null) bRest = createCallStack()
              bRest.push(bFirst)
            }
            bFirst = bindNext.asInstanceOf[Bind]
            current = fa

          case Suspend(thunk) =>
            // Try/catch described as statement to prevent ObjectRef ;-)
            try {
              current = thunk()
            }
            catch {
              case NonFatal(ex) => current = Error(ex)
            }

          case Error(error) =>
            findErrorHandler(bFirst, bRest) match {
              case null =>
                return CancelableFuture.failed(error)
              case bind =>
                // Try/catch described as statement to prevent ObjectRef ;-)
                try { current = bind.recover(error) }
                catch { case NonFatal(e) => current = Error(e) }
                frameIndex = em.nextFrameIndex(frameIndex)
                bFirst = null
            }

          case Async(_) =>
            return goAsync4Future(current, scheduler, opts, bFirst, bRest, frameIndex, forceAsync = false)

          case ref: MemoizeSuspend[_] =>
            // Already processed?
            ref.value match {
              case Some(materialized) =>
                materialized match {
                  case Success(value) =>
                    unboxed = value.asInstanceOf[AnyRef]
                    hasUnboxed = true
                    current = null
                  case Failure(error) =>
                    current = Error(error)
                }
              case None =>
                return goAsync4Future(current, scheduler, opts, bFirst, bRest, frameIndex, forceAsync = false)
            }
        }

        if (hasUnboxed) {
          popNextBind(bFirst, bRest) match {
            case null =>
              return CancelableFuture.successful(unboxed.asInstanceOf[A])
            case bind =>
              // Try/catch described as statement to prevent ObjectRef ;-)
              try {
                current = bind(unboxed)
              }
              catch {
                case NonFatal(ex) => current = Error(ex)
              }
              frameIndex = em.nextFrameIndex(frameIndex)
              hasUnboxed = false
              unboxed = null
              bFirst = null
          }
        }
      } else {
        // Force async boundary
        return goAsync4Future(current, scheduler, opts, bFirst, bRest, frameIndex, forceAsync = true)
      }
    } while (true)
    // $COVERAGE-OFF$
    null
    // $COVERAGE-ON$
  }

  /** Called when we hit the first async boundary in
    * [[startLightWithCallback]].
    */
  private def goAsyncForLightCB(
    source: Current,
    scheduler: Scheduler,
    opts: Task.Options,
    cb: Callback[Any],
    bindCurrent: Bind,
    bindRest: CallStack,
    nextFrame: FrameIndex,
    forceAsync: Boolean): Cancelable = {

    val context = Context(scheduler, opts)
    if (forceAsync)
      restartAsync(source, context, cb, bindCurrent, bindRest)
    else
      startWithCallback(source, context, cb, bindCurrent, bindRest, nextFrame)

    context.connection
  }

  /** Called when we hit the first async boundary in [[startAsFuture]]. */
  private def goAsync4Future[A](
    source: Current,
    scheduler: Scheduler,
    opts: Task.Options,
    bindCurrent: Bind,
    bindRest: CallStack,
    nextFrame: FrameIndex,
    forceAsync: Boolean): CancelableFuture[A] = {

    val p = Promise[A]()
    val cb = Callback.fromPromise(p)
    val fa = source.asInstanceOf[Task[A]]
    val context = Context(scheduler, opts)
    if (forceAsync)
      restartAsync(fa, context, cb, bindCurrent, bindRest)
    else
      startWithCallback(fa, context, cb, bindCurrent, bindRest, nextFrame)

    CancelableFuture(p.future, context.connection)
  }

  /** Starts the execution and memoization of a `Task.MemoizeSuspend` state. */
  private def startMemoization[A](
    self: MemoizeSuspend[A],
    context: Context,
    cb: Callback[A],
    bindCurrent: Bind,
    bindRest: CallStack,
    nextFrame: FrameIndex): Boolean = {

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

    implicit val s: Scheduler = context.scheduler

    self.state.get match {
      case null =>
        val p = Promise[A]()

        if (!self.state.compareAndSet(null, (p, context.connection))) {
          // $COVERAGE-OFF$
          startMemoization(self, context, cb, bindCurrent, bindRest, nextFrame) // retry
          // $COVERAGE-ON$
        } else {
          val underlying = try self.thunk() catch { case NonFatal(ex) => Error(ex) }
          val callback = new Callback[A] {
            def onError(ex: Throwable): Unit = {
              cacheValue(self.state, Failure(ex))
              restartAsync(Error(ex), context, cb, bindCurrent, bindRest)
            }

            def onSuccess(value: A): Unit = {
              cacheValue(self.state, Success(value))
              restartAsync(Now(value), context, cb, bindCurrent, bindRest)
            }
          }

          // Asynchronous boundary to prevent stack-overflows!
          s.execute(new TrampolinedRunnable {
            def run(): Unit = {
              startWithCallback(underlying, context, callback, null, null, nextFrame)
            }
          })
          true
        }

      case (p: Promise[_], mainCancelable: StackedCancelable) =>
        // execution is pending completion
        context.connection push mainCancelable
        p.asInstanceOf[Promise[A]].future.onComplete { r =>
          context.connection.pop()
          context.frameRef.reset()
          startWithCallback(fromTry(r), context, cb, bindCurrent, bindRest, 1)
        }
        true

      case _: Try[_] =>
        // Race condition happened
        // $COVERAGE-OFF$
        false
        // $COVERAGE-ON$
    }
  }

  private def findErrorHandler(bFirst: Bind, bRest: CallStack): StackFrame[Any, Task[Any]] = {
    var result: StackFrame[Any, Task[Any]] = null
    var cursor = bFirst
    var continue = true

    while (continue) {
      if (cursor != null && cursor.isInstanceOf[StackFrame[_, _]]) {
        result = cursor.asInstanceOf[StackFrame[Any, Task[Any]]]
        continue = false
      } else {
        cursor = if (bRest ne null) bRest.pop() else null
        continue = cursor != null
      }
    }
    result
  }

  private def popNextBind(bFirst: Bind, bRest: CallStack): Bind = {
    if ((bFirst ne null) && !bFirst.isInstanceOf[StackFrame.ErrorHandler[_, _]])
      return bFirst

    if (bRest eq null) return null
    do {
      bRest.pop() match {
        case null => return null
        case _: StackFrame.ErrorHandler[_, _] => // next please
        case ref => return ref
      }
    } while (true)
    // $COVERAGE-OFF$
    null
    // $COVERAGE-ON$
  }
}
