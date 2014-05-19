package monifu.concurrent.atomic

/**
 * Provided for source-level compatibility with the JVM version. There is no difference between
 * functionality imported from this package and `monifu.concurrent.atomic`.
 */
package object padded {
  type AtomicFloat = AtomicNumberAny[Float]

  object AtomicFloat {
    def apply(initial: Float): AtomicFloat =
      AtomicNumberAny(initial)
  }

  type AtomicDouble = AtomicNumberAny[Double]

  object AtomicDouble {
    def apply(initial: Double): AtomicDouble  =
      AtomicNumberAny(initial)
  }

  type AtomicShort = AtomicNumberAny[Short]

  object AtomicShort {
    def apply(initial: Short): AtomicShort  =
      AtomicNumberAny(initial)
  }

  type AtomicChar = AtomicNumberAny[Char]

  object AtomicChar {
    def apply(initial: Char): AtomicChar  =
      AtomicNumberAny(initial)
  }

  type AtomicBoolean = AtomicAny[Boolean]

  object AtomicBoolean {
    def apply(initial: Boolean): AtomicBoolean  =
      AtomicAny(initial)
  }

  type AtomicInt = AtomicNumberAny[Int]

  object AtomicInt {
    def apply(initial: Int): AtomicInt  =
      AtomicNumberAny(initial)
  }

  type AtomicLong = AtomicNumberAny[Long]

  object AtomicLong {
    def apply(initial: Long): AtomicLong  =
      AtomicNumberAny(initial)
  }

  type AtomicByte = AtomicNumberAny[Byte]

  object AtomicByte {
    def apply(initial: Byte): AtomicByte  =
      AtomicNumberAny(initial)
  }
}
