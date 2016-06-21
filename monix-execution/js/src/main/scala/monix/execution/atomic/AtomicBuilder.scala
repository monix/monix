/*
 * Copyright (c) 2016 by its authors. Some rights reserved.
 * See the project homepage at: https://sincron.org
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

/** For a given `T` indicates the most specific `Atomic[T]`
  * reference type to use.
  *
  * In essence this is implementing a form of specialization
  * driven by implicits.
  */
trait AtomicBuilder[T, R <: Atomic[T]] {
  def buildInstance(initialValue: T, strategy: PaddingStrategy): R
}

private[atomic] object Implicits {
  abstract class Level1 {
    implicit def AtomicRefBuilder[T <: AnyRef]: AtomicBuilder[T, AtomicAny[T]] =
      new AtomicBuilder[T, AtomicAny[T]] {
        def buildInstance(initialValue: T, strategy: PaddingStrategy) =
          AtomicAny(initialValue)
      }
  }

  abstract class Level2 extends Level1 {
    implicit def AtomicNumberBuilder[T  <: AnyRef : Numeric]: AtomicBuilder[T, AtomicNumberAny[T]] =
      new AtomicBuilder[T, AtomicNumberAny[T]] {
        def buildInstance(initialValue: T, strategy: PaddingStrategy) =
          AtomicNumberAny(initialValue)
      }
  }
}

object AtomicBuilder extends Implicits.Level2 {
  implicit val AtomicIntBuilder: AtomicBuilder[Int, AtomicInt] =
    new AtomicBuilder[Int, AtomicInt] {
      def buildInstance(initialValue: Int, strategy: PaddingStrategy) =
        AtomicInt(initialValue)
    }

  implicit val AtomicLongBuilder: AtomicBuilder[Long, AtomicLong] =
    new AtomicBuilder[Long, AtomicLong] {
      def buildInstance(initialValue: Long, strategy: PaddingStrategy) =
        AtomicLong(initialValue)
    }

  implicit val AtomicBooleanBuilder: AtomicBuilder[Boolean, AtomicBoolean] =
    new AtomicBuilder[Boolean, AtomicBoolean] {
      def buildInstance(initialValue: Boolean, strategy: PaddingStrategy) =
        AtomicBoolean(initialValue)
    }

  implicit val AtomicByteBuilder: AtomicBuilder[Byte, AtomicByte] =
    new AtomicBuilder[Byte, AtomicByte] {
      def buildInstance(initialValue: Byte, strategy: PaddingStrategy): AtomicByte =
        AtomicByte(initialValue)
    }

  implicit val AtomicCharBuilder: AtomicBuilder[Char, AtomicChar] =
    new AtomicBuilder[Char, AtomicChar] {
      def buildInstance(initialValue: Char, strategy: PaddingStrategy): AtomicChar =
        AtomicChar(initialValue)
    }

  implicit val AtomicShortBuilder: AtomicBuilder[Short, AtomicShort] =
    new AtomicBuilder[Short, AtomicShort] {
      def buildInstance(initialValue: Short, strategy: PaddingStrategy): AtomicShort =
        AtomicShort(initialValue)
    }

  implicit val AtomicFloatBuilder: AtomicBuilder[Float, AtomicFloat] =
    new AtomicBuilder[Float, AtomicFloat] {
      def buildInstance(initialValue: Float, strategy: PaddingStrategy): AtomicFloat =
        AtomicFloat(initialValue)
    }

  implicit val AtomicDoubleBuilder: AtomicBuilder[Double, AtomicDouble] =
    new AtomicBuilder[Double, AtomicDouble] {
      def buildInstance(initialValue: Double, strategy: PaddingStrategy): AtomicDouble =
        AtomicDouble(initialValue)
    }
}
