/*
 * Copyright (c) 2014-2017 by The Monix Project Developers.
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

import monix.eval.{Coeval, Task}
import monix.execution.cancelables.BooleanCancelable
import monix.execution.exceptions.DummyException
import monix.execution.internal.Platform
import monix.tail.batches.BatchCursor
import org.scalacheck.Test
import org.scalacheck.Test.Parameters

object IterantZipMapSuite extends BaseTestSuite {
  override lazy val checkConfig: Parameters = {
    if (Platform.isJVM)
      Test.Parameters.default.withMaxSize(256)
    else
      Test.Parameters.default.withMaxSize(32)
  }

  test("Iterant.zipMap equivalence with List.zip") { implicit s =>
    check5 { (list1: List[Int], idx1: Int, list2: List[Int], idx2: Int, f: (Int, Int) => Long) =>
      val stream1 = arbitraryListToIterant[Coeval, Int](list1, math.abs(idx1) + 1, allowErrors = false)
      val stream2 = arbitraryListToIterant[Coeval, Int](list2, math.abs(idx2) + 1, allowErrors = false)

      val received = stream1.zipMap(stream2)(f).toListL
      val expected = Coeval(list1.zip(list2).map { case (a,b) => f(a,b) })
      received <-> expected
    }
  }

  test("Iterant.zipMap protects against user error") { implicit s =>
    check2{ (s1: Iterant[Task, Int], s2: Iterant[Task, Int]) =>
      val dummy = DummyException("dummy")
      val f = (_: Int, _: Int) => (throw dummy) : Long
      val suffix = Iterant[Task].now(1)
      val stream1 = s1.onErrorIgnore ++ suffix
      val stream2 = s2.onErrorIgnore ++ suffix
      val received = stream1.zipMap(stream2)(f).toListL
      received <-> Task.raiseError(dummy)
    }
  }

  test("Iterant.zipMap triggers early stop on user error") { implicit s =>
    check3 { (s1: Iterant[Task, Int], s2: Iterant[Task, Int], idx: Int) =>
      val dummy = DummyException("dummy")
      val f = (_: Int, _: Int) => (throw dummy) : Long

      val suffix = math.abs(idx % 3) match {
        case 0 => Iterant[Task].fromIterable(List(1,2,3))
        case 1 => Iterant[Task].fromIterator(List(1,2,3).iterator)
        case 2 => Iterant[Task].nextS(1, Task.now(Iterant[Task].now(2)), Task.unit)
      }

      val c1 = BooleanCancelable()
      val stream1 = (s1.onErrorIgnore ++ suffix).doOnEarlyStop(Task.eval(c1.cancel()))
      val c2 = BooleanCancelable()
      val stream2 = (s2.onErrorIgnore ++ suffix).doOnEarlyStop(Task.eval(c2.cancel()))

      stream1.zipMap(stream2)(f).toListL.runAsync
      s.tick()

      c1.isCanceled && c2.isCanceled
    }
  }

  test("Iterant.zipMap preserves the source earlyStop") { implicit s =>
    var effect = 0
    val stop = Coeval.eval(effect += 1)
    val source1 = Iterant[Coeval].nextCursorS(BatchCursor(1,2,3), Coeval.now(Iterant[Coeval].empty[Int]), stop)
    val source2 = Iterant[Coeval].nextCursorS(BatchCursor(1,2,3), Coeval.now(Iterant[Coeval].empty[Int]), stop)
    val stream = source1.zip(source2)
    stream.earlyStop.value
    assertEquals(effect, 2)
  }
}
