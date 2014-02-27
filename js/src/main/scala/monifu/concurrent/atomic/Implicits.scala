package monifu.concurrent.atomic

private[atomic] trait ImplicitsLevel1 {
  implicit def AtomicAnyBuilder[T] = new AtomicBuilder[T, AtomicAny[T]] {
    def buildInstance(initialValue: T) =
      AtomicAny(initialValue)
  }
}

private[atomic] trait ImplicitsLevel2 extends ImplicitsLevel1 {
  implicit def AtomicNumberBuilder[T : Numeric] =
    new AtomicBuilder[T, AtomicNumberAny[T]] {
      def buildInstance(initialValue: T) =
        AtomicNumberAny(initialValue)
    }
}

trait Implicits extends ImplicitsLevel2
