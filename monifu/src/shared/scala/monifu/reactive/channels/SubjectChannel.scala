package monifu.reactive.channels

import monifu.reactive.{Observable, Observer, Channel, Subject}
import monifu.reactive.observers.BufferedObserver
import monifu.concurrent.Scheduler

/**
 * Wraps any [[Subject]] into a [[Channel]].
 */
class SubjectChannel[-I,+O](subject: Subject[I, O], s: Scheduler) extends Channel[I] with Observable[O] {
  final implicit val scheduler = s

  private[this] val channel = BufferedObserver(subject)

  final def unsafeSubscribe(observer: Observer[O]): Unit = {
    subject.unsafeSubscribe(observer)
  }

  final def pushNext(elems: I*): Unit = {
    for (elem <- elems) channel.onNext(elem)
  }

  final def pushComplete(): Unit = {
    channel.onComplete()
  }

  final def pushError(ex: Throwable): Unit = {
    channel.onError(ex)
  }
}

object SubjectChannel {
  /**
   * Wraps any [[Subject]] into a [[Channel]].
   */
  def apply[I,O](subject: Subject[I, O])(implicit s: Scheduler): SubjectChannel[I, O] = {
    new SubjectChannel[I,O](subject, s)
  }
}
