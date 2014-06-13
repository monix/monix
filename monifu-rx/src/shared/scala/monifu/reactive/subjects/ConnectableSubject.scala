package monifu.reactive.subjects

import monifu.reactive.{ConnectableObservable, Subject}

/**
 * Represents a [[monifu.reactive.Subject Subject]] that waits for
 * the call to `connect()` before starting to emit elements to its subscriber(s).
 *
 * You can convert any `Subject` into a `ConnectableSubject` by means of
 * [[monifu.reactive.Subject.multicast Subject.multicast]].
 */
trait ConnectableSubject[-I,+O] extends ConnectableObservable[O] with Subject[I, O]
