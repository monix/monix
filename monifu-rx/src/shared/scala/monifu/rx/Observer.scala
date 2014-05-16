package monifu.rx

import monifu.rx.api.Ack

/**
 * The Observer from the Rx pattern is the trio of callbacks that
 * get subscribed to an Observable for receiving events.
 *
 * `Observer` is the synchronous version, whereas [[AsyncObserver]]
 * is the asynchronous version.
 *
 * The events received must follow the Rx grammar, which is:
 *      onNext *   (onCompleted | onError)?
 *
 * That means an Observer can receive zero or multiple events, the stream
 * ending either in one or zero `onCompleted` or `onError` (just one, not both),
 * and after onCompleted or onError, a well behaved Observable implementation
 * shouldn't send any more onNext events.
 */
trait Observer[-T] {
  def onNext(elem: T): Ack

  def onError(ex: Throwable): Unit

  def onCompleted(): Unit
}
