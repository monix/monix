/*
 * Copyright (c) 2014-2022 Monix Contributors.
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
package internal

import cats.effect.kernel.{ Fiber, Outcome, Poll }
import monix.eval.Task.RaiseError
import monix.eval.internal.TaskFiber.*
import monix.execution.{ Callback, Scheduler }
import monix.execution.atomic.Atomic
import monix.execution.schedulers.TrampolineExecutionContext
import java.util.concurrent.RejectedExecutionException
import scala.annotation.{ switch, tailrec }
import scala.concurrent.ExecutionContext
import scala.util.control.NonFatal

/** Runtime interpreter and Cats Effect fiber handle for [[Task]].
  *
  * At most one `run()` invocation evaluates the interpreter. Asynchronous callbacks
  * and cancellation requests never run it directly; they publish immutable snapshots
  * through `stateRef`. A successful CAS from inactive to active owns the decision to
  * schedule the next run, so interpreter fields are accessed only while that ownership
  * is held, without intrinsic locking.
  */
private[eval] final class TaskFiber[A] private[eval] (
  source: Task[A],
  cb: Callback[Throwable, A],
  initExecutionContext: ExecutionContext = null
)(implicit
  initialScheduler: Scheduler
) extends Fiber[Task, Throwable, A] with Task.Visitor[Any, Control] with Runnable {
  // These interpreter fields are read and written only by the run-loop. A state CAS
  // publishes callback results before a later run installs them in `currentRef`.
  private[this] var currentRef: Current = source
  private[this] var callStackRef: TaskCallStack = null
  private[this] var scheduler: Scheduler = initialScheduler
  // A Scheduler adapter supplies clocks and batching, but Async.executionContext
  // must expose the exact context supplied to evalOn, including in child fibers.
  private[this] var executionContext: ExecutionContext =
    if (initExecutionContext eq null) initialScheduler else initExecutionContext
  private[this] var reschedule = false
  private[this] val runLoop = new Runnable {
    def run(): Unit = runOwned()
  }

  // The run-loop owns mask updates. An inactive canceler may read the depth only after
  // its CAS has acquired the scheduling claim.
  private[this] var maskDepth: Int = 0
  private[this] var resumeCurrent: Boolean = true

  // All state shared with callbacks, cancelers, and joiners lives in this one atomic
  // reference. CAS publication is also the happens-before boundary used when a run
  // moves between scheduler threads.
  private[this] val stateRef = Atomic(
    Active[A](
      Nil,
      runActive = true,
      isCanceled = false,
      pendingRef = null,
      asyncCallback = null
    ): FiberState[A]
  )

  @inline
  private def callStack: TaskCallStack = {
    // Allocate on the first frame push, so terminal fibers need no continuation stack.
    if (callStackRef eq null) callStackRef = new TaskCallStack(8)
    callStackRef
  }

  override def visit(ref: Task.Pure[Any]): Control =
    processUnboxedValue(ref.a)

  override def visit(ref: Task.Delay[Any]): Control = {
    try currentRef = Task.Pure(ref.thunk())
    catch { case NonFatal(error) => currentRef = Task.RaiseError(error) }
    Continue
  }

  override def visit(ref: Task.Defer[Any]): Control = {
    try currentRef = ref.thunk()
    catch { case NonFatal(error) => currentRef = Task.RaiseError(error) }
    Continue
  }

  override def visit(ref: Task.ReadExecutionContext.type): Control =
    processUnboxedValue(executionContext)

  override def visit[S](ref: Task.FlatMap[S, Any]): Control = {
    callStack.pushFlatMap(ref.f.asInstanceOf[Any => Task[Any]])
    currentRef = ref.source
    Continue
  }

  override def visit[S](ref: Task.HandleErrorWith[S, Any]): Control = {
    callStack.pushHandleError(ref.f)
    currentRef = ref.source
    Continue
  }

  override def visit(ref: Task.OnCancel[Any]): Control = {
    callStack.pushOnCancel(ref.onCancel)
    currentRef = ref.source
    Continue
  }

  override def visit(ref: Task.RaiseError): Control = {
    val err = ref.e
    // Error propagation discards binds and cancellation finalizers until the next
    // matching handler. No handler means this is the fiber's terminal error.
    val handler = if (callStackRef eq null) null else callStackRef.findAndPopNextHandleError()
    handler match {
      case null =>
        complete(Outcome.Errored(err))
        cb.onError(err)
        Break
      case bind =>
        // Invoking an error handler may throw; a `NonFatal` becomes the next
        // `RaiseError` node.
        // Try/catch described as statement, otherwise ObjectRef happens ;-)
        try {
          currentRef = bind(err)
        } catch {
          case e if NonFatal(e) =>
            currentRef = RaiseError(e)
        }
        Continue
    }
  }

  override def visit(ref: Task.AsyncSimple[Any]): Control = {
    val callback = new TaskRestartCallback(this, scheduler)
    installCallback(callback)
    callback.start(ref)
    Break
  }

  override def visit[S](ref: Task.AsyncCont[S, Any]): Control = {
    // `callback` and `get` may be used in either order. The indirection retains the
    // first side until the other arrives.
    val callback = new TaskCallbackIndirection[Throwable, S]
    val get = Task.AsyncSimple[S]((_, waiting) => callback.register(waiting))
      .onCancel(Task.delay(callback.clear()))
    // Constructing the continuation result may throw. Move a `NonFatal` into the
    // ordinary `Task` error channel.
    try currentRef = ref.cont(scheduler, callback, get)
    catch {
      case error if NonFatal(error) =>
        currentRef = Task.RaiseError(error)
    }
    Continue
  }

  override def visit(ref: Task.Cancelled.type): Control = {
    markCanceled()
    if (maskDepth > 0) {
      // Keep the cancellation flag pending. Leaving the mask, or entering a matching
      // poll region, will turn it back into a `Cancelled` node.
      currentRef = Task.Pure(())
      Continue
    } else {
      installCallback(null)
      val finalizer = if (callStackRef eq null) null else callStackRef.findAndPopNextOnCancel()
      finalizer match {
        case null =>
          // No finalizers remain. Publishing the canceled outcome starts notifying
          // callers blocked in `Fiber.cancel` or `join`. The root callback has only
          // success/error channels and is not signaled for cancellation.
          currentRef = ref
          complete(Outcome.Canceled())
          Break
        case onCancel =>
          // Finalizers are masked and run in LIFO order. Their errors are reported;
          // cancellation continues after the reporter returns normally.
          maskDepth += 1
          def continueCancellation(): Task[Nothing] = {
            maskDepth -= 1
            Task.Cancelled
          }
          currentRef = Task.HandleErrorWith(
            onCancel.flatMap(_ => continueCancellation()),
            error => {
              scheduler.reportFailure(error)
              continueCancellation()
            }
          )
          Continue
      }
    }
  }

  override def visit(ref: Task.Uncancelable[Any]): Control = {
    val previous = maskDepth
    maskDepth += 1
    val id = maskDepth
    // Poll records this fiber and the current nesting level. Applying it on another
    // fiber, or while a different level is current, cannot lower that mask.
    val poll = new Poll[Task] {
      def apply[B](fa: Task[B]): Task[B] =
        Task.PollRegion(fa, id, TaskFiber.this)
    }

    try {
      currentRef = restoreMask(ref.body(poll), previous)
    } catch {
      case e if NonFatal(e) =>
        // The body function runs during interpretation. Restore the mask before
        // moving its exception into the Task error channel.
        maskDepth = previous
        currentRef = Task.RaiseError(e)
    }
    Continue
  }

  override def visit(ref: Task.PollRegion[Any]): Control = {
    if ((ref.owner eq this) && maskDepth == ref.id) {
      // Lower one matching mask level, then restore it on both success and error.
      val previous = maskDepth
      maskDepth -= 1
      if (isCancellationRequested && maskDepth == 0)
        currentRef = Task.Cancelled
      else
        currentRef = restoreMask(ref.source, previous)
    } else {
      // Foreign or currently mismatched Poll tokens are identity transformations.
      currentRef = ref.source
    }
    Continue
  }

  override def visit[S](ref: Task.Start[S]): Control = {
    // Successful child values are observed through `join`, so the root callback can
    // ignore success. `Callback.empty` still reports a child error.
    val child = new TaskFiber[S](
      ref.source,
      Callback.empty(scheduler),
      initExecutionContext = executionContext
    )(scheduler)
    try {
      scheduler.execute(child)
      currentRef = Task.Pure(child)
    } catch {
      case error: RejectedExecutionException =>
        currentRef = Task.RaiseError(error)
    }
    Continue
  }

  override def visit(ref: Task.EvalOn[Any]): Control = {
    if (ref.ec eq executionContext) {
      currentRef = ref.source
      Continue
    } else {
      val previous = scheduler
      val previousContext = executionContext
      // All three outcomes restore the context. Install the frames before yielding,
      // so cancellation on the target also restores the enclosing context.
      callStack.pushOnCancel(Task.ResumeOn(Task.pure(()), previous, previousContext))
      callStack.pushHandleError(error => Task.ResumeOn(Task.RaiseError(error), previous, previousContext))
      callStack.pushFlatMap(value => Task.ResumeOn(Task.Pure(value), previous, previousContext))
      scheduler = ref.ec match {
        case target: Scheduler => target
        case ec => Scheduler(ec)
      }
      executionContext = ref.ec
      currentRef = ref.source
      reschedule = true
      Break
    }
  }

  override def visit(ref: Task.ResumeOn[Any]): Control = {
    scheduler = ref.scheduler
    executionContext = ref.executionContext
    currentRef = ref.source
    reschedule = true
    Break
  }

  /** Publishes a callback result without allowing concurrent run-loop executions.
    *
    * The CAS stores the result before making an inactive fiber active. Only that
    * inactive-to-active transition schedules a run; otherwise `selectNextRun` consumes
    * the result before the current run relinquishes ownership. Completion or
    * cancellation may discard it. Contract-compliant async nodes produce one result.
    */
  def continueWithRef(ref: Current, callback: TaskRestartCallback): Unit = {
    @tailrec def publish(): Boolean =
      stateRef.get() match {
        case Finished(_) =>
          false
        case current @ Active(_, _, _, pendingRef, registered) =>
          if ((registered ne callback) || (pendingRef ne null))
            false
          else {
            val schedule = !current.runActive
            val update = current.copy(runActive = true, pendingRef = ref)
            if (stateRef.compareAndSet(current, update)) schedule else publish()
          }
      }

    // A scheduler may execute inline or through a trampoline, so submission happens
    // after the state transition rather than inside its CAS retry loop.
    if (publish())
      scheduler.execute(this)
  }

  override def run(): Unit =
    // Enter only after scheduler dispatch, preserving its thread selection. Inline
    // schedulers can resubmit this fiber without nesting interpreter invocations.
    TrampolineExecutionContext.immediate.execute(runLoop)

  private def runOwned(): Unit = {
    // The initial run and explicit scheduler yields already own `currentRef`. Async
    // wake-ups first claim the callback or cancellation published in `stateRef`.
    val runScheduler = scheduler
    val runContext = executionContext
    var remaining = runScheduler.executionModel.recommendedBatchSize
    var runAgain =
      if (resumeCurrent) {
        resumeCurrent = false
        true
      } else {
        selectNextRun()
      }

    while (runAgain) {
      var continue = Continue
      var active = true
      while (continue && active && remaining > 0) {
        remaining -= 1
        // One atomic read covers both completion and cancellation for this dispatch.
        // If cancellation wins before a child first runs, the child does not enter an
        // `OnCancel` or `Uncancelable` node whose dynamic state is not installed yet.
        stateRef.get() match {
          case Active(_, _, isCanceled, _, _) =>
            if (isCanceled && maskDepth == 0 && (currentRef ne Task.Cancelled))
              currentRef = Task.Cancelled

            // Pure, error, and bind dominate normal execution, so the hot path avoids
            // virtual visitor dispatch for their tags.
            (currentRef.tag: @switch) match {
              case 0 =>
                continue = visit(currentRef.asInstanceOf[Task.Pure[AnyRef]])
              case 1 =>
                continue = visit(currentRef.asInstanceOf[Task.RaiseError])
              case 2 =>
                continue = visit(currentRef.asInstanceOf[Task.FlatMap[Any, Any]])
              case _ =>
                continue = currentRef.accept(this)
            }
          case Finished(_) =>
            active = false
        }
      }
      // Bound synchronous work as well as async work. A queued canceler or sibling
      // must get a turn even when this task never suspends by itself.
      if (continue && active && remaining == 0)
        reschedule = true
      if (reschedule) {
        reschedule = false
        resumeCurrent = true
        try {
          scheduler.execute(this)
          return
        } catch {
          case error: RejectedExecutionException =>
            // Rejection means no run was accepted, so this invocation still owns
            // the interpreter. Propagate the failure from the context which is
            // actually running, allowing enclosing handlers and finalizers to run.
            // If its own queue rejected the yield, finish recovery in this run;
            // retrying that queue at each batch could replace cleanup with errors.
            remaining =
              if (scheduler eq runScheduler) Int.MaxValue
              else runScheduler.executionModel.recommendedBatchSize
            scheduler = runScheduler
            executionContext = runContext
            resumeCurrent = false
            currentRef = Task.RaiseError(error)
        }
      } else
        runAgain = selectNextRun()
    }
  }

  /** Publishes cancellation atomically and waits for this fiber's outcome.
    * Waiting is uncancelable: completion of `Fiber.cancel` means the remaining
    * registered finalizers have finished, not merely that a flag was set.
    */
  override def cancel: Task[Unit] =
    Task.uncancelable { _ =>
      Task.delay(requestCancel()).flatMap(_ => join.map(_ => ()))
    }

  /** Suspends until the atomic result state is terminal. The CAS loop in
    * [[registerJoin]] prevents registration from missing concurrent completion.
    * Registration has no removal token, so the target retains a canceled joiner's
    * callback until the target itself completes.
    */
  override def join: Task[Outcome[Task, Throwable, A]] =
    Task.AsyncSimple((_, callback) => registerJoin(callback))

  private def processUnboxedValue(unboxedRef: Any): Control = {
    // Success discards error and cancellation frames until the next bind. Finding no
    // bind means this is the fiber's terminal value.
    val nextBind = if (callStackRef eq null) null else callStackRef.findAndPopNextFlatMap()
    nextBind match {
      case null =>
        val value = unboxedRef.asInstanceOf[A]
        complete(Outcome.Succeeded(Task.Pure(value)))
        cb.onSuccess(value)
        Break
      case bind =>
        // Invoking a bind may throw; a `NonFatal` becomes the next `RaiseError` node.
        // Try/catch described as statement to prevent ObjectRef ;-)
        try {
          currentRef = bind(unboxedRef)
        } catch {
          case ex if NonFatal(ex) =>
            currentRef = Task.RaiseError(ex)
        }
        Continue
    }
  }

  @tailrec
  private def installCallback(callback: TaskRestartCallback): Unit =
    stateRef.get() match {
      case current @ Active(_, _, _, _, _) =>
        if ((current.asyncCallback ne callback) || (current.pendingRef ne null)) {
          val update = current.copy(asyncCallback = callback, pendingRef = null)
          if (!stateRef.compareAndSet(current, update)) installCallback(callback)
        }
      case Finished(_) => ()
    }

  /** Restores a prior cancellation depth on both success and error. A cancellation
    * recorded while masked takes precedence as soon as restoration exposes depth 0.
    */
  private def restoreMask(source: Task[Any], previous: Int): Task[Any] =
    Task.HandleErrorWith(
      Task.FlatMap[Any, Any](
        source,
        value => {
          maskDepth = previous
          if (isCancellationRequested && maskDepth == 0) Task.Cancelled else Task.Pure(value)
        }
      ),
      error => {
        maskDepth = previous
        if (isCancellationRequested && maskDepth == 0) Task.Cancelled else Task.RaiseError(error)
      }
    )

  private def complete(outcome: Outcome[Task, Throwable, A]): Unit = {
    // Only the successful CAS can finish the fiber. Listener notification starts
    // after publication, and reversing restores registration order.
    // Terminal success and error call this before signaling the unsafe-run callback.
    // Each listener is isolated: failure to resume one joiner must not prevent other
    // joiners or the unsafe-run callback from observing this terminal outcome.
    @tailrec def loop(): Unit =
      stateRef.get() match {
        case current @ Active(listeners, _, _, _, _) =>
          if (stateRef.compareAndSet(current, Finished(outcome))) {
            currentRef = null
            callStackRef = null
            listeners.reverse.foreach { listener =>
              try listener.onSuccess(outcome)
              catch { case NonFatal(error) => scheduler.reportFailure(error) }
            }
          } else
            loop()
        case Finished(_) =>
          ()
      }

    loop()
  }

  private def registerJoin(callback: Callback[Throwable, Outcome[Task, Throwable, A]]): Unit = {
    // A registration either adds its callback while the state is `Active`, so
    // `complete` sees it, or observes `Finished` and receives the outcome immediately.
    @tailrec def loop(): Unit =
      stateRef.get() match {
        case current @ Active(listeners, _, _, _, _) =>
          if (!stateRef.compareAndSet(current, current.copy(listeners = callback :: listeners)))
            loop()
        case Finished(outcome) =>
          callback.onSuccess(outcome)
      }
    loop()
  }

  private def requestCancel(): Unit = {
    @tailrec def releaseMaskedClaim(): Boolean =
      stateRef.get() match {
        case Finished(_) =>
          false
        case current @ Active(_, true, true, pendingRef, _) =>
          // A racing callback observed the canceler's scheduling claim and therefore
          // did not submit a run. Keep the claim and schedule that callback instead of
          // releasing it.
          if (pendingRef ne null)
            true
          else if (stateRef.compareAndSet(current, current.copy(runActive = false)))
            false
          else
            releaseMaskedClaim()
        case Active(_, false, true, _, _) =>
          false
        case Active(_, _, false, _, _) =>
          false
      }

    @tailrec def publish(): Boolean =
      stateRef.get() match {
        case Finished(_) =>
          false
        case Active(_, _, true, _, _) =>
          false
        case current @ Active(_, true, false, _, _) =>
          if (stateRef.compareAndSet(current, current.copy(isCanceled = true))) false
          else publish()
        case current @ Active(_, false, false, _, _) =>
          val update = current.copy(runActive = true, isCanceled = true)
          if (stateRef.compareAndSet(current, update)) {
            // The inactive-to-active CAS transfers access to the run-loop fields to
            // this thread. A masked suspension needs no scheduler handoff unless a
            // callback raced the mask check.
            if (maskDepth == 0) true else releaseMaskedClaim()
          } else {
            publish()
          }
      }

    // Submission stays outside the state transition because a scheduler may execute
    // inline or through a trampoline.
    if (publish())
      scheduler.execute(this)
  }

  /** Selects work published while the run-loop was active.
    *
    * Priority is terminal outcome, unmasked cancellation, callback result, then
    * marking the run-loop inactive. The successful CAS prevents a callback or
    * cancellation request from being lost while ownership changes.
    */
  @tailrec
  private def selectNextRun(): Boolean =
    stateRef.get() match {
      case Finished(_) =>
        false
      case current @ Active(_, _, isCanceled, pendingRef, _) =>
        if (isCanceled && maskDepth == 0) {
          val update = current.copy(pendingRef = null, asyncCallback = null)
          if (stateRef.compareAndSet(current, update)) {
            currentRef = Task.Cancelled
            true
          } else {
            selectNextRun()
          }
        } else if (pendingRef ne null) {
          val update = current.copy(pendingRef = null, asyncCallback = null)
          if (stateRef.compareAndSet(current, update)) {
            currentRef = pendingRef
            true
          } else {
            selectNextRun()
          }
        } else {
          val update = current.copy(runActive = false)
          if (stateRef.compareAndSet(current, update)) false else selectNextRun()
        }
    }

  @tailrec
  private def markCanceled(): Unit =
    stateRef.get() match {
      case current @ Active(_, _, false, _, _) =>
        if (!stateRef.compareAndSet(current, current.copy(isCanceled = true)))
          markCanceled()
      case Active(_, _, true, _, _) | Finished(_) =>
        ()
    }

  private def isCancellationRequested: Boolean =
    stateRef.get() match {
      case Active(_, _, isCanceled, _, _) => isCanceled
      case Finished(_) => false
    }
}

object TaskFiber {
  private type Current = Task[Any]

  private type Control = Boolean
  private final val Continue: Control = true
  private final val Break: Control = false

  /** Immutable state shared across the run-loop, async callbacks, cancelers, and
    * joiners.
    *
    * While `Active`:
    *
    *   - `runActive` means a run is executing/submitted, or a canceler temporarily owns
    *     the decision whether one is needed;
    *   - `isCanceled` is monotonic and is interpreted when `maskDepth` permits;
    *   - `pendingRef` contains at most one callback result and implies `runActive`;
    *   - `asyncCallback` identifies the only suspension allowed to publish a result;
    *   - `listeners` contains join callbacks in reverse registration order.
    *
    * `Finished` is terminal. Replacing an `Active` reference with `Finished` publishes
    * the outcome before any listener is invoked.
    */
  private sealed trait FiberState[A]

  private final case class Active[A](
    listeners: List[Callback[Throwable, Outcome[Task, Throwable, A]]],
    runActive: Boolean,
    isCanceled: Boolean,
    pendingRef: Current,
    asyncCallback: TaskRestartCallback
  ) extends FiberState[A]

  private final case class Finished[A](
    outcome: Outcome[Task, Throwable, A]
  ) extends FiberState[A]
}
