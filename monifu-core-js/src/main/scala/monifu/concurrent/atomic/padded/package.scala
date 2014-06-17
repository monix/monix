/*
 * Copyright (c) 2014 by its authors. Some rights reserved. 
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
