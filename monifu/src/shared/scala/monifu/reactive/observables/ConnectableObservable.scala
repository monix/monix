package monifu.reactive.observables

import monifu.reactive.cancelables.BooleanCancelable

/**
 * Represents an [[monifu.reactive.Observable Observable]] that waits for
 * the call to `connect()` before
 * starting to emit elements to its subscriber(s).
 *
 * Useful for converting cold observables into hot observables and thus returned by
 * [[monifu.reactive.Observable.multicast Observable.multicast]].
 */
trait ConnectableObservable[+T] extends GenericObservable[T] {
  /**
   * Starts emitting events to subscribers.
   */
  def connect(): BooleanCancelable
}
