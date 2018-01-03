/*
 * Copyright (c) 2014-2018 by The Monix Project Developers.
 * See the project homepage at: https://monix.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package monix.execution.atomic

/** For a given `A` indicates the most specific `Atomic[A]`
  * reference type to use.
  *
  * In essence this is implementing a form of specialization
  * driven by implicits.
  */
trait AtomicBuilder[A, R <: Atomic[A]] extends Serializable {
  def buildInstance(
    initialValue: A,
    padding: PaddingStrategy,
    allowPlatformIntrinsics: Boolean): R

  def buildSafeInstance(
    initialValue: A,
    padding: PaddingStrategy): R
}

private[atomic] object Implicits {
  abstract class Level1 {
    /** Provides an [[AtomicBuilder]] instance for [[AtomicAny]]. */
    implicit def AtomicRefBuilder[A <: AnyRef] = new AtomicBuilder[A, AtomicAny[A]] {
      def buildInstance(initialValue: A, padding: PaddingStrategy, allowPlatformIntrinsics: Boolean) =
        AtomicAny.create(initialValue, padding, allowPlatformIntrinsics)

      def buildSafeInstance(initialValue: A, padding: PaddingStrategy) =
        AtomicAny.safe(initialValue, padding)
    }
  }

  abstract class Level2 extends Level1 {
    /** Provides an [[AtomicBuilder]] instance for [[AtomicNumberAny]]. */
    implicit def AtomicNumberBuilder[A <: AnyRef : Numeric] =
      new AtomicBuilder[A, AtomicNumberAny[A]] {
        def buildInstance(initialValue: A, padding: PaddingStrategy, allowPlatformIntrinsics: Boolean) =
          AtomicNumberAny.create(initialValue, padding, allowPlatformIntrinsics)(implicitly[Numeric[A]])

        def buildSafeInstance(initialValue: A, padding: PaddingStrategy) =
          AtomicNumberAny.safe(initialValue, padding)(implicitly[Numeric[A]])
      }
  }
}

object AtomicBuilder extends Implicits.Level2 {
  /** Provides an [[AtomicBuilder]] instance for [[AtomicInt]]. */
  implicit object AtomicIntBuilder extends AtomicBuilder[Int, AtomicInt] {
    def buildInstance(initialValue: Int, padding: PaddingStrategy, allowPlatformIntrinsics: Boolean): AtomicInt =
      AtomicInt.create(initialValue, padding, allowPlatformIntrinsics)

    def buildSafeInstance(initialValue: Int, padding: PaddingStrategy): AtomicInt =
      AtomicInt.safe(initialValue, padding)
  }

  /** Provides an [[AtomicBuilder]] instance for [[AtomicLong]]. */
  implicit object AtomicLongBuilder extends AtomicBuilder[Long, AtomicLong] {
    def buildInstance(initialValue: Long, padding: PaddingStrategy, allowPlatformIntrinsics: Boolean): AtomicLong =
      AtomicLong.create(initialValue, padding, allowPlatformIntrinsics)

    def buildSafeInstance(initialValue: Long, padding: PaddingStrategy): AtomicLong =
      AtomicLong.safe(initialValue, padding)
  }

  /** Provides an [[AtomicBuilder]] instance for [[AtomicBoolean]]. */
  implicit object AtomicBooleanBuilder extends AtomicBuilder[Boolean, AtomicBoolean] {
    def buildInstance(initialValue: Boolean, padding: PaddingStrategy, allowPlatformIntrinsics: Boolean) =
      AtomicBoolean.create(initialValue, padding, allowPlatformIntrinsics)

    def buildSafeInstance(initialValue: Boolean, padding: PaddingStrategy) =
      AtomicBoolean.safe(initialValue, padding)
  }

  /** Provides an [[AtomicBuilder]] instance for [[AtomicByte]]. */
  implicit object AtomicByteBuilder extends AtomicBuilder[Byte, AtomicByte] {
    def buildInstance(initialValue: Byte, padding: PaddingStrategy, allowPlatformIntrinsics: Boolean): AtomicByte =
      AtomicByte.create(initialValue, padding, allowPlatformIntrinsics)

    def buildSafeInstance(initialValue: Byte, padding: PaddingStrategy): AtomicByte =
      AtomicByte.safe(initialValue, padding)
  }

  /** Provides an [[AtomicBuilder]] instance for [[AtomicChar]]. */
  implicit object AtomicCharBuilder extends AtomicBuilder[Char, AtomicChar] {
    def buildInstance(initialValue: Char, padding: PaddingStrategy, allowPlatformIntrinsics: Boolean): AtomicChar =
      AtomicChar.create(initialValue, padding, allowPlatformIntrinsics)

    def buildSafeInstance(initialValue: Char, padding: PaddingStrategy): AtomicChar =
      AtomicChar.safe(initialValue, padding)
  }

  /** Provides an [[AtomicBuilder]] instance for [[AtomicShort]]. */
  implicit object AtomicShortBuilder extends AtomicBuilder[Short, AtomicShort] {
    def buildInstance(initialValue: Short, padding: PaddingStrategy, allowPlatformIntrinsics: Boolean): AtomicShort =
      AtomicShort.create(initialValue, padding, allowPlatformIntrinsics)

    def buildSafeInstance(initialValue: Short, padding: PaddingStrategy): AtomicShort =
      AtomicShort.safe(initialValue, padding)
  }

  /** Provides an [[AtomicBuilder]] instance for [[AtomicFloat]]. */
  implicit object AtomicFloatBuilder extends AtomicBuilder[Float, AtomicFloat] {
    def buildInstance(initialValue: Float, padding: PaddingStrategy, allowPlatformIntrinsics: Boolean): AtomicFloat =
      AtomicFloat.create(initialValue, padding, allowPlatformIntrinsics)

    def buildSafeInstance(initialValue: Float, padding: PaddingStrategy): AtomicFloat =
      AtomicFloat.safe(initialValue, padding)
  }

  /** Provides an [[AtomicBuilder]] instance for [[AtomicDouble]]. */
  implicit object AtomicDoubleBuilder extends AtomicBuilder[Double, AtomicDouble] {
    def buildInstance(initialValue: Double, padding: PaddingStrategy, allowPlatformIntrinsics: Boolean): AtomicDouble =
      AtomicDouble.create(initialValue, padding, allowPlatformIntrinsics)

    def buildSafeInstance(initialValue: Double, padding: PaddingStrategy): AtomicDouble =
      AtomicDouble.safe(initialValue, padding)
  }
}
