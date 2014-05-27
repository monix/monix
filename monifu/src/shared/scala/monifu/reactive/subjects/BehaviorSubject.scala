package monifu.reactive.subjects

import scala.concurrent.{Promise, Future}
import monifu.reactive.api.Ack
import monifu.reactive.api.Ack.{Continue, Done}
import monifu.concurrent.Scheduler
import monifu.reactive.Observer
import monifu.reactive.internals.PromiseCounter
import monifu.concurrent.atomic.padded.Atomic
import scala.annotation.tailrec
import scala.collection.immutable.Set
import scala.util.{Failure, Success}


final class BehaviorSubject3[T] private (initialValue: T, s: Scheduler) extends Subject[T,T] { self =>
  import BehaviorSubject3.State
  import BehaviorSubject3.State._

  implicit val scheduler = s
  private[this] val state = Atomic(Empty(initialValue) : State[T])

  def subscribeFn(observer: Observer[T]): Unit = {
    @tailrec
    def loop(newAck: Future[Continue]): (Future[Continue], T, Throwable, Boolean) = {
      state.get match {
        case current @ Empty(cachedValue) =>
          if (!state.compareAndSet(current, Active(Set(observer), cachedValue, newAck)))
            loop(newAck)
          else
            (Continue, cachedValue, null, false)

        case current @ Active(observers, cachedValue, lastAck) =>
          if (!state.compareAndSet(current, Active(observers + observer, cachedValue, newAck)))
            loop(newAck)
          else
            (lastAck, cachedValue, null, false)

        case current @ Complete(cachedValue, errorThrown, lastAck) =>
          if (!state.compareAndSet(current, Complete(cachedValue, errorThrown, newAck)))
            loop(newAck)
          else
            (lastAck, cachedValue, errorThrown, true)
      }
    }

    val p = Promise[Continue]()
    val (ack, value, ex, shouldComplete) = loop(p.future)

    if (ex != null) {
      try observer.onError(ex) finally p.success(Continue)
    }
    else
      ack.onComplete(_ =>
        observer.onNext(value).onComplete {
          case Success(Continue) =>
            try {
              if (shouldComplete)
                observer.onComplete()
            }
            finally {
              p.success(Continue)
            }
          case Success(Done) =>
            removeSubscription(observer)
            p.success(Continue)
          case Failure(err) =>
            removeSubscription(observer)
            try observer.onError(err) finally p.success(Continue)
        }
      )
  }

  @tailrec
  def onNext(elem: T): Future[Ack] = {
    state.get match {
      case current @ Empty(_) =>
        if (!state.compareAndSet(current, Empty(elem)))
          onNext(elem)
        else
          Continue

      case current @ Active(observers, cachedValue, lastAck) =>
        val p = PromiseCounter[Continue](Continue, observers.size)
        if (!state.compareAndSet(current, Active(observers, elem, p.future)))
          onNext(elem)
        else {
          lastAck.onComplete { _ =>
            for (obs <- observers)
              obs.onNext(elem).onComplete {
                case Success(Continue) =>
                  p.countdown()
                case Success(Done) =>
                  removeSubscription(obs)
                  p.countdown()
                case Failure(ex) =>
                  removeSubscription(obs)
                  try obs.onError(ex) finally p.countdown()
              }
          }

          p.future
        }

      case _ =>
        Done
    }
  }

  @tailrec
  def onComplete(): Unit =
    state.get match {
      case current @ Empty(cachedValue) =>
        if (!state.compareAndSet(current, Complete(cachedValue, null, Continue)))
          onComplete()

      case current @ Active(observers, cachedValue, lastAck) =>
        if (!state.compareAndSet(current, Complete(cachedValue, null, lastAck)))
          onComplete()
        else
          lastAck.onComplete { _ =>
            for (obs <- observers)
              obs.onComplete()
          }

      case _ => // already complete, ignore
    }

  @tailrec
  def onError(ex: Throwable): Unit =
    state.get match {
      case current @ Empty(cachedValue) =>
        if (!state.compareAndSet(current, Complete(cachedValue, ex, Continue)))
          onError(ex)

      case current @ Active(observers, cachedValue, lastAck) =>
        if (!state.compareAndSet(current, Complete(cachedValue, ex, lastAck)))
          onError(ex)
        else
          lastAck.onComplete { _ =>
            for (obs <- observers)
              obs.onError(ex)
          }

      case _ => // already complete, ignore
    }

  private[this] def removeSubscription(obs: Observer[T]): Unit =
    state.transform {
      case current @ Active(observers,_,_) => current.copy(observers - obs)
      case other => other
    }
}

object BehaviorSubject3 {
  def apply[T](initialValue: T)(implicit scheduler: Scheduler): BehaviorSubject3[T] =
    new BehaviorSubject3[T](initialValue, scheduler)

  private sealed trait State[T]
  private object State {
    case class Empty[T](cachedValue: T) extends State[T]
    case class Active[T](observers: Set[Observer[T]], cachedValue: T, lastAck: Future[Continue]) extends State[T]
    case class Complete[T](cachedValue: T, errorThrown: Throwable = null, lastAck: Future[Continue]) extends State[T]
  }
}