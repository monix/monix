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
    strategy: PaddingStrategy,
    allowPlatformIntrinsics: Boolean): R

  def buildSafeInstance(
    initialValue: A,
    padding: PaddingStrategy): R
}

private[atomic] object Implicits {
  abstract class Level1 {
    /** Provides an [[AtomicBuilder]] instance for [[AtomicAny]]. */
    implicit def AtomicRefBuilder[A <: AnyRef]: AtomicBuilder[A, AtomicAny[A]] =
      new AtomicBuilder[A, AtomicAny[A]] {
        def buildInstance(initialValue: A, strategy: PaddingStrategy, allowPlatformIntrinsics: Boolean) =
          AtomicAny(initialValue)
        def buildSafeInstance(initialValue: A, strategy: PaddingStrategy) =
          AtomicAny(initialValue)
      }
  }

  abstract class Level2 extends Level1 {
    /** Provides an [[AtomicBuilder]] instance for [[AtomicNumberAny]]. */
    implicit def AtomicNumberBuilder[A  <: AnyRef : Numeric]: AtomicBuilder[A, AtomicNumberAny[A]] =
      new AtomicBuilder[A, AtomicNumberAny[A]] {
        def buildInstance(initialValue: A, strategy: PaddingStrategy, allowPlatformIntrinsics: Boolean) =
          AtomicNumberAny(initialValue)
        def buildSafeInstance(initialValue: A, strategy: PaddingStrategy) =
          AtomicNumberAny(initialValue)
      }
  }
}

object AtomicBuilder extends Implicits.Level2 {
  /** Provides an [[AtomicBuilder]] instance for [[AtomicInt]]. */
  implicit val AtomicIntBuilder: AtomicBuilder[Int, AtomicInt] =
    new AtomicBuilder[Int, AtomicInt] {
      def buildInstance(initialValue: Int, strategy: PaddingStrategy, allowPlatformIntrinsics: Boolean) =
        AtomicInt(initialValue)
      def buildSafeInstance(initialValue: Int, strategy: PaddingStrategy) =
        AtomicInt(initialValue)
    }

  /** Provides an [[AtomicBuilder]] instance for [[AtomicLong]]. */
  implicit val AtomicLongBuilder: AtomicBuilder[Long, AtomicLong] =
    new AtomicBuilder[Long, AtomicLong] {
      def buildInstance(initialValue: Long, strategy: PaddingStrategy, allowPlatformIntrinsics: Boolean) =
        AtomicLong(initialValue)
      def buildSafeInstance(initialValue: Long, strategy: PaddingStrategy) =
        AtomicLong(initialValue)
    }

  /** Provides an [[AtomicBuilder]] instance for [[AtomicBoolean]]. */
  implicit val AtomicBooleanBuilder: AtomicBuilder[Boolean, AtomicBoolean] =
    new AtomicBuilder[Boolean, AtomicBoolean] {
      def buildInstance(initialValue: Boolean, strategy: PaddingStrategy, allowPlatformIntrinsics: Boolean) =
        AtomicBoolean(initialValue)
      def buildSafeInstance(initialValue: Boolean, strategy: PaddingStrategy) =
        AtomicBoolean(initialValue)
    }

  /** Provides an [[AtomicBuilder]] instance for [[AtomicByte]]. */
  implicit val AtomicByteBuilder: AtomicBuilder[Byte, AtomicByte] =
    new AtomicBuilder[Byte, AtomicByte] {
      def buildInstance(initialValue: Byte, strategy: PaddingStrategy, allowPlatformIntrinsics: Boolean): AtomicByte =
        AtomicByte(initialValue)
      def buildSafeInstance(initialValue: Byte, strategy: PaddingStrategy): AtomicByte =
        AtomicByte(initialValue)
    }

  /** Provides an [[AtomicBuilder]] instance for [[AtomicChar]]. */
  implicit val AtomicCharBuilder: AtomicBuilder[Char, AtomicChar] =
    new AtomicBuilder[Char, AtomicChar] {
      def buildInstance(initialValue: Char, strategy: PaddingStrategy, allowPlatformIntrinsics: Boolean): AtomicChar =
        AtomicChar(initialValue)
      def buildSafeInstance(initialValue: Char, strategy: PaddingStrategy): AtomicChar =
        AtomicChar(initialValue)
    }

  /** Provides an [[AtomicBuilder]] instance for [[AtomicShort]]. */
  implicit val AtomicShortBuilder: AtomicBuilder[Short, AtomicShort] =
    new AtomicBuilder[Short, AtomicShort] {
      def buildInstance(initialValue: Short, strategy: PaddingStrategy, allowPlatformIntrinsics: Boolean): AtomicShort =
        AtomicShort(initialValue)
      def buildSafeInstance(initialValue: Short, strategy: PaddingStrategy): AtomicShort =
        AtomicShort(initialValue)
    }

  /** Provides an [[AtomicBuilder]] instance for [[AtomicFloat]]. */
  implicit val AtomicFloatBuilder: AtomicBuilder[Float, AtomicFloat] =
    new AtomicBuilder[Float, AtomicFloat] {
      def buildInstance(initialValue: Float, strategy: PaddingStrategy, allowPlatformIntrinsics: Boolean): AtomicFloat =
        AtomicFloat(initialValue)
      def buildSafeInstance(initialValue: Float, strategy: PaddingStrategy): AtomicFloat =
        AtomicFloat(initialValue)
    }

  /** Provides an [[AtomicBuilder]] instance for [[AtomicDouble]]. */
  implicit val AtomicDoubleBuilder: AtomicBuilder[Double, AtomicDouble] =
    new AtomicBuilder[Double, AtomicDouble] {
      def buildInstance(initialValue: Double, strategy: PaddingStrategy, allowPlatformIntrinsics: Boolean): AtomicDouble =
        AtomicDouble(initialValue)
      def buildSafeInstance(initialValue: Double, strategy: PaddingStrategy): AtomicDouble =
        AtomicDouble(initialValue)
    }
}
