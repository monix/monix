package monifu.reactive.subjects

import scala.concurrent.Future
import monifu.reactive.api.Ack
import monifu.reactive.api.Ack.{Continue, Done}
import monifu.concurrent.Scheduler
import monifu.reactive.Observer
import monifu.reactive.internals.PromiseCounter
import monifu.concurrent.atomic.Atomic
import scala.annotation.tailrec
import scala.collection.immutable.Set
import scala.util.Success


/**
 * A `PublishSubject` emits to a subscriber only those items that are
 * emitted by the source subsequent to the time of the subscription
 *
 * <img src="https://raw.githubusercontent.com/wiki/alexandru/monifu/assets/rx-operators/S.PublishSubject.png" />
 *
 * If the source terminates with an error, the `PublishSubject` will not emit any
 * items to subsequent subscribers, but will simply pass along the error
 * notification from the source Observable.
 *
 * <img src="https://raw.githubusercontent.com/wiki/alexandru/monifu/assets/rx-operators/S.PublishSubject.e.png" />
 */
final class PublishSubject[T] private (s: Scheduler) extends Subject[T,T] { self =>
  import PublishSubject._

  implicit val scheduler = s
  private[this] val state = Atomic(Empty : State[T])
  private[this] var lastResponse = Continue : Future[Continue]

  @tailrec
  def subscribeFn(observer: Observer[T]): Unit =
    state.get match {
      case Empty =>
        if (!state.compareAndSet(Empty, Active(Set(observer))))
          subscribeFn(observer)
      case current @ Active(observers) =>
        if (!state.compareAndSet(current, Active(observers + observer)))
          subscribeFn(observer)
      case Complete(errorThrown) =>
        if (errorThrown != null)
          observer.onError(errorThrown)
        else
          observer.onComplete()
    }

  @tailrec
  private[this] def removeSubscription(observer: Observer[T]): Unit =
    state.get match {
      case current @ Active(observers) =>
        val update = observers - observer
        if (update.nonEmpty) {
          if (!state.compareAndSet(current, Active(update)))
            removeSubscription(observer)
        }
        else {
          if (!state.compareAndSet(current, Empty))
            removeSubscription(observer)
        }
      case _ => // ignore
    }

  def onNext(elem: T): Future[Ack] =
    state.get match {
      case Empty => Continue
      case Complete(_) => Done
      case Active(observers) =>
        val newPromise = PromiseCounter[Continue](Continue, observers.size)
        val newResponse = newPromise.future
        val oldResponse = lastResponse
        lastResponse = newResponse

        oldResponse.onComplete { _ =>
          val iterator = observers.iterator
          while (iterator.hasNext) {
            val obs = iterator.next()

            obs.onNext(elem).onComplete {
              case Success(Continue) =>
                newPromise.countdown()
              case _ =>
                removeSubscription(obs)
                newPromise.countdown()
            }
          }
        }

        newResponse
    }

  @tailrec
  def onError(ex: Throwable): Unit =
    state.get match {
      case _: Complete => // ignore
      case Empty =>
        if (!state.compareAndSet(Empty, Complete(ex)))
          onError(ex)
      case current @ Active(observers) =>
        if (!state.compareAndSet(current, Complete(ex)))
          onError(ex)
        else
          lastResponse.onComplete { _ =>
            val iterator = observers.iterator
            while (iterator.hasNext) {
              val obs = iterator.next()
              obs.onError(ex)
            }
          }
    }

  @tailrec
  def onComplete() =
    state.get match {
      case _: Complete => // ignore
      case Empty =>
        if (!state.compareAndSet(Empty, Complete(null)))
          onComplete()
      case current @ Active(observers) =>
        if (!state.compareAndSet(current, Complete(null)))
          onComplete()
        else
          lastResponse.onComplete { _ =>
            val iterator = observers.iterator
            while (iterator.hasNext) {
              val obs = iterator.next()
              obs.onComplete()
            }
          }
    }
}

object PublishSubject {
  def apply[T]()(implicit scheduler: Scheduler): PublishSubject[T] =
    new PublishSubject[T](scheduler)

  private sealed trait State[+T]
  private case object Empty extends State[Nothing]
  private case class Active[T](observers: Set[Observer[T]]) extends State[T]
  private case class Complete(errorThrown: Throwable = null) extends State[Nothing]
}