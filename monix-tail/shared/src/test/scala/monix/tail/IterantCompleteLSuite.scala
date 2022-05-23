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
import monix.eval.Coeval
import monix.execution.atomic.Atomic
import monix.execution.exceptions.DummyException
import scala.util.Failure

object IterantCompleteLSuite extends BaseTestSuite {
  test("completedL works") { implicit s =>
    check1 { (iter: Iterant[Coeval, Int]) =>
      var effect = 0
      val trigger = iter.onErrorIgnore ++ Iterant[Coeval].suspend {
        effect += 1
        Iterant[Coeval].empty[Int]
      }

      val fa = trigger.completedL *> Coeval.eval(effect)
      fa <-> Coeval.now(1)
    }
  }

  test("BatchCursor.completedL protects against errors") { implicit s =>
    val dummy = DummyException("dummy")
    val cursor = ThrowExceptionCursor[Int](dummy)
    var earlyStop = false

    val fa = Iterant[Coeval]
      .resource(Coeval.unit)(_ => Coeval { earlyStop = true })
      .flatMap(_ => Iterant[Coeval].fromBatchCursor(cursor))

    assertEquals(fa.completedL.runTry(), Failure(dummy))
    assert(earlyStop, "earlyStop")
  }

  test("Batch.completedL protects against errors") { implicit s =>
    val dummy = DummyException("dummy")
    val batch = ThrowExceptionBatch[Int](dummy)
    var earlyStop = false

    val fa = Iterant[Coeval]
      .resource(Coeval.unit)(_ => Coeval { earlyStop = true })
      .flatMap(_ => Iterant[Coeval].fromBatch(batch))

    assertEquals(fa.completedL.runTry(), Failure(dummy))
    assert(earlyStop, "earlyStop")
  }

  test("resource gets released for failing `rest` on Next node") { implicit s =>
    var effect = 0

    def stop(i: Int): Coeval[Unit] =
      Coeval { effect += i }

    val dummy = DummyException("dummy")
    val node3 = Iterant[Coeval].nextS(3, Coeval.raiseError(dummy)).guarantee(stop(3))
    val node2 = Iterant[Coeval].nextS(2, Coeval(node3)).guarantee(stop(2))
    val node1 = Iterant[Coeval].nextS(1, Coeval(node2)).guarantee(stop(1))

    assertEquals(node1.completedL.runTry(), Failure(dummy))
    assertEquals(effect, 6)
  }

  test("completedL handles Scope's release before the rest of the stream") { implicit s =>
    val triggered = Atomic(false)
    val fail = DummyException("fail")

    val lh = Iterant[Coeval].scopeS[Unit, Int](
      Coeval.unit,
      _ => Coeval(Iterant.pure(1)),
      (_, _) => Coeval(triggered.set(true))
    )

    val stream = Iterant[Coeval].concatS(
      Coeval(lh),
      Coeval {
        if (!triggered.getAndSet(true))
          Iterant[Coeval].raiseError[Int](fail)
        else
          Iterant[Coeval].empty[Int]
      }
    )

    assertEquals(stream.completedL.value(), ())
  }

  test("completedL handles Scope's release after use is finished") { implicit s =>
    val triggered = Atomic(false)
    val fail = DummyException("fail")

    val stream = Iterant[Coeval].scopeS[Unit, Int](
      Coeval.unit,
      _ =>
        Coeval(1 +: Iterant[Coeval].suspend {
          if (triggered.getAndSet(true))
            Iterant[Coeval].raiseError[Int](fail)
          else
            Iterant[Coeval].empty[Int]
        }),
      (_, _) => {
        Coeval(triggered.set(true))
      }
    )
    assertEquals((0 +: stream :+ 2).completedL.value(), ())
  }
}
