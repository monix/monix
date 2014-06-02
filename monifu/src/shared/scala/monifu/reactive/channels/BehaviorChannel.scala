package monifu.reactive.channels

import monifu.reactive.{Observable, Observer, Channel}
import monifu.reactive.observers.BufferedObserver
import monifu.concurrent.Scheduler
import monifu.reactive.subjects.BehaviorSubject

/**
 * A `BehaviorChannel` is a [[Channel]] that uses an underlying
 * [[monifu.reactive.subjects.BehaviorSubject BehaviorSubject]].
 */
final class BehaviorChannel[T] private (initialValue: T, s: Scheduler) extends Channel[T] with Observable[T] {
  implicit val scheduler = s

  private[this] val subject = BehaviorSubject(initialValue)
  private[this] val channel = BufferedObserver(subject)

  private[this] var isDone = false
  private[this] var lastValue = initialValue
  private[this] var errorThrown = null : Throwable

  def unsafeSubscribe(observer: Observer[T]): Unit = {
    subject.unsafeSubscribe(observer)
  }

  def pushNext(elems: T*): Unit = synchronized {
    if (!isDone)
      for (elem <- elems) {
        lastValue = elem
        channel.onNext(elem)
      }
  }

  def pushComplete() = synchronized {
    if (!isDone) {
      isDone = true
      channel.onComplete()
    }
  }

  def pushError(ex: Throwable) = synchronized {
    if (!isDone) {
      isDone = true
      errorThrown = ex
      channel.onError(ex)
    }
  }

  def :=(update: T): Unit = pushNext(update)

  def apply(): T = synchronized {
    if (errorThrown ne null)
      throw errorThrown
    else
      lastValue
  }
}

object BehaviorChannel {
  def apply[T](initial: T)(implicit s: Scheduler): BehaviorChannel[T] =
    new BehaviorChannel[T](initial, s)
}
