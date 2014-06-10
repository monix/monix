package monifu.reactive.channels

import monifu.reactive.{Observable, Observer, Channel, Subject}
import monifu.reactive.observers.BufferedObserver
import monifu.concurrent.Scheduler
import monifu.reactive.api.BufferPolicy
import monifu.reactive.api.BufferPolicy.Unbounded

/**
 * Wraps any [[Subject]] into a [[Channel]].
 */
class SubjectChannel[-I,+O](subject: Subject[I, O], policy: BufferPolicy, s: Scheduler) extends Channel[I] with Observable[O] {
  final implicit val scheduler = s
  private[this] val channel = BufferedObserver(subject, policy)

  final def subscribeFn(observer: Observer[O]): Unit = {
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
  def apply[I,O](subject: Subject[I, O], bufferPolicy: BufferPolicy = Unbounded)(implicit s: Scheduler): SubjectChannel[I, O] = {
    new SubjectChannel[I,O](subject, bufferPolicy, s)
  }
}
