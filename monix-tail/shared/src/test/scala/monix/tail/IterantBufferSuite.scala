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

package monix.tail

import cats.laws._
import cats.laws.discipline._
import monix.eval.Coeval
import monix.execution.exceptions.DummyException
import scala.util.Failure

object IterantBufferSuite extends BaseTestSuite {
  test("bufferTumbling(c) is consistent with List.sliding(c, c)") { implicit s =>
    check3 { (list: List[Int], idx: Int, num: Int) =>
      val count = math.abs(num % 4) + 2
      val stream = arbitraryListToIterant[Coeval, Int](list, idx, allowErrors = false)

      stream.bufferTumbling(count).map(_.toList).toListL <->
        Coeval(list.sliding(count, count).toList)
    }
  }

  test("bufferSliding(c, s) is consistent with List.sliding(c, s)") { implicit s =>
    check4 { (list: List[Int], idx: Int, c: Int, s: Int) =>
      val count = math.abs(c % 4) + 2
      val skip = math.abs(s % 4) + 2
      val stream = arbitraryListToIterant[Coeval, Int](list, idx, allowErrors = false)

      stream.bufferSliding(count, skip).map(_.toList).toListL <->
        Coeval(list.sliding(count, skip).toList)
    }
  }

  test("source.batched <-> source") { implicit s =>
    check2 { (stream: Iterant[Coeval, Int], c: Int) =>
      val count = math.abs(c % 4) + 2
      stream.batched(count) <-> stream
    }
  }

  test("protect against broken batches") { implicit s =>
    val dummy = DummyException("dummy")
    var effect = 0

    val stream = Iterant[Coeval].nextBatchS[Int](ThrowExceptionBatch(dummy), Coeval(Iterant[Coeval].empty), Coeval.unit)
      .doOnEarlyStop(Coeval { effect += 1 })
      .bufferTumbling(10)

    assertEquals(effect, 0)
    assertEquals(stream.toListL.runTry, Failure(dummy))
    assertEquals(effect, 1)
  }

  test("protect against broken cursors") { implicit s =>
    val dummy = DummyException("dummy")
    var effect = 0

    val stream = Iterant[Coeval].nextCursorS[Int](ThrowExceptionCursor(dummy), Coeval(Iterant[Coeval].empty), Coeval.unit)
      .doOnEarlyStop(Coeval { effect += 1 })
      .bufferTumbling(10)

    assertEquals(effect, 0)
    assertEquals(stream.toListL.runTry, Failure(dummy))
    assertEquals(effect, 1)
  }
}
