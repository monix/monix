package monifu.reactive.observers

import monifu.reactive.{Observable, Channel, Observer}
import scala.concurrent.Promise
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

  // MUST BE lock.enter by `self`, only available if isConnected == false
  private[this] var queue = mutable.ArrayBuffer.empty[T]
  // MUST BE lock.enter by `self`, only available if isConnected == false
  private[this] var scheduledDone = false
  // MUST BE lock.enter by `self`, only available if isConnected == false
  private[this] var scheduledError = null : Throwable
  // MUST BE lock.enter by `self`
  private[this] var isConnectionStarted = false

  // Promise guaranteed to be fulfilled once isConnected is
  // seen as true and used for back-pressure.
  // MUST BE lock.enter by `self`, only available if isConnected == false
  private[this] var connectedPromise = Promise[Ack]()

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
          def onNext(elem: T) =
            observer.onNext(elem).onCancelComplete(connectedPromise)

          def onError(ex: Throwable) = {
            connectedPromise.success(Cancel)
            observer.onError(ex)
          }

          def onComplete() = lock.enter {
            if (scheduledDone) {
              connectedPromise.success(Cancel)
              if (scheduledError ne null)
                observer.onError(scheduledError)
              else
                observer.onComplete()
            }
            else
              connectedPromise.success(Continue)
          }
        })

        connectedPromise.future.onComplete(_ => lock.enter {
          queue = null // for garbage collecting purposes
          scheduledError = null // for garbage collecting purposes
          connectedPromise = null // for garbage collecting purposes
          isConnected = true
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
    if (!isConnected)
      lock.enter {
        // race condition guard, since connectedPromise may be null otherwise
        if (!isConnected)
          connectedPromise.future.flatMap {
            case Cancel => Cancel
            case Continue => observer.onNext(elem)
          }
        else
          observer.onNext(elem)
      }
    else {
      // taking fast path
      observer.onNext(elem)
    }
  }

  def onComplete() = {
    if (!isConnected)
      lock.enter {
        // race condition guard, since connectedPromise may be null otherwise
        if (!isConnected)
          connectedPromise.future
            .onContinueTriggerComplete(observer)
        else
          observer.onComplete()
      }
    else {
      // taking fast path
      observer.onComplete()
    }
  }

  def onError(ex: Throwable) = {
    if (!isConnected)
      lock.enter {
        // race condition guard, since connectedPromise may be null otherwise
        if (!isConnected)
          connectedPromise.future
            .onContinueTriggerError(observer, ex)
        else
          observer.onComplete()
      }
    else {
      // taking fast path
      observer.onError(ex)
    }
  }
}
