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


/**
 * Provided for source-level compatibility with the JVM version. There is no difference between
 * functionality imported from this package and `monifu.concurrent.atomic`.
 */
package object padded {
  type AtomicFloat = padded.AtomicNumberAny[Float]

  object AtomicFloat {
    def apply(initial: Float): AtomicFloat =
      padded.AtomicNumberAny(initial)
  }

  type AtomicDouble = padded.AtomicNumberAny[Double]

  object AtomicDouble {
    def apply(initial: Double): AtomicDouble  =
      padded.AtomicNumberAny(initial)
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

  type AtomicInt = padded.AtomicNumberAny[Int]

  object AtomicInt {
    def apply(initial: Int): AtomicInt  =
      padded.AtomicNumberAny(initial)
  }

  type AtomicLong = padded.AtomicNumberAny[Long]

  object AtomicLong {
    def apply(initial: Long): AtomicLong  =
      padded.AtomicNumberAny(initial)
  }

  type AtomicByte = padded.AtomicNumberAny[Byte]

  object AtomicByte {
    def apply(initial: Byte): AtomicByte  =
      padded.AtomicNumberAny(initial)
  }
}
