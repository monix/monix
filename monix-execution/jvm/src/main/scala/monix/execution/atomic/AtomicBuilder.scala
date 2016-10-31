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
trait AtomicBuilder[T, R <: Atomic[T]] extends Serializable {
  def buildInstance(
    initialValue: T,
    padding: PaddingStrategy,
    allowPlatformIntrinsics: Boolean): R
}

private[atomic] object Implicits {
  abstract class Level1 {
    implicit def AtomicRefBuilder[T <: AnyRef] = new AtomicBuilder[T, AtomicAny[T]] {
      def buildInstance(initialValue: T, padding: PaddingStrategy, allowPlatformIntrinsics: Boolean) =
        AtomicAny.create(initialValue, padding, allowPlatformIntrinsics)
    }
  }

  abstract class Level2 extends Level1 {
    implicit def AtomicNumberBuilder[T <: AnyRef : Numeric] =
      new AtomicBuilder[T, AtomicNumberAny[T]] {
        def buildInstance(initialValue: T, padding: PaddingStrategy, allowPlatformIntrinsics: Boolean) =
          AtomicNumberAny.create(initialValue, padding, allowPlatformIntrinsics)(implicitly[Numeric[T]])
      }
  }
}

object AtomicBuilder extends Implicits.Level2 {
  implicit object AtomicIntBuilder extends AtomicBuilder[Int, AtomicInt] {
    def buildInstance(initialValue: Int, padding: PaddingStrategy, allowPlatformIntrinsics: Boolean): AtomicInt =
      AtomicInt.create(initialValue, padding, allowPlatformIntrinsics)
  }

  implicit object AtomicLongBuilder extends AtomicBuilder[Long, AtomicLong] {
    def buildInstance(initialValue: Long, padding: PaddingStrategy, allowPlatformIntrinsics: Boolean): AtomicLong =
      AtomicLong.create(initialValue, padding, allowPlatformIntrinsics)
  }

  implicit object AtomicBooleanBuilder extends AtomicBuilder[Boolean, AtomicBoolean] {
    def buildInstance(initialValue: Boolean, padding: PaddingStrategy, allowPlatformIntrinsics: Boolean) =
      AtomicBoolean.create(initialValue, padding, allowPlatformIntrinsics)
  }

  implicit object AtomicByteBuilder extends AtomicBuilder[Byte, AtomicByte] {
    def buildInstance(initialValue: Byte, padding: PaddingStrategy, allowPlatformIntrinsics: Boolean): AtomicByte =
      AtomicByte.create(initialValue, padding, allowPlatformIntrinsics)
  }

  implicit object AtomicCharBuilder extends AtomicBuilder[Char, AtomicChar] {
    def buildInstance(initialValue: Char, padding: PaddingStrategy, allowPlatformIntrinsics: Boolean): AtomicChar =
      AtomicChar.create(initialValue, padding, allowPlatformIntrinsics)
  }

  implicit object AtomicShortBuilder extends AtomicBuilder[Short, AtomicShort] {
    def buildInstance(initialValue: Short, padding: PaddingStrategy, allowPlatformIntrinsics: Boolean): AtomicShort =
      AtomicShort.create(initialValue, padding, allowPlatformIntrinsics)
  }

  implicit object AtomicFloatBuilder extends AtomicBuilder[Float, AtomicFloat] {
    def buildInstance(initialValue: Float, padding: PaddingStrategy, allowPlatformIntrinsics: Boolean): AtomicFloat =
      AtomicFloat.create(initialValue, padding, allowPlatformIntrinsics)
  }

  implicit object AtomicDoubleBuilder extends AtomicBuilder[Double, AtomicDouble] {
    def buildInstance(initialValue: Double, padding: PaddingStrategy, allowPlatformIntrinsics: Boolean): AtomicDouble =
      AtomicDouble.create(initialValue, padding, allowPlatformIntrinsics)
  }
}
