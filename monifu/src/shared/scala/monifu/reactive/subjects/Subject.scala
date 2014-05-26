package monifu.reactive.subjects

import monifu.reactive.{Observer, Observable}

/**
 * A `Subject` is a sort of bridge or proxy that acts both as an
 * [[Observer]] and as an [[Observable]]. Because it is a `Observer`,
 * it can to an `Observable` and because it is an `Observable`, it can pass through the
 * items it observes by re-emitting them and it can also emit new items.
 */
trait Subject[-I, +O] extends Observable[O] with Observer[I]
