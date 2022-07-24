/*
 * Copyright (c) 2014-2022 Monix Contributors.
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
import monix.eval.{ Coeval, Task }
import monix.execution.atomic.Atomic
import monix.execution.exceptions.DummyException
import monix.execution.internal.Platform
import org.scalacheck.Test
import org.scalacheck.Test.Parameters

import scala.concurrent.duration._
import scala.util.Success

class IterantZipMapSuite extends BaseTestSuite {
  override lazy val checkConfig: Parameters = {
    if (Platform.isJVM)
      Test.Parameters.default.withMaxSize(256)
    else
      Test.Parameters.default.withMaxSize(32)
  }

  fixture.test("Iterant.zipMap equivalence with List.zip") { implicit s =>
    check5 { (list1: List[Int], idx1: Int, list2: List[Int], idx2: Int, f: (Int, Int) => Long) =>
      val stream1 = arbitraryListToIterant[Coeval, Int](list1, math.abs(idx1) + 1, allowErrors = false)
      val stream2 = arbitraryListToIterant[Coeval, Int](list2, math.abs(idx2) + 1, allowErrors = false)

      val received = stream1.zipMap(stream2)(f).toListL
      val expected = Coeval(list1.zip(list2).map { case (a, b) => f(a, b) })
      received <-> expected
    }
  }

  fixture.test("Iterant.zipMap protects against user error") { implicit s =>
    check2 { (s1: Iterant[Task, Int], s2: Iterant[Task, Int]) =>
      val dummy = DummyException("dummy")
      val f = (_: Int, _: Int) => (throw dummy): Long
      val suffix = Iterant[Task].now(1)
      val stream1 = s1.onErrorIgnore ++ suffix
      val stream2 = s2.onErrorIgnore ++ suffix
      val received = stream1.zipMap(stream2)(f).toListL
      received <-> Task.raiseError(dummy)
    }
  }

  fixture.test("Iterant.parZip equivalence with List.zip") { implicit s =>
    check4 { (list1: List[Int], idx1: Int, list2: List[Int], idx2: Int) =>
      val stream1 = arbitraryListToIterant[Task, Int](list1, math.abs(idx1) + 1, allowErrors = false)
      val stream2 = arbitraryListToIterant[Task, Int](list2, math.abs(idx2) + 1, allowErrors = false)

      val received = stream1.parZip(stream2).toListL
      val expected = Task.eval(list1.zip(list2))
      received <-> expected
    }
  }

  fixture.test("Iterant.zip does not process in parallel") { implicit s =>
    val stream1 = Iterant[Task].suspend(Task.eval(Iterant[Task].pure(1)).delayExecution(1.second))
    val stream2 = Iterant[Task].suspend(Task.eval(Iterant[Task].pure(2)).delayExecution(1.second))
    val task = stream1.zip(stream2).headOptionL

    val f = task.runToFuture
    assertEquals(f.value, None)

    s.tick(1.second)
    assertEquals(f.value, None)

    s.tick(1.second)
    assertEquals(f.value, Some(Success(Some((1, 2)))))
  }

  fixture.test("Iterant.parZip can process in parallel") { implicit s =>
    val stream1 = Iterant[Task].suspend(Task.eval(Iterant[Task].pure(1)).delayExecution(1.second))
    val stream2 = Iterant[Task].suspend(Task.eval(Iterant[Task].pure(2)).delayExecution(1.second))
    val task = stream1.parZip(stream2).headOptionL

    val f = task.runToFuture
    assertEquals(f.value, None)

    s.tick(1.second)
    assertEquals(f.value, Some(Success(Some((1, 2)))))
  }

  test("Iterant.zip can cope with Scope") {
    val stream = {
      val triggered = Atomic(false)
      val fail = DummyException("fail")

      val lh = Iterant[Coeval].scopeS[Unit, Int](
        Coeval.unit,
        _ => Coeval(Iterant.pure(1)),
        (_, _) => Coeval(triggered.set(true))
      )

      val scope = Iterant[Coeval].concatS(
        Coeval(lh),
        Coeval {
          if (!triggered.getAndSet(true))
            Iterant[Coeval].raiseError[Int](fail)
          else
            Iterant[Coeval].empty[Int]
        }
      )

      0 +: scope :+ 2
    }

    assertEquals(
      stream.zip(stream).toListL.value(),
      List((0, 0), (1, 1), (2, 2))
    )
  }
}
