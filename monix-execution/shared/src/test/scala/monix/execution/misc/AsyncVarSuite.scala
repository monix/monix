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

package monix.execution.misc

import minitest.SimpleTestSuite
import monix.execution.atomic.PaddingStrategy.LeftRight128
import scala.util.Success

object AsyncVarSuite extends SimpleTestSuite {
  test("empty; put; take; put; take") {
    val av = AsyncVar.empty[Int]

    val r1 = av.put(10)
    assertEquals(r1.value, Some(Success(())))
    val r2 = av.take
    assertEquals(r2.value, Some(Success(10)))

    val r3 = av.put(20)
    assertEquals(r3.value, Some(Success(())))
    val r4 = av.take
    assertEquals(r4.value, Some(Success(20)))
  }

  test("empty; take; put; take; put") {
    val av = AsyncVar.empty[Int]

    val take1 = av.take
    assertEquals(take1.value, None)
    val put1 = av.put(10)
    assertEquals(put1.value, Some(Success(())))
    assertEquals(take1.value, Some(Success(10)))

    val take2 = av.take
    assertEquals(take2.value, None)
    val put2 = av.put(20)
    assertEquals(put2.value, Some(Success(())))
    assertEquals(take2.value, Some(Success(20)))
  }

  test("empty; put; put; put; take; take; take") {
    val av = AsyncVar.empty[Int]

    val put1 = av.put(10)
    assertEquals(put1.value, Some(Success(())))
    val put2 = av.put(20)
    assertEquals(put2.value, None)
    val put3 = av.put(30)
    assertEquals(put3.value, None)

    val take1 = av.take
    assertEquals(take1.value, Some(Success(10)))
    assertEquals(put2.value, Some(Success(())))
    assertEquals(put3.value, None)

    val take2 = av.take
    assertEquals(take2.value, Some(Success(20)))
    assertEquals(put3.value, Some(Success(())))

    val take3 = av.take
    assertEquals(take3.value, Some(Success(30)))
  }

  test("empty; take; take; take; put; put; put") {
    val av = AsyncVar.empty[Int]

    val take1 = av.take
    assertEquals(take1.value, None)
    val take2 = av.take
    assertEquals(take2.value, None)
    val take3 = av.take
    assertEquals(take3.value, None)

    val put1 = av.put(10)
    assertEquals(put1.value, Some(Success(())))
    assertEquals(take1.value, Some(Success(10)))

    val put2 = av.put(20)
    assertEquals(put2.value, Some(Success(())))
    assertEquals(take2.value, Some(Success(20)))

    val put3 = av.put(30)
    assertEquals(put3.value, Some(Success(())))
    assertEquals(take3.value, Some(Success(30)))
  }

  test("initial; take; take; put") {
    val av = AsyncVar(10)

    val take1 = av.take
    assertEquals(take1.value, Some(Success(10)))
    val take2 = av.take
    assertEquals(take2.value, None)

    val put1 = av.put(20)
    assertEquals(put1.value, Some(Success(())))
    assertEquals(take2.value, Some(Success(20)))
  }

  test("empty; read; put; take") {
    val av = AsyncVar.empty[Int]

    val read1 = av.read
    assertEquals(read1.value, None)

    val put1 = av.put(10)
    assertEquals(put1.value, Some(Success(())))
    assertEquals(read1.value, Some(Success(10)))

    val take1 = av.take
    assertEquals(take1.value, Some(Success(10)))
  }

  test("empty; put; read; take") {
    val av = AsyncVar.empty[Int]

    val put1 = av.put(10)
    assertEquals(put1.value, Some(Success(())))

    val read1 = av.read
    assertEquals(read1.value, Some(Success(10)))

    val take1 = av.take
    assertEquals(take1.value, Some(Success(10)))
  }

  test("initial; read; take") {
    val av = AsyncVar(10)

    val read1 = av.read
    assertEquals(read1.value, Some(Success(10)))

    val take1 = av.take
    assertEquals(take1.value, Some(Success(10)))
  }

  test("withPadding; put; take; put; take") {
    val av = AsyncVar.withPadding[Int](LeftRight128)

    val r1 = av.put(10)
    assertEquals(r1.value, Some(Success(())))
    val r2 = av.take
    assertEquals(r2.value, Some(Success(10)))

    val r3 = av.put(20)
    assertEquals(r3.value, Some(Success(())))
    val r4 = av.take
    assertEquals(r4.value, Some(Success(20)))
  }

  test("withPadding(initial); put; take; put; take") {
    val av = AsyncVar.withPadding[Int](10, LeftRight128)

    val r2 = av.take
    assertEquals(r2.value, Some(Success(10)))
    val r3 = av.put(20)
    assertEquals(r3.value, Some(Success(())))
    val r4 = av.take
    assertEquals(r4.value, Some(Success(20)))
  }
  
  test("put(null) throws NullPointerException") {
    val av = AsyncVar.empty[String]
    intercept[NullPointerException] {
      av.put(null)
    }
  }
}
