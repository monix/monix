/*
 * Copyright (c) 2014-2021 by The Monix Project Developers.
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
import cats.syntax.eq._
import monix.eval.{ Coeval, Task }
import monix.execution.atomic.Atomic
import monix.execution.exceptions.DummyException
import monix.execution.internal.Platform
import monix.tail.batches.{ Batch, BatchCursor }

object IterantRepeatSuite extends BaseTestSuite {
  test("Iterant.repeat works for one item") { _ =>
    val count = if (Platform.isJVM) 5000 else 500
    val r = Iterant[Coeval].pure(1).repeat.take(count).sumL.value()
    assertEquals(r, count)
  }

  test("Iterant.repeat works for many many") { _ =>
    val count = if (Platform.isJVM) 10 else 500

    check2 { (list: List[Int], index: Int) =>
      val fa = arbitraryListToIterant[Coeval, Int](list, index, allowErrors = false).repeat
        .take(count)

      val expected =
        if (list.isEmpty) Iterant.empty[Coeval, Int]
        else {
          Iterant[Coeval].fromIterable(
            (0 until (count / list.length + 1))
              .flatMap(_ => list)
              .take(count)
          )
        }

      fa <-> expected
    }
  }

  test("Iterant.repeat terminates on exception") { implicit s =>
    var effect = 0
    var values = List[Int]()
    val expectedValues = List.fill(6)(1)
    val dummy = DummyException("dummy")
    val source = Iterant[Coeval].nextS(1, Coeval(Iterant[Coeval].empty[Int]))

    intercept[DummyException] {
      source.repeat.map { x =>
        if (effect == 6) throw dummy
        else {
          effect += 1
          values ::= x
          x
        }
      }.toListL.value()
      ()
    }

    assertEquals(values, expectedValues)
  }

  test("Iterant.repeat protects against broken batches") { implicit s =>
    check1 { (iter: Iterant[Task, Int]) =>
      val dummy = DummyException("dummy")
      val suffix = Iterant[Task].nextBatchS[Int](new ThrowExceptionBatch(dummy), Task.now(Iterant[Task].empty))
      val stream = iter.onErrorIgnore ++ suffix
      val received = stream.repeat
      received <-> iter.onErrorIgnore ++ Iterant[Task].haltS[Int](Some(dummy))
    }
  }

  test("Iterant.repeat protects against broken cursors") { implicit s =>
    check1 { (iter: Iterant[Task, Int]) =>
      val dummy = DummyException("dummy")
      val suffix = Iterant[Task].nextCursorS[Int](new ThrowExceptionCursor(dummy), Task.now(Iterant[Task].empty))
      val stream = iter.onErrorIgnore ++ suffix
      val received = stream.repeat
      received <-> iter.onErrorIgnore ++ Iterant[Task].haltS[Int](Some(dummy))
    }
  }

  test("Iterant.repeat terminates streams that end in error") { _ =>
    check1 { (stream: Iterant[Coeval, Int]) =>
      val dummy = DummyException("dummy")
      val fa = stream ++ Iterant[Coeval].raiseError[Int](dummy)
      fa.repeat <-> fa
    }
  }

  test("Iterant.repeat terminates if the source is empty") { implicit s =>
    val source1 = Iterant[Coeval].empty[Int]
    val source2 = Iterant[Coeval].suspendS(Coeval(source1))
    val source3 = Iterant[Coeval].nextCursorS[Int](BatchCursor(), Coeval(source2))
    val source4 = Iterant[Coeval].nextBatchS[Int](Batch.empty[Int], Coeval(source3))

    assertEquals(source1.repeat.toListL.value(), List.empty[Int])
    assertEquals(source2.repeat.toListL.value(), List.empty[Int])
    assertEquals(source3.repeat.toListL.value(), List.empty[Int])
    assertEquals(source4.repeat.toListL.value(), List.empty[Int])
  }

  test("Iterant.repeat discards Scopes properly") { implicit s =>
    val acquired = Atomic(0)
    val sum = Iterant
      .resource(Coeval(acquired.incrementAndGet()))(_ => Coeval(acquired.decrement()))
      .repeat
      .take(10)
      .sumL
      .value()

    assertEquals(sum, 10)
  }

  test("Iterant.repeatEval captures effects") { _ =>
    check1 { (xs: Vector[Int]) =>
      val iterator = xs.iterator
      val evaluated = Iterant[Coeval]
        .repeatEval(iterator.next())
        .take(xs.length)

      evaluated <-> Iterant[Coeval].fromIterator(xs.iterator)
    }
  }

  test("Iterant.repeatEval terminates on exceptions") { _ =>
    val dummy = DummyException("dummy")
    val xs = Iterant[Coeval].repeatEval[Int] {
      throw dummy
    }
    assert(xs === Iterant[Coeval].raiseError(dummy))
  }

  test("Iterant.repeatEvalF repeats effectful values") { _ =>
    val repeats = 66
    var effect = 0
    val increment = Coeval { effect += 1 }
    Iterant[Coeval].repeatEvalF(increment).take(repeats).completedL.value()
    assertEquals(effect, repeats)
  }

  test("Iterant.repeatEvalF terminates on exceptions raised in F") { _ =>
    val dummy = DummyException("dummy")
    val xs = Iterant[Coeval].repeatEvalF(Coeval.raiseError[Int](dummy))

    assert(xs === Iterant[Coeval].raiseError(dummy))
  }
}
