package monifu.reactive.subjects

import scala.concurrent.Future
import monifu.reactive.api.Ack.{Continue, Done}
import monifu.concurrent.Scheduler
import monifu.reactive.Observer
import monifu.concurrent.atomic.padded.Atomic
import scala.annotation.tailrec
import monifu.reactive.api.{Ack, ConnectableObserver}


final class BehaviorSubject[T] private (initialValue: T, s: Scheduler) extends Subject[T,T] { self =>
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
          obs.scheduleFirst(cachedValue)

          if (!state.compareAndSet(current, Active(Array(obs), cachedValue)))
            loop()
          else
            obs

        case current @ Active(observers, cachedValue) =>
          val obs = new ConnectableObserver[T](observer)
          obs.scheduleFirst(cachedValue)

          if (!state.compareAndSet(current, Active(observers :+ obs, cachedValue)))
            loop()
          else
            obs

        case current @ Complete(cachedValue, errorThrown) =>
          val obs = new ConnectableObserver[T](observer)
          if (errorThrown eq null) {
            obs.scheduleFirst(cachedValue)
            obs.scheduleComplete()
          }
          else {
            obs.schedulerError(errorThrown)
          }
          obs
      }
    }

    loop().connect()
  }

  private[this] def emitNext(obs: Observer[T], elem: T): Future[Continue] =
    obs.onNext(elem) match {
      case Continue => Continue
      case Done =>
        removeSubscription(obs)
        Continue
      case other =>
        other.map {
          case Continue => Continue
          case Done =>
            removeSubscription(obs)
            Continue
        }
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

        else {
          var idx = 0
          var acc = Continue : Future[Continue]

          while (idx < observers.length) {
            val obs = observers(idx)
            acc = if (acc == Continue || (acc.isCompleted && acc.value.get.isSuccess))
              emitNext(obs, elem)
            else
              acc.flatMap(_ => emitNext(obs, elem))

            idx += 1
          }

          acc
        }

      case _ =>
        Done
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

  private[this] def removeSubscription(obs: Observer[T]): Unit =
    state.transform {
      case current @ Active(observers,_) =>
        current.copy(observers.filterNot(_ eq obs))
      case other =>
        other
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