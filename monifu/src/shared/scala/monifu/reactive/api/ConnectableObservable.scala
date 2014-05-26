package monifu.reactive.api

import monifu.reactive.Observable
import monifu.concurrent.cancelables.BooleanCancelable

/**
 * Represents an [[Observable]] that waits for the call to `connect()` before
 * starting to emit elements to its subscriber(s).
 *
 * Useful for converting cold observables into hot observables and thus returned by
 * [[Observable.multicast]].
 */
trait ConnectableObservable[+T] extends Observable[T] {
  /**
   * Starts emitting events to subscribers.
   */
  def connect(): BooleanCancelable
}
