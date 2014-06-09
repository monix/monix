package monifu.reactive

/**
 * A channel is meant for imperative style feeding of events.
 *
 * When emitting events, one doesn't need to follow the back-pressure contract.
 * On the other hand the grammar must still be respected:
 *
 *     (pushNext)* (pushComplete | pushError)
 */
trait Channel[-I] { self =>
  /**
   * Push the given events down the stream.
   */
  def pushNext(elem: I*): Unit

  /**
   * End the stream.
   */
  def pushComplete(): Unit

  /**
   * Ends the stream with an error.
   */
  def pushError(ex: Throwable): Unit
}
