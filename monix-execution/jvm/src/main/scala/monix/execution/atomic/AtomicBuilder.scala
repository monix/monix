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
  def buildInstance(initialValue: T, padding: PaddingStrategy): R
}

private[atomic] object Implicits {
  abstract class Level1 {
    implicit def AtomicRefBuilder[T <: AnyRef] = new AtomicBuilder[T, AtomicAny[T]] {
      def buildInstance(initialValue: T, padding: PaddingStrategy) =
        AtomicAny.withPadding(initialValue, padding)
    }
  }

  abstract class Level2 extends Level1 {
    implicit def AtomicNumberBuilder[T <: AnyRef : Numeric] =
      new AtomicBuilder[T, AtomicNumberAny[T]] {
        def buildInstance(initialValue: T, padding: PaddingStrategy) =
          AtomicNumberAny.withPadding(initialValue, padding)(implicitly[Numeric[T]])
      }
  }
}

object AtomicBuilder extends Implicits.Level2 {
  implicit object AtomicIntBuilder extends AtomicBuilder[Int, AtomicInt] {
    def buildInstance(initialValue: Int, padding: PaddingStrategy): AtomicInt =
      AtomicInt.withPadding(initialValue, padding)
  }

  implicit object AtomicLongBuilder extends AtomicBuilder[Long, AtomicLong] {
    def buildInstance(initialValue: Long, padding: PaddingStrategy): AtomicLong =
      AtomicLong.withPadding(initialValue, padding)
  }

  implicit object AtomicBooleanBuilder extends AtomicBuilder[Boolean, AtomicBoolean] {
    def buildInstance(initialValue: Boolean, padding: PaddingStrategy) =
      AtomicBoolean.withPadding(initialValue, padding)
  }

  implicit object AtomicByteBuilder extends AtomicBuilder[Byte, AtomicByte] {
    def buildInstance(initialValue: Byte, padding: PaddingStrategy): AtomicByte =
      AtomicByte.withPadding(initialValue, padding)
  }

  implicit object AtomicCharBuilder extends AtomicBuilder[Char, AtomicChar] {
    def buildInstance(initialValue: Char, padding: PaddingStrategy): AtomicChar =
      AtomicChar.withPadding(initialValue, padding)
  }

  implicit object AtomicShortBuilder extends AtomicBuilder[Short, AtomicShort] {
    def buildInstance(initialValue: Short, padding: PaddingStrategy): AtomicShort =
      AtomicShort.withPadding(initialValue, padding)
  }

  implicit object AtomicFloatBuilder extends AtomicBuilder[Float, AtomicFloat] {
    def buildInstance(initialValue: Float, padding: PaddingStrategy): AtomicFloat =
      AtomicFloat.withPadding(initialValue, padding)
  }

  implicit object AtomicDoubleBuilder extends AtomicBuilder[Double, AtomicDouble] {
    def buildInstance(initialValue: Double, padding: PaddingStrategy): AtomicDouble =
      AtomicDouble.withPadding(initialValue, padding)
  }
}
