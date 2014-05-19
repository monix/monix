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
  sealed trait Continue extends Ack

  object Continue extends Continue {
    val asFuture = Future.successful(Continue)
  }

  /**
   * Acknowledgement or processing that signals upstream that the
   * consumer is no longer interested in receiving events.
   */
  sealed trait Done extends Ack

  object Done extends Done {
    val asFuture = Future.successful(Done)
  }  
}






