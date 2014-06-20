package monifu.concurrent.atomic.padded

import monifu.concurrent.atomic.AtomicBuilder

trait PaddedAtomicBuilder[T, R <: Atomic[T]] extends AtomicBuilder[T, R]

object PaddedAtomicBuilder extends Implicits.Level3

private[padded] object Implicits {
  trait Level1 {
    implicit def AtomicRefBuilder[T] = new PaddedAtomicBuilder[T, AtomicAny[T]] {
      def buildInstance(initialValue: T) =
        AtomicAny(initialValue)
    }
  }

  trait Level2 extends Level1 {
    implicit def AtomicNumberBuilder[T : Numeric] =
      new PaddedAtomicBuilder[T, AtomicNumberAny[T]] {
        def buildInstance(initialValue: T) =
          AtomicNumberAny(initialValue)
      }
  }

  trait Level3 extends Level2 {
    implicit val AtomicIntBuilder =
      new PaddedAtomicBuilder[Int, AtomicInt] {
        def buildInstance(initialValue: Int) =
          AtomicInt(initialValue)
      }

    implicit val AtomicLongBuilder =
      new PaddedAtomicBuilder[Long, AtomicLong] {
        def buildInstance(initialValue: Long) =
          AtomicLong(initialValue)
      }

    implicit val AtomicBooleanBuilder =
      new PaddedAtomicBuilder[Boolean, AtomicBoolean] {
        def buildInstance(initialValue: Boolean) =
          AtomicBoolean(initialValue)
      }

    implicit val AtomicByteBuilder =
      new PaddedAtomicBuilder[Byte, AtomicByte] {
        def buildInstance(initialValue: Byte): AtomicByte =
          AtomicByte(initialValue)
      }

    implicit val AtomicCharBuilder =
      new PaddedAtomicBuilder[Char, AtomicChar] {
        def buildInstance(initialValue: Char): AtomicChar =
          AtomicChar(initialValue)
      }

    implicit val AtomicShortBuilder =
      new PaddedAtomicBuilder[Short, AtomicShort] {
        def buildInstance(initialValue: Short): AtomicShort =
          AtomicShort(initialValue)
      }

    implicit val AtomicFloatBuilder =
      new PaddedAtomicBuilder[Float, AtomicFloat] {
        def buildInstance(initialValue: Float): AtomicFloat =
          AtomicFloat(initialValue)
      }

    implicit val AtomicDoubleBuilder =
      new PaddedAtomicBuilder[Double, AtomicDouble] {
        def buildInstance(initialValue: Double): AtomicDouble =
          AtomicDouble(initialValue)
      }
  }
}

