package monifu.reactive.subjects

import scala.concurrent.Future
import monifu.reactive.api.Ack.{Continue, Cancel}
import monifu.concurrent.Scheduler
import monifu.reactive.{Subject, Observer}
import monifu.concurrent.atomic.padded.Atomic
import scala.annotation.tailrec
import monifu.reactive.api.Ack
import monifu.reactive.observers.ConnectableObserver
import monifu.reactive.internals.PromiseCounter
import monifu.reactive.internals.FutureAckExtensions
import monifu.reactive.observables.GenericObservable

/**
 * `BehaviorSubject` when subscribed, will emit the most recently emitted item by the source,
 * or the `initialValue` (as the seed) in case no value has yet been emitted, the continuing
 * to emit events subsequent to the time of invocation.
 *
 * <img src="https://raw.githubusercontent.com/wiki/alexandru/monifu/assets/rx-operators/S.BehaviorSubject.png" />
 *
 * When the source terminates in error, the `BehaviorSubject` will not emit any items to
 * subsequent subscribers, but instead it will pass along the error notification.
 *
 * <img src="https://raw.githubusercontent.com/wiki/alexandru/monifu/assets/rx-operators/S.BehaviorSubject.png" />
 */
final class BehaviorSubject[T] private (initialValue: T, s: Scheduler) extends Subject[T,T] with GenericObservable[T] { self =>
  import BehaviorSubject.State
  import BehaviorSubject.State._

  implicit val scheduler = s
  private[this] val state = Atomic(Empty(initialValue) : State[T])

  def subscribeFn(observer: Observer[T]): Unit = {
    @tailrec
    def loop(): ConnectableObserver[T] = {
      state.get match {
        case current @ Empty(cachedValue) =>
          val obs = new ConnectableObserver[T](observer)
          obs.pushNext(cachedValue)

          if (!state.compareAndSet(current, Active(Array(obs), cachedValue)))
            loop()
          else
            obs

        case current @ Active(observers, cachedValue) =>
          val obs = new ConnectableObserver[T](observer)
          obs.pushNext(cachedValue)

          if (!state.compareAndSet(current, Active(observers :+ obs, cachedValue)))
            loop()
          else
            obs

        case current @ Complete(cachedValue, errorThrown) =>
          val obs = new ConnectableObserver[T](observer)
          if (errorThrown eq null) {
            obs.pushNext(cachedValue)
            obs.pushComplete()
          }
          else {
            obs.pushError(errorThrown)
          }
          obs
      }
    }

    loop().connect()
  }

  @tailrec
  def onNext(elem: T): Future[Ack] = {
    state.get match {
      case current @ Empty(_) =>
        if (!state.compareAndSet(current, Empty(elem)))
          onNext(elem)
        else
          Continue

      case current @ Active(observers, cachedValue) =>
        if (!state.compareAndSet(current, Active(observers, elem)))
          onNext(elem)

        else
          stream(observers, elem)

      case _ =>
        Cancel
    }
  }

  @tailrec
  def onComplete(): Unit =
    state.get match {
      case current @ Empty(cachedValue) =>
        if (!state.compareAndSet(current, Complete(cachedValue, null))) {
          onComplete() // retry
        }
      case current @ Active(observers, cachedValue) =>
        if (!state.compareAndSet(current, Complete(cachedValue, null))) {
          onComplete() // retry
        }
        else {
          var idx = 0
          while (idx < observers.length) {
            observers(idx).onComplete()
            idx += 1
          }
        }
      case _ =>
        // already complete, ignore
    }

  @tailrec
  def onError(ex: Throwable): Unit =
    state.get match {
      case current @ Empty(cachedValue) =>
        if (!state.compareAndSet(current, Complete(cachedValue, ex))) {
          onError(ex) // retry
        }
      case current @ Active(observers, cachedValue) =>
        if (!state.compareAndSet(current, Complete(cachedValue, ex))) {
          onError(ex) // retry
        }
        else {
          var idx = 0
          while (idx < observers.length) {
            observers(idx).onError(ex)
            idx += 1
          }
        }
      case _ =>
        // already complete, ignore
    }

  private[this] def stream(array: Array[ConnectableObserver[T]], elem: T): Future[Continue] = {
    val newPromise = PromiseCounter[Continue](Continue, array.length)
    val length = array.length
    var idx = 0

    while (idx < length) {
      val obs = array(idx)
      obs.onNext(elem).onCompleteNow {
        case Continue.IsSuccess =>
          newPromise.countdown()
        case _ =>
          removeSubscription(obs)
          newPromise.countdown()
      }

      idx += 1
    }

    newPromise.future
  }

  @tailrec
  private[this] def removeSubscription(observer: Observer[T]): Unit =
    state.get match {
      case current @ Active(observers, cachedValue) =>
        val update = observers.filterNot(_ == observer)
        if (update.nonEmpty) {
          if (!state.compareAndSet(current, Active(update, cachedValue)))
            removeSubscription(observer)
        }
        else {
          if (!state.compareAndSet(current, Empty(cachedValue)))
            removeSubscription(observer)
        }
      case _ => // ignore
    }
}

object BehaviorSubject {
  def apply[T](initialValue: T)(implicit scheduler: Scheduler): BehaviorSubject[T] =
    new BehaviorSubject[T](initialValue, scheduler)

  private sealed trait State[T]
  private object State {
    case class Empty[T](cachedValue: T) extends State[T]
    case class Active[T](iterator: Array[ConnectableObserver[T]], cachedValue: T) extends State[T]
    case class Complete[T](cachedValue: T, errorThrown: Throwable = null) extends State[T]
  }
}