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

private[atomic] trait ImplicitsLevel3 extends ImplicitsLevel2 {
  implicit val AtomicIntBuilder: AtomicBuilder[Int, AtomicInt] =
    new AtomicBuilder[Int, AtomicInt] {
      def buildInstance(initialValue: Int) =
        AtomicInt(initialValue)
    }

  implicit val AtomicLongBuilder: AtomicBuilder[Long, AtomicLong] =
    new AtomicBuilder[Long, AtomicLong] {
      def buildInstance(initialValue: Long) =
        AtomicLong(initialValue)
    }

  implicit val AtomicBooleanBuilder: AtomicBuilder[Boolean, AtomicBoolean] =
    new AtomicBuilder[Boolean, AtomicBoolean] {
      def buildInstance(initialValue: Boolean) =
        AtomicBoolean(initialValue)
    }

  implicit val AtomicByteBuilder: AtomicBuilder[Byte, AtomicByte] =
    new AtomicBuilder[Byte, AtomicByte] {
      def buildInstance(initialValue: Byte) =
        AtomicByte(initialValue)
    }

  implicit val AtomicCharBuilder: AtomicBuilder[Char, AtomicChar] =
    new AtomicBuilder[Char, AtomicChar] {
      def buildInstance(initialValue: Char) =
        AtomicChar(initialValue)
    }

  implicit val AtomicShortBuilder: AtomicBuilder[Short, AtomicShort] =
    new AtomicBuilder[Short, AtomicShort] {
      def buildInstance(initialValue: Short) =
        AtomicShort(initialValue)
    }

  implicit val AtomicFloatBuilder: AtomicBuilder[Float, AtomicFloat] =
    new AtomicBuilder[Float, AtomicFloat] {
      def buildInstance(initialValue: Float) =
        AtomicFloat(initialValue)
    }

  implicit val AtomicDoubleBuilder: AtomicBuilder[Double, AtomicDouble] =
    new AtomicBuilder[Double, AtomicDouble] {
      def buildInstance(initialValue: Double) =
        AtomicDouble(initialValue)
    }
}

trait Implicits extends ImplicitsLevel3

