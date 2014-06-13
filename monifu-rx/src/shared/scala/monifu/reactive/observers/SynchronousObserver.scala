package monifu.reactive.observers

import monifu.reactive.Observer
import monifu.reactive.api.Ack

/**
 * A `SyncObserver` is an [[Observer]] that signals demand
 * to upstream synchronously (i.e. the upstream observable doesn't need to
 * wait on a `Future` in order to decide whether to send the next event
 * or not).
 * 
 * Can be used for optimizations.
 */
trait SynchronousObserver[-T] extends Observer[T] {
  /**
   * Returns either a [[monifu.reactive.api.Ack.Continue Continue]] or a
   * [[monifu.reactive.api.Ack.Cancel Cancel]], in response to an `elem` event
   * being received.
   */
  def onNext(elem: T): Ack
}

