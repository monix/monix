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

import minitest.SimpleTestSuite

object AtomicBuilderSuite extends SimpleTestSuite {
  test("initialize AtomicChar") {
    val ref = Atomic('a')
    assertEquals(ref.get, 'a')
  }

  test("initialize AtomicBoolean") {
    val ref: AtomicBoolean = Atomic(true)
    assertEquals(ref.get, true)
  }

  test("initialize AtomicInt") {
    val ref: AtomicInt = Atomic(10)
    assertEquals(ref.get, 10)
  }

  test("initialize AtomicLong") {
    val ref: AtomicLong = Atomic(10L)
    assertEquals(ref.get, 10L)
  }

  test("initialize AtomicByte") {
    val ref: AtomicByte = AtomicByte(10.toByte)
    assertEquals(ref.get, 10.toByte)
  }

  test("initialize AtomicShort") {
    val ref: AtomicShort = AtomicShort(10.toShort)
    assertEquals(ref.get, 10.toShort)
  }

  test("initialize AtomicFloat") {
    val ref: AtomicFloat = Atomic(0.10f)
    assertEquals(ref.get, 0.10f)
  }

  test("initialize AtomicDouble") {
    val ref: AtomicDouble = Atomic(0.10)
    assertEquals(ref.get, 0.10)
  }

  test("initialize AtomicBigInt") {
    val ref: AtomicNumberAny[BigInt] = Atomic(BigInt("10"))
    assertEquals(ref.get, BigInt("10"))
  }

  test("initialize AtomicAny(AnyRef)") {
    val ref: AtomicAny[String] = Atomic("value")
    assertEquals(ref.get, "value")
  }
}
