/*
 * Copyright (c) 2014 by its authors. Some rights reserved.
 * See the project homepage at
 *
 *     http://www.monifu.org/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  	http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
 
package monifu.concurrent.atomic

import monifu.concurrent.atomic

/**
 * Provided for source-level compatibility with the JVM version. There is no difference between
 * functionality imported from this package and `monifu.concurrent.atomic`.
 */
package object padded {
  type AtomicAny[T] = atomic.AtomicAny[T]

  object AtomicAny {
    def apply[T](initialValue: T): AtomicAny[T] =
      atomic.AtomicAny(initialValue)
  }

  type AtomicNumberAny[T] = atomic.AtomicNumberAny[T]

  object AtomicNumberAny {
    def apply[T : Numeric](initialValue: T): AtomicNumberAny[T] = {
      atomic.AtomicNumberAny(initialValue)
    }
  }

  type AtomicShort = padded.AtomicNumberAny[Short]

  object AtomicShort {
    def apply(initial: Short): AtomicShort  =
      padded.AtomicNumberAny(initial)
  }

  type AtomicChar = padded.AtomicNumberAny[Char]

  object AtomicChar {
    def apply(initial: Char): AtomicChar  =
      padded.AtomicNumberAny(initial)
  }

  type AtomicBoolean = padded.AtomicAny[Boolean]

  object AtomicBoolean {
    def apply(initial: Boolean): AtomicBoolean  =
      padded.AtomicAny(initial)
  }

  type AtomicInt = atomic.AtomicInt

  object AtomicInt {
    def apply(initial: Int): AtomicInt  =
      atomic.AtomicInt(initial)
  }

  type AtomicLong = atomic.AtomicLong

  object AtomicLong {
    def apply(initial: Long): AtomicLong  =
      atomic.AtomicLong(initial)
  }

  type AtomicFloat = atomic.AtomicFloat

  object AtomicFloat {
    def apply(initial: Float): AtomicFloat  =
      atomic.AtomicFloat(initial)
  }

  type AtomicDouble = atomic.AtomicDouble

  object AtomicDouble {
    def apply(initial: Double): AtomicDouble  =
      atomic.AtomicDouble(initial)
  }

  type AtomicByte = padded.AtomicNumberAny[Byte]

  object AtomicByte {
    def apply(initial: Byte): AtomicByte  =
      padded.AtomicNumberAny(initial)
  }
}
