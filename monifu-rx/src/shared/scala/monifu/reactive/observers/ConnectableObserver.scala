package monifu.reactive.observers

import monifu.reactive.{Observable, Channel, Observer}
import scala.concurrent.{Future, Promise}
import monifu.reactive.api.Ack
import monifu.concurrent.Scheduler
import monifu.reactive.api.Ack.{Cancel, Continue}
import scala.collection.mutable
import monifu.reactive.internals.FutureAckExtensions
import monifu.concurrent.locks.SpinLock

/**
 * Wraps an [[Observer]] into an implementation that abstains from emitting items until the call
 * to `connect()` happens. Prior to `connect()` it's also a [[Channel]] into which you can enqueue
 * events for delivery once `connect()` happens, but before any items
 * emitted by `onNext` / `onComplete` and `onError`.
 *
 * Example: {{{
 *   val obs = ConnectableObserver(observer)
 *
 *   // schedule onNext event, after connect()
 *   obs.onNext("c")
 *
 *   // schedule event "a" to be emitted first
 *   obs.pushNext("a")
 *   // schedule event "b" to be emitted second
 *   obs.pushNext("b")
 *
 *   // underlying observer now gets events "a", "b", "c" in order
 *   obs.connect()
 * }}}
 *
 * Example of an observer ended in error: {{{
 *   val obs = ConnectableObserver(observer)
 *
 *   // schedule onNext event, after connect()
 *   obs.onNext("c")
 *
 *   obs.pushNext("a") // event "a" to be emitted first
 *   obs.pushNext("b") // event "b" to be emitted first
 *
 *   // schedule an onError sent downstream, once connect()
 *   // happens, but after "a" and "b"
 *   obs.pushError(new RuntimeException())
 *
 *   // underlying observer receives ...
 *   // onNext("a") -> onNext("b") -> onError(RuntimeException)
 *   obs.connect()
 *
 *   // NOTE: that onNext("c") never happens
 * }}}
 */
final class ConnectableObserver[-T](underlying: Observer[T])(implicit s: Scheduler)
  extends Channel[T] with Observer[T] { self =>

  private[this] val observer = SafeObserver(underlying)
  private[this] val lock = SpinLock()

  // MUST BE synchronized by `lock`, only available if isConnected == false
  private[this] var queue = mutable.ArrayBuffer.empty[T]
  // MUST BE synchronized by `lock`, only available if isConnected == false
  private[this] var scheduledDone = false
  // MUST BE synchronized by `lock`, only available if isConnected == false
  private[this] var scheduledError = null : Throwable
  // MUST BE synchronized by `lock`
  private[this] var isConnectionStarted = false
  // MUST BE synchronized by `lock`, as long as isConnected == false
  private[this] var wasCanceled = false

  // Promise guaranteed to be fulfilled once isConnected is
  // seen as true and used for back-pressure.
  // MUST BE synchronized by `lock`, only available if isConnected == false
  private[this] val connectedPromise = Promise[Ack]()
  private[this] var connectedFuture = connectedPromise.future

  // Volatile that is set to true once the buffer is drained.
  // Once visible as true, it implies that the queue is empty
  // and has been drained and thus the onNext/onError/onComplete
  // can take the fast path
  @volatile private[this] var isConnected = false

  /**
   * Connects the underling observer to the upstream publisher.
   *
   * This function should be idempotent. Calling it multiple times should have the same
   * effect as calling it once.
   */
  def connect(): Unit =
    lock.enter {
      if (!isConnected && !isConnectionStarted) {
        isConnectionStarted = true

        Observable.from(queue).unsafeSubscribe(new Observer[T] {
          private[this] var lastAck = Continue : Future[Ack]
          private[this] val bufferWasDrained = Promise[Ack]()

          bufferWasDrained.future.onSuccess {
            case Continue =>
              connectedPromise.success(Continue)
              isConnected = true
              queue = null // gc relief
            case Cancel =>
              wasCanceled = true
              connectedPromise.success(Cancel)
              isConnected = true
              queue = null // gc relief
          }

          def onNext(elem: T): Future[Ack] = {
            lastAck = observer.onNext(elem)
              .ifCancelTryCanceling(bufferWasDrained)
            lastAck
          }

          def onComplete(): Unit = {
            if (!scheduledDone) {
              bufferWasDrained.tryCompleteWith(lastAck)
            }
            else if (scheduledError ne null) {
              if (bufferWasDrained.trySuccess(Cancel))
                observer.onError(scheduledError)
            }
            else if (bufferWasDrained.trySuccess(Cancel))
              observer.onComplete()
          }

          def onError(ex: Throwable): Unit = {
            if (scheduledError ne null)
              s.reportFailure(ex)
            else {
              scheduledDone = true
              scheduledError = ex
              if (bufferWasDrained.trySuccess(Cancel))
                observer.onError(ex)
              else
                s.reportFailure(ex)
            }
          }
        })
      }
    }

  /**
   * Emit an item immediately to the underlying observer,
   * after previous `pushNext()` events, but before any events emitted through
   * `onNext`.
   */
  def pushNext(elems: T*) =
    lock.enter {
      if (isConnected || isConnectionStarted)
        throw new IllegalStateException("Observer was already connected, so cannot pushNext")
      else if (!scheduledDone)
        queue.append(elems : _*)
    }

  /**
   * Emit an item
   */
  def pushComplete() =
    lock.enter {
      if (isConnected || isConnectionStarted)
        throw new IllegalStateException("Observer was already connected, so cannot pushNext")
      else if (!scheduledDone) {
        scheduledDone = true
      }
    }

  def pushError(ex: Throwable) =
    lock.enter {
      if (isConnected || isConnectionStarted)
        throw new IllegalStateException("Observer was already connected, so cannot pushNext")
      else if (!scheduledDone) {
        scheduledDone = true
        scheduledError = ex
      }
    }

  def onNext(elem: T) = {
    if (!isConnected) {
      // no need for synchronization here, since this reference is initialized
      // before the subscription happens and because it gets written only in
      // onNext / onComplete, which are non-concurrent clauses
      connectedFuture = connectedFuture.flatMap {
        case Cancel => Cancel
        case Continue =>
          observer.onNext(elem)
      }
      connectedFuture
    }
    else if (!wasCanceled) {
      // taking fast path
      observer.onNext(elem)
    }
    else {
      // was canceled either during connect, or the upstream publisher
      // sent an onNext event after onComplete / onError
      Cancel
    }
  }

  def onComplete() = {
    // we cannot take a fast path here
    connectedFuture.onContinueComplete(observer)
  }

  def onError(ex: Throwable) = {
    // we cannot take a fast path here
    connectedFuture.onContinueComplete(observer, ex)
  }
}
