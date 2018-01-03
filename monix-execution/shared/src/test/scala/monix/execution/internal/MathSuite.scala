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

package monix.execution.internal

import minitest.SimpleTestSuite

object MathSuite extends SimpleTestSuite {
  test("roundToPowerOf2(int)") {
    assertEquals(math.roundToPowerOf2(1), 1)
    assertEquals(math.roundToPowerOf2(2), 2)
    assertEquals(math.roundToPowerOf2(3), 4)
    assertEquals(math.roundToPowerOf2(4), 4)
    assertEquals(math.roundToPowerOf2(5), 4)
    assertEquals(math.roundToPowerOf2(1023), 1024)
    assertEquals(math.roundToPowerOf2(1200), 1024)
    assertEquals(math.roundToPowerOf2(Int.MaxValue), 1 << 30)
  }

  test("roundToPowerOf2(long)") {
    assertEquals(math.roundToPowerOf2(1L), 1)
    assertEquals(math.roundToPowerOf2(2L), 2)
    assertEquals(math.roundToPowerOf2(3L), 4)
    assertEquals(math.roundToPowerOf2(4L), 4)
    assertEquals(math.roundToPowerOf2(5L), 4)
    assertEquals(math.roundToPowerOf2(1023L), 1024)
    assertEquals(math.roundToPowerOf2(1200L), 1024)
    assertEquals(math.roundToPowerOf2(Int.MaxValue.toLong), 1L << 31)
  }

  test("nextPowerOf2(int)") {
    assertEquals(math.nextPowerOf2(1), 1)
    assertEquals(math.nextPowerOf2(2), 2)
    assertEquals(math.nextPowerOf2(3), 4)
    assertEquals(math.nextPowerOf2(4), 4)
    assertEquals(math.nextPowerOf2(5), 8)
    assertEquals(math.nextPowerOf2(1023), 1024)
    assertEquals(math.nextPowerOf2(1200), 2048)
    assertEquals(math.nextPowerOf2(Int.MaxValue), 1 << 30)
  }

  test("nextPowerOf2(long)") {
    assertEquals(math.nextPowerOf2(1L), 1)
    assertEquals(math.nextPowerOf2(2L), 2)
    assertEquals(math.nextPowerOf2(3L), 4)
    assertEquals(math.nextPowerOf2(4L), 4)
    assertEquals(math.nextPowerOf2(5L), 8)
    assertEquals(math.nextPowerOf2(1023L), 1024)
    assertEquals(math.nextPowerOf2(1200L), 2048)
    assertEquals(math.nextPowerOf2(Int.MaxValue.toLong), 1L << 31)
  }
}
