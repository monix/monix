package monix.eval.internal

import monix.eval.{Callback, Task}
import monix.eval.Task.{Async, Context, Error, Eval, FlatMap, FrameIndex, MemoizeSuspend, Now, OnFinish, Suspend, fromTry}
import monix.execution.{CancelableFuture, ExecutionModel, Scheduler}
import monix.execution.atomic.AtomicAny
import monix.execution.cancelables.StackedCancelable
import monix.execution.internal.collection.ArrayStack
import monix.execution.misc.{LocalContext, NonFatal}

import scala.annotation.tailrec
import scala.concurrent.Promise
import scala.util.{Failure, Success, Try}


private[eval] object TaskTracedRunLoop {
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
                       bindRest: CallStack,
                       local: LocalContext.Context): Unit = {
    val oldCtx = LocalContext.getContext()
    if (!context.shouldCancel)
      context.scheduler.executeAsync { () =>
        // Resetting the frameRef, as a real asynchronous boundary happened
        context.frameRef.reset()
        startWithCallback(source, context, cb, bindCurrent, bindRest, 1, local)
      }
  }

  /** Logic for finding the next `Transformation` reference,
    * meant for handling errors in the run-loop.
    */
  private def findErrorHandler(bFirst: Bind, bRest: CallStack): Transformation[Any, Task[Any]] = {
    var result: Transformation[Any, Task[Any]] = null
    var cursor = bFirst
    var continue = true

    while (continue) {
      if (cursor != null && cursor.isInstanceOf[Transformation[_, _]]) {
        result = cursor.asInstanceOf[Transformation[Any, Task[Any]]]
        continue = false
      } else {
        cursor = if (bRest ne null) bRest.pop() else null
        continue = cursor != null
      }
    }
    result
  }


  private def popNextBind(bFirst: Bind, bRest: CallStack): Bind = {
    if ((bFirst ne null) && !bFirst.isInstanceOf[Transformation.OnError[_]])
      bFirst
    else if (bRest ne null) {
      var cursor: Bind = null
      do { cursor = bRest.pop() }
      while (cursor != null && cursor.isInstanceOf[Transformation.OnError[_]])
      cursor
    } else {
      null
    }
  }

  /** Internal utility, starts or resumes evaluation of
    * the run-loop from where it left off.
    *
    * The `frameIndex=1` default value ensures that the
    * first cycle of the trampoline gets executed regardless of
    * the `ExecutionModel`.
    */
  def startWithCallback[A](
                            source: Task[A],
                            context: Context,
                            cb: Callback[A],
                            bindCurrent: Bind,
                            bindRest: CallStack,
                            frameIndex: FrameIndex,
                            local: LocalContext.Context): Unit = {

    final class RestartCallback(context: Context, callback: Callback[Any]) extends Callback[Any] {
      private[this] var canCall = false
      private[this] var bFirst: Bind = _
      private[this] var bRest: CallStack = _
      private[this] val runLoopIndex = context.frameRef

      def prepare(bindCurrent: Bind, bindRest: CallStack): Unit = {
        canCall = true
        this.bFirst = bindCurrent
        this.bRest = bindRest
      }

      def onSuccess(value: Any): Unit =
        if (canCall) {
          canCall = false
          loop(Now(value), context.executionModel, callback, this, bFirst, bRest, runLoopIndex())
        }

      def onError(ex: Throwable): Unit =
        if (canCall) {
          canCall = false
          loop(Error(ex), context.executionModel, callback, this, bFirst, bRest, runLoopIndex())
        } else {
          context.scheduler.reportFailure(ex)
        }

      override def toString(): String =
        s"RestartCallback($context, $callback)@${hashCode()}"
    }

    def executeOnFinish(
                         em: ExecutionModel,
                         cb: Callback[Any],
                         rcb: RestartCallback,
                         bFirst: Bind,
                         bRest: CallStack,
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
        context.frameRef := nextFrame

        // rcb reference might be null, so initializing
        val restartCallback = if (rcb != null) rcb else new RestartCallback(context, cb)
        LocalContext.withContext(local) {
          restartCallback.prepare(bFirst, bRest)
          onFinish(context, restartCallback)
        }

      }
    }

    @tailrec def loop(
                       source: Current,
                       em: ExecutionModel,
                       cb: Callback[Any],
                       rcb: RestartCallback,
                       bFirst: Bind,
                       bRest: CallStack,
                       frameIndex: FrameIndex): Unit = {

      if (frameIndex != 0) source match {
        case ref @ FlatMap(fa, _, _) =>
          var callStack: CallStack = bRest
          val bindNext = ref.bind()
          if (bFirst ne null) {
            if (callStack eq null) callStack = createCallStack()
            callStack.push(bFirst)
          }
          // Next iteration please
          loop(fa, em, cb, rcb, bindNext, callStack, frameIndex)

        case Now(value) =>
          popNextBind(bFirst, bRest) match {
            case null => cb.onSuccess(value)
            case bind =>
              val fa = try bind(value) catch { case NonFatal(ex) => Error(ex) }
              // Given a flatMap evaluation just happened, must increment the index
              val nextFrame = em.nextFrameIndex(frameIndex)
              // Next iteration please
              loop(fa, em, cb, rcb, null, bRest, nextFrame)
          }

        case Eval(thunk) =>
          var streamErrors = true
          var nextState: Current = null
          try {
            val value = thunk()
            streamErrors = false

            popNextBind(bFirst, bRest) match {
              case null => cb.onSuccess(value)
              case bind =>
                nextState = try bind(value) catch { case NonFatal(ex) => Error(ex) }
            }
          } catch {
            case NonFatal(ex) if streamErrors =>
              nextState = Error(ex)
          }

          if (nextState ne null) {
            // Given a flatMap evaluation just happened, must increment the index
            val nextFrame = em.nextFrameIndex(frameIndex)
            // Next iteration please
            val nextBFirst = if (streamErrors) bFirst else null
            loop(nextState, em, cb, rcb, nextBFirst, bRest, nextFrame)
          }

        case Suspend(thunk) =>
          // Next iteration please
          val fa = try thunk() catch { case NonFatal(ex) => Error(ex) }
          loop(fa, em, cb, rcb, bFirst, bRest, frameIndex)

        case Error(ex) =>
          findErrorHandler(bFirst, bRest) match {
            case null => cb.onError(ex)
            case bind =>
              val fa = try bind.error(ex) catch { case NonFatal(e) => Error(e) }
              // Given a flatMap evaluation just happened, must increment the index
              val nextFrame = em.nextFrameIndex(frameIndex)
              // Next cycle please
              loop(fa, em, cb, rcb, null, bRest, nextFrame)
          }

        case Async(onFinish) =>
          executeOnFinish(em, cb, rcb, bFirst, bRest, onFinish, frameIndex)

        case ref: MemoizeSuspend[_] =>
          // Already processed?
          ref.value match {
            case Some(materialized) =>
              // Next iteration please
              loop(fromTry(materialized), em, cb, rcb, bFirst, bRest, frameIndex)

            case None =>
              val anyRef = ref.asInstanceOf[MemoizeSuspend[Any]]
              val isSuccess = startMemoization(anyRef, context, cb, bFirst, bRest, frameIndex, local)
              // Next iteration please
              if (!isSuccess) loop(ref, em, cb, rcb, bFirst, bRest, frameIndex)
          }
      }
      else {
        // Force async boundary
        restartAsync(source, context, cb, bFirst, bRest, local)
      }
    }

    // Can happen to receive a `RestartCallback` (e.g. from Task.fork),
    // in which case we should unwrap it
    val callback = cb.asInstanceOf[Callback[Any]]
    loop(source, context.executionModel, callback, null, bindCurrent, bindRest, frameIndex)
  }

  /*def startLightWithCallback[A](source: Task[A], scheduler: Scheduler, cb: Callback[A]): Cancelable = {
    /* Called when we hit the first async boundary. */
    def goAsync(
                 source: Current,
                 bindCurrent: Bind,
                 bindRest: CallStack,
                 nextFrame: FrameIndex,
                 forceAsync: Boolean): Cancelable = {

      val context = Context(scheduler)
      val cba = cb.asInstanceOf[Callback[Any]]
      if (forceAsync)
        restartAsync(source, context, cba, bindCurrent, bindRest)
      else
        startWithCallback(source, context, cba, bindCurrent, bindRest, nextFrame)

      context.connection
    }

    /* Loop that evaluates a Task until the first async boundary is hit,
     * or until the evaluation is finished, whichever comes first.
     */
    @tailrec def loop(
                       source: Current,
                       em: ExecutionModel,
                       cb: Callback[Any],
                       bFirst: Bind,
                       bRest: CallStack,
                       frameIndex: Int): Cancelable = {

      if (frameIndex != 0) source match {
        case ref @ FlatMap(fa, _, _) =>
          var callStack: CallStack = bRest
          val bind = ref.bind()
          if (bFirst ne null) {
            if (callStack eq null) callStack = createCallStack()
            callStack.push(bFirst)
          }
          // Next iteration please
          loop(fa, em, cb, bind, callStack, frameIndex)

        case Now(value) =>
          popNextBind(bFirst, bRest) match {
            case null =>
              cb.onSuccess(value)
              Cancelable.empty
            case bind =>
              val fa = try bind(value) catch { case NonFatal(ex) => Error(ex) }
              // Given a flatMap evaluation just happened, must increment the index
              val nextFrame = em.nextFrameIndex(frameIndex)
              loop(fa, em, cb, null, bRest, nextFrame)
          }

        case Eval(thunk) =>
          var streamErrors = true
          var nextState: Current = null
          try {
            val value = thunk()
            streamErrors = false

            popNextBind(bFirst, bRest) match {
              case null => cb.onSuccess(value)
              case bind =>
                nextState = try bind(value) catch { case NonFatal(ex) => Error(ex) }
            }
          } catch {
            case NonFatal(ex) if streamErrors =>
              nextState = Error(ex)
          }

          if (nextState eq null) Cancelable.empty else {
            // Given a flatMap evaluation just happened, must increment the index
            val nextFrame = em.nextFrameIndex(frameIndex)
            // Next iteration please
            val nextBFirst = if (streamErrors) bFirst else null
            loop(nextState, em, cb, nextBFirst, bRest, nextFrame)
          }

        case Suspend(thunk) =>
          val fa = try thunk() catch { case NonFatal(ex) => Error(ex) }
          loop(fa, em, cb, bFirst, bRest, frameIndex)

        case Error(ex) =>
          findErrorHandler(bFirst, bRest) match {
            case null =>
              cb.onError(ex)
              Cancelable.empty
            case bind =>
              val fa = try bind.error(ex) catch { case NonFatal(e) => Error(e) }
              // Given a flatMap evaluation just happened, must increment the index
              val nextFrame = em.nextFrameIndex(frameIndex)
              // Next cycle please
              loop(fa, em, cb, null, bRest, nextFrame)
          }

        case ref: MemoizeSuspend[_] =>
          ref.asInstanceOf[MemoizeSuspend[A]].value match {
            case Some(materialized) =>
              loop(fromTry(materialized), em, cb, bFirst, bRest, frameIndex)
            case None =>
              goAsync(source, bFirst, bRest, frameIndex, forceAsync = false)
          }

        case async @ Async(_) =>
          goAsync(async, bFirst, bRest, frameIndex, forceAsync = false)
      }
      else {
        // Asynchronous boundary is forced
        goAsync(source, bFirst, bRest, frameIndex, forceAsync = true)
      }
    }

    val em = scheduler.executionModel
    loop(source, em, cb.asInstanceOf[Callback[Any]], null, null, frameStart(em))
  }*/

  /** A run-loop that attempts to complete a
    * [[monix.execution.CancelableFuture CancelableFuture]]
    * synchronously falling back to [[startWithCallback]]
    * and actual asynchronous execution in case of an
    * asynchronous boundary.
    */
  def startAsFuture[A](source: Task[A], scheduler: Scheduler, local: LocalContext.Context): CancelableFuture[A] = {
    /* Called when we hit the first async boundary. */
    def goAsync(
                 source: Current,
                 bindCurrent: Bind,
                 bindRest: CallStack,
                 nextFrame: FrameIndex,
                 forceAsync: Boolean): CancelableFuture[Any] = {

      val p = Promise[Any]()
      val cb: Callback[Any] = new Callback[Any] {
        def onSuccess(value: Any): Unit = p.trySuccess(value)
        def onError(ex: Throwable): Unit = p.tryFailure(ex)
      }

      val context = Context(scheduler)
      if (forceAsync)
        restartAsync(source, context, cb, bindCurrent, bindRest, local)
      else
        startWithCallback(source, context, cb, bindCurrent, bindRest, nextFrame, local)

      CancelableFuture(p.future, context.connection)
    }

    /* Loop that evaluates a Task until the first async boundary is hit,
     * or until the evaluation is finished, whichever comes first.
     */
    @tailrec def loop(
                       source: Current,
                       em: ExecutionModel,
                       bFirst: Bind,
                       bRest: CallStack,
                       frameIndex: Int): CancelableFuture[Any] = {

      if (frameIndex != 0) source match {
        case ref @ FlatMap(fa, _, _) =>
          var callStack: CallStack = bRest
          val bind = ref.bind()
          if (bFirst ne null) {
            if (callStack eq null) callStack = createCallStack()
            callStack.push(bFirst)
          }
          // Next iteration please
          loop(fa, em, bind, callStack, frameIndex)

        case Now(value) =>
          popNextBind(bFirst, bRest) match {
            case null =>
              CancelableFuture.successful(value)
            case bind =>
              val fa = try bind(value) catch { case NonFatal(ex) => Error(ex) }
              // Given a flatMap evaluation just happened, must increment the index
              val nextFrame = em.nextFrameIndex(frameIndex)
              loop(fa, em, null, bRest, nextFrame)
          }

        case Eval(thunk) =>
          var nextBFirst = bFirst
          var result: CancelableFuture[Any] = null
          var nextState: Current = null

          try {
            val value = thunk()
            popNextBind(bFirst, bRest) match {
              case null =>
                result = CancelableFuture.successful(value)
              case bind =>
                nextBFirst = null
                nextState = bind(value)
            }
          } catch {
            case NonFatal(e) =>
              nextState = Error(e)
          }

          if (result ne null) result else {
            // Given a flatMap evaluation just happened, must increment the index
            val nextFrame = em.nextFrameIndex(frameIndex)
            // Next iteration please
            loop(nextState, em, nextBFirst, bRest, nextFrame)
          }

        case Suspend(thunk) =>
          val fa = try thunk() catch { case NonFatal(ex) => Error(ex) }
          loop(fa, em, bFirst, bRest, frameIndex)

        case Error(ex) =>
          findErrorHandler(bFirst, bRest) match {
            case null => CancelableFuture.failed(ex)
            case bind =>
              val fa = try bind.error(ex) catch { case NonFatal(e) => Error(e) }
              // Given a flatMap evaluation just happened, must increment the index
              val nextFrame = em.nextFrameIndex(frameIndex)
              // Next cycle please
              loop(fa, em, null, bRest, nextFrame)
          }

        case async @ Async(_) =>
          goAsync(async, bFirst, bRest, frameIndex, forceAsync = false)

        case ref: MemoizeSuspend[_] =>
          ref.asInstanceOf[MemoizeSuspend[A]].value match {
            case Some(materialized) =>
              loop(fromTry(materialized), em, bFirst, bRest, frameIndex)
            case None =>
              goAsync(source, bFirst, bRest, frameIndex, forceAsync = false)
          }
      }
      else {
        // Asynchronous boundary is forced
        goAsync(source, bFirst, bRest, frameIndex, forceAsync = true)
      }
    }

    val em = scheduler.executionModel
    loop(source, em, null, null, frameStart(em))
      .asInstanceOf[CancelableFuture[A]]
  }

  /** Starts the execution and memoization of a `Task.MemoizeSuspend` state. */
  def startMemoization[A](self: MemoizeSuspend[A], context: Context, cb: Callback[A], bindCurrent: Bind, bindRest: CallStack, nextFrame: FrameIndex, local: LocalContext.Context): Boolean = {
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

    implicit val s = context.scheduler

    self.state.get match {
      case null =>
        val p = Promise[A]()

        if (!self.state.compareAndSet(null, (p, context.connection)))
          startMemoization(self, context, cb, bindCurrent, bindRest, nextFrame, local) // retry
        else {
          val underlying = try self.thunk() catch { case NonFatal(ex) => Error(ex) }

          val callback = new Callback[A] {
            def onError(ex: Throwable): Unit = {
              cacheValue(self.state, Failure(ex))
              restartAsync(Error(ex), context, cb, bindCurrent, bindRest, local)
            }

            def onSuccess(value: A): Unit = {
              cacheValue(self.state, Success(value))
              restartAsync(Now(value), context, cb, bindCurrent, bindRest, local)
            }
          }

          // Asynchronous boundary to prevent stack-overflows!
          s.executeTrampolined { () =>
            startWithCallback(underlying, context, callback, null, null, nextFrame, local)
          }

          true
        }

      case (p: Promise[_], mainCancelable: StackedCancelable) =>
        // execution is pending completion
        context.connection push mainCancelable
        p.asInstanceOf[Promise[A]].future.onComplete { r =>
          context.connection.pop()
          context.frameRef.reset()
          startWithCallback(fromTry(r), context, cb, bindCurrent, bindRest, 1, local)
        }
        true

      case _: Try[_] =>
        // Race condition happened
        false
    }
  }
}

