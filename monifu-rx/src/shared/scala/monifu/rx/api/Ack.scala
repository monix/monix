package monifu.rx.api

import scala.concurrent.Future

/**
 * Represents the acknowledgement of processing that a consumer
 * sends back upstream on `Observer.onNext`
 */
sealed trait Ack

object Ack {
  /**
   * Acknowledgement of processing that signals upstream that the
   * consumer is interested in receiving more events.
   */
  case object Continue extends Ack {
    def asFuture = Future.successful(Continue)
  }

  /**
   * Acknowledgement or processing that signals upstream that the
   * consumer is no longer interested in receiving events.
   */
  case object Stop extends Ack {
    def asFuture = Future.successful(Stop)
  }
}






