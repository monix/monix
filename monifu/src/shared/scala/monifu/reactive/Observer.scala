package monifu.reactive

import scala.concurrent.Future
import monifu.reactive.api.Ack

/**
 * The Observer from the Rx pattern is the trio of callbacks that
 * get subscribed to an Observable for receiving events.
 *
 * The events received must follow the Rx grammar, which is:
 *      onNext *   (onComplete | onError)?
 *
 * That means an Observer can receive zero or multiple events, the stream
 * ending either in one or zero `onComplete` or `onError` (just one, not both),
 * and after onComplete or onError, a well behaved Observable implementation
 * shouldn't send any more onNext events.
 */
trait Observer[-T] {
  def onNext(elem: T): Future[Ack]

  def onError(ex: Throwable): Future[Ack.Done]

  def onComplete(): Future[Ack.Done]
}
