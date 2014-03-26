package monifu.concurrent.atomic

trait AtomicBuilder[T, R <: Atomic[T]] {
  def buildInstance(initialValue: T): R
}

object AtomicBuilder extends Implicits