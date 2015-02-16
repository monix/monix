/*
 * Copyright (c) 2015 Alexandru Nedelcu
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package monifu.concurrent.atomic.padded

import monifu.concurrent.atomic

trait AtomicBuilder[T, R <: atomic.Atomic[T]] extends atomic.AtomicBuilder[T, R]

object AtomicBuilder extends Implicits.Level3

private[padded] object Implicits {
  trait Level1 {
    implicit def AtomicRefBuilder[T]: AtomicBuilder[T, AtomicAny[T]] =
      new AtomicBuilder[T, AtomicAny[T]] {
        def buildInstance(initialValue: T) =
          AtomicAny(initialValue)
      }
  }

  trait Level2 extends Level1 {
    implicit def AtomicNumberBuilder[T : Numeric]: AtomicBuilder[T, AtomicNumberAny[T]] =
      new AtomicBuilder[T, AtomicNumberAny[T]] {
        def buildInstance(initialValue: T) =
          AtomicNumberAny(initialValue)
      }
  }

  trait Level3 extends Level2 {
    implicit val AtomicIntBuilder =
      new AtomicBuilder[Int, AtomicInt] {
        def buildInstance(initialValue: Int) =
          AtomicInt(initialValue)
      }

    implicit val AtomicLongBuilder =
      new AtomicBuilder[Long, AtomicLong] {
        def buildInstance(initialValue: Long) =
          AtomicLong(initialValue)
      }

    implicit val AtomicBooleanBuilder =
      new AtomicBuilder[Boolean, AtomicBoolean] {
        def buildInstance(initialValue: Boolean) =
          AtomicBoolean(initialValue)
      }

    implicit val AtomicByteBuilder =
      new AtomicBuilder[Byte, AtomicByte] {
        def buildInstance(initialValue: Byte): AtomicByte =
          AtomicByte(initialValue)
      }

    implicit val AtomicCharBuilder =
      new AtomicBuilder[Char, AtomicChar] {
        def buildInstance(initialValue: Char): AtomicChar =
          AtomicChar(initialValue)
      }

    implicit val AtomicShortBuilder =
      new AtomicBuilder[Short, AtomicShort] {
        def buildInstance(initialValue: Short): AtomicShort =
          AtomicShort(initialValue)
      }

    implicit val AtomicFloatBuilder =
      new AtomicBuilder[Float, AtomicFloat] {
        def buildInstance(initialValue: Float): AtomicFloat =
          AtomicFloat(initialValue)
      }

    implicit val AtomicDoubleBuilder =
      new AtomicBuilder[Double, AtomicDouble] {
        def buildInstance(initialValue: Double): AtomicDouble =
          AtomicDouble(initialValue)
      }
  }
}

