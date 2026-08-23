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
import monix.eval.IO.RaiseError
import monix.eval.internal.IOFiber.*
import monix.execution.{ Callback, Scheduler }
import monix.execution.atomic.Atomic
import scala.annotation.{ switch, tailrec }
import scala.util.control.NonFatal

/** Runtime interpreter and Cats Effect fiber handle for [[IO]].
  *
  * At most one `run()` invocation evaluates the interpreter. Asynchronous callbacks
  * and cancellation requests never run it directly; they publish immutable snapshots
  * through `stateRef`. A successful CAS from inactive to active owns the decision to
  * schedule the next run, so interpreter fields are accessed only while that ownership
  * is held, without intrinsic locking.
  */
private[eval] final class IOFiber[A] private[eval] (
  source: IO[A],
  cb: Callback[Throwable, A],
  initCallStack: IOCallStack = null,
  initIsCancelled: Boolean = false
)(implicit
  scheduler: Scheduler
) extends Fiber[IO, Throwable, A] with IO.Visitor[Any, Control] with Runnable {
  // These interpreter fields are read and written only by the run-loop. A state CAS
  // publishes callback results before a later run installs them in `currentRef`.
  private[this] var currentRef: Current = source
  private[this] var callStackRef: IOCallStack = initCallStack
  private[this] var _restartCallback: IORestartCallback = _

  // The run-loop owns mask updates. An inactive canceler may read the depth only after
  // its CAS has acquired the scheduling claim.
  private[this] var maskDepth: Int = 0
  private[this] var firstRun: Boolean = true

  // All state shared with callbacks, cancelers, and joiners lives in this one atomic
  // reference. CAS publication is also the happens-before boundary used when a run
  // moves between scheduler threads.
  private[this] val stateRef = Atomic(
    Active[A](Nil, runActive = true, isCanceled = initIsCancelled, pendingRef = null): FiberState[A]
  )

  @inline
  private def callStack: IOCallStack = {
    // Allocate on the first frame push or outcome search, keeping fiber construction
    // independent of the continuation-stack representation.
    if (callStackRef eq null) callStackRef = new IOCallStack(8)
    callStackRef
  }

  override def visit(ref: IO.Pure[Any]): Control =
    processUnboxedValue(ref.a.asInstanceOf[AnyRef])

  override def visit[S](ref: IO.FlatMap[S, Any]): Control = {
    callStack.pushFlatMap(ref.f.asInstanceOf[Any => IO[Any]])
    currentRef = ref.source
    Continue
  }

  override def visit[S](ref: IO.HandleErrorWith[S, Any]): Control = {
    callStack.pushHandleError(ref.f)
    currentRef = ref.source
    Continue
  }

  override def visit(ref: IO.OnCancel[Any]): Control = {
    callStack.pushOnCancel(ref.onCancel)
    currentRef = ref.source
    Continue
  }

  override def visit(ref: IO.RaiseError): Control = {
    val err = ref.e
    // Error propagation discards binds and cancellation finalizers until the next
    // matching handler. No handler means this is the fiber's terminal error.
    callStack.findAndPopNextHandleError() match {
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

  override def visit(ref: IO.AsyncSimple[Any]): Control = {
    // A callback arriving while `runActive` is true is stored in `pendingRef`. One
    // arriving after the run-loop stops schedules it again.
    restartCallback().start(ref)
    Break
  }

  override def visit[S](ref: IO.AsyncCont[S, Any]): Control = {
    // `callback` and `get` may be used in either order. The indirection retains the
    // first side until the other arrives.
    val callback = new IOCallbackIndirection[Throwable, S]
    val get = IO.AsyncSimple[S]((_, waiting) => callback.register(waiting))
    // Constructing the continuation result may throw. Move a `NonFatal` into the
    // ordinary `IO` error channel.
    try currentRef = ref.cont(scheduler, callback, get)
    catch {
      case error if NonFatal(error) =>
        currentRef = IO.RaiseError(error)
    }
    Continue
  }

  override def visit(ref: IO.Cancelled.type): Control = {
    markCanceled()
    if (maskDepth > 0) {
      // Keep the cancellation flag pending. Leaving the mask, or entering a matching
      // poll region, will turn it back into a `Cancelled` node.
      currentRef = IO.Pure(())
      Continue
    } else
      callStack.findAndPopNextOnCancel() match {
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
          def continueCancellation(): IO[Nothing] = {
            maskDepth -= 1
            IO.Cancelled
          }
          currentRef = IO.HandleErrorWith(
            onCancel.flatMap(_ => continueCancellation()),
            error => {
              scheduler.reportFailure(error)
              continueCancellation()
            }
          )
          Continue
      }
  }

  override def visit(ref: IO.Uncancelable[Any]): Control = {
    val previous = maskDepth
    maskDepth += 1
    val id = maskDepth
    // Poll records this fiber and the current nesting level. Applying it on another
    // fiber, or while a different level is current, cannot lower that mask.
    val poll = new Poll[IO] {
      def apply[B](fa: IO[B]): IO[B] =
        IO.PollRegion(fa, id, IOFiber.this)
    }

    try {
      currentRef = restoreMask(ref.body(poll), previous)
    } catch {
      case e if NonFatal(e) =>
        // The body function runs during interpretation. Restore the mask before
        // moving its exception into the IO error channel.
        maskDepth = previous
        currentRef = IO.RaiseError(e)
    }
    Continue
  }

  override def visit(ref: IO.PollRegion[Any]): Control = {
    if ((ref.owner eq this) && maskDepth == ref.id) {
      // Lower one matching mask level, then restore it on both success and error.
      val previous = maskDepth
      maskDepth -= 1
      if (isCancellationRequested && maskDepth == 0)
        currentRef = IO.Cancelled
      else
        currentRef = restoreMask(ref.source, previous)
    } else {
      // Foreign or currently mismatched Poll tokens are identity transformations.
      currentRef = ref.source
    }
    Continue
  }

  override def visit[S](ref: IO.Start[S]): Control = {
    // Successful child values are observed through `join`, so the root callback can
    // ignore success. `Callback.empty` still reports a child error.
    val child = new IOFiber[S](ref.source, Callback.empty)(scheduler)
    scheduler.execute(child)
    currentRef = IO.Pure(child)
    Continue
  }

  /** Publishes a callback result without allowing concurrent run-loop executions.
    *
    * The CAS stores the result before making an inactive fiber active. Only that
    * inactive-to-active transition schedules a run; otherwise `selectNextRun` consumes
    * the result before the current run relinquishes ownership. Completion or
    * cancellation may discard it. Contract-compliant async nodes produce one result.
    */
  def continueWithRef(ref: Current): Unit = {
    @tailrec def publish(): Boolean =
      stateRef.get() match {
        case Finished(_) =>
          false
        case current @ Active(_, _, _, pendingRef) =>
          if (pendingRef ne null)
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

  override def run(): Unit = {
    // The initial run already owns `source`. Later runs first claim the callback or
    // cancellation wake-up published in `stateRef`.
    var runAgain =
      if (firstRun) {
        firstRun = false
        true
      } else {
        selectNextRun()
      }

    while (runAgain) {
      var continue = Continue
      var active = true
      while (continue && active) {
        // One atomic read covers both completion and cancellation for this dispatch.
        // If cancellation wins before a child first runs, the child does not enter an
        // `OnCancel` or `Uncancelable` node whose dynamic state is not installed yet.
        stateRef.get() match {
          case Active(_, _, isCanceled, _) =>
            if (isCanceled && maskDepth == 0 && (currentRef ne IO.Cancelled))
              currentRef = IO.Cancelled

            // Pure, error, and bind dominate normal execution, so the hot path avoids
            // virtual visitor dispatch for their tags.
            (currentRef.tag: @switch) match {
              case 0 =>
                continue = visit(currentRef.asInstanceOf[IO.Pure[AnyRef]])
              case 1 =>
                continue = visit(currentRef.asInstanceOf[IO.RaiseError])
              case 2 =>
                continue = visit(currentRef.asInstanceOf[IO.FlatMap[Any, Any]])
              case _ =>
                continue = currentRef.accept(this)
            }
          case Finished(_) =>
            active = false
        }
      }
      runAgain = selectNextRun()
    }
  }

  /** Requests cancellation on this fiber's scheduler and waits for its outcome.
    * Waiting is uncancelable: completion of `Fiber.cancel` means the remaining
    * registered finalizers have finished, not merely that a flag was set.
    */
  override def cancel: IO[Unit] =
    IO.uncancelable { _ =>
      IO.delay(scheduler.execute(() => requestCancel())).flatMap(_ => join.map(_ => ()))
    }

  /** Suspends until the atomic result state is terminal. The CAS loop in
    * [[registerJoin]] prevents registration from missing concurrent completion.
    * Registration has no removal token, so the target retains a canceled joiner's
    * callback until the target itself completes.
    */
  override def join: IO[Outcome[IO, Throwable, A]] =
    IO.AsyncSimple((_, callback) => registerJoin(callback))

  private def processUnboxedValue(unboxedRef: AnyRef): Control = {
    // Success discards error and cancellation frames until the next bind. Finding no
    // bind means this is the fiber's terminal value.
    callStack.findAndPopNextFlatMap() match {
      case null =>
        val value = unboxedRef.asInstanceOf[A]
        complete(Outcome.Succeeded(IO.Pure(value)))
        cb.onSuccess(value)
        Break
      case bind =>
        // Invoking a bind may throw; a `NonFatal` becomes the next `RaiseError` node.
        // Try/catch described as statement to prevent ObjectRef ;-)
        try {
          currentRef = bind(unboxedRef)
        } catch {
          case ex if NonFatal(ex) =>
            currentRef = IO.RaiseError(ex)
        }
        Continue
    }
  }

  private def restartCallback(): IORestartCallback = {
    if (_restartCallback == null)
      _restartCallback = new IORestartCallback(this, scheduler)
    _restartCallback
  }

  /** Restores a prior cancellation depth on both success and error. A cancellation
    * recorded while masked takes precedence as soon as restoration exposes depth 0.
    */
  private def restoreMask(source: IO[Any], previous: Int): IO[Any] =
    IO.HandleErrorWith(
      IO.FlatMap[Any, Any](
        source,
        value => {
          maskDepth = previous
          if (isCancellationRequested && maskDepth == 0) IO.Cancelled else IO.Pure(value)
        }
      ),
      error => {
        maskDepth = previous
        if (isCancellationRequested && maskDepth == 0) IO.Cancelled else IO.RaiseError(error)
      }
    )

  private def complete(outcome: Outcome[IO, Throwable, A]): Unit = {
    // Only the successful CAS can finish the fiber. Listener notification starts
    // after publication, and reversing restores registration order.
    // Terminal success and error call this before signaling the unsafe-run callback.
    // Listener invocation is unguarded; a throwing listener stops later notifications.
    @tailrec def loop(): Unit =
      stateRef.get() match {
        case current @ Active(listeners, _, _, _) =>
          if (stateRef.compareAndSet(current, Finished(outcome)))
            listeners.reverse.foreach(_.onSuccess(outcome))
          else
            loop()
        case Finished(_) =>
          ()
      }

    loop()
  }

  private def registerJoin(callback: Callback[Throwable, Outcome[IO, Throwable, A]]): Unit = {
    // A registration either adds its callback while the state is `Active`, so
    // `complete` sees it, or observes `Finished` and receives the outcome immediately.
    @tailrec def loop(): Unit =
      stateRef.get() match {
        case current @ Active(listeners, _, _, _) =>
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
        case current @ Active(_, true, true, pendingRef) =>
          // A racing callback observed the canceler's scheduling claim and therefore
          // did not submit a run. Keep the claim and schedule that callback instead of
          // releasing it.
          if (pendingRef ne null)
            true
          else if (stateRef.compareAndSet(current, current.copy(runActive = false)))
            false
          else
            releaseMaskedClaim()
        case Active(_, false, true, _) =>
          false
        case Active(_, _, false, _) =>
          false
      }

    @tailrec def publish(): Boolean =
      stateRef.get() match {
        case Finished(_) =>
          false
        case Active(_, _, true, _) =>
          false
        case current @ Active(_, true, false, _) =>
          if (stateRef.compareAndSet(current, current.copy(isCanceled = true))) false
          else publish()
        case current @ Active(_, false, false, _) =>
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
      case current @ Active(_, _, isCanceled, pendingRef) =>
        if (isCanceled && maskDepth == 0) {
          val update = current.copy(pendingRef = null)
          if (stateRef.compareAndSet(current, update)) {
            currentRef = IO.Cancelled
            true
          } else {
            selectNextRun()
          }
        } else if (pendingRef ne null) {
          val update = current.copy(pendingRef = null)
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
      case current @ Active(_, _, false, _) =>
        if (!stateRef.compareAndSet(current, current.copy(isCanceled = true)))
          markCanceled()
      case Active(_, _, true, _) | Finished(_) =>
        ()
    }

  private def isCancellationRequested: Boolean =
    stateRef.get() match {
      case Active(_, _, isCanceled, _) => isCanceled
      case Finished(_) => false
    }
}

object IOFiber {
  private type Current = IO[Any]

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
    *   - `listeners` contains join callbacks in reverse registration order.
    *
    * `Finished` is terminal. Replacing an `Active` reference with `Finished` publishes
    * the outcome before any listener is invoked.
    */
  private sealed trait FiberState[A]

  private final case class Active[A](
    listeners: List[Callback[Throwable, Outcome[IO, Throwable, A]]],
    runActive: Boolean,
    isCanceled: Boolean,
    pendingRef: Current
  ) extends FiberState[A]

  private final case class Finished[A](
    outcome: Outcome[IO, Throwable, A]
  ) extends FiberState[A]
}
