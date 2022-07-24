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

package monix.eval

import cats.laws._
import cats.laws.discipline._
import monix.execution.exceptions.DummyException

import scala.util.{ Random, Success }

class CoevalFlatMapSuite extends BaseTestSuite {
  test("transformWith equivalence with flatMap") {
    check2 { (fa: Coeval[Int], f: Int => Coeval[Int]) =>
      fa.redeemWith(Coeval.raiseError, f) <-> fa.flatMap(f)
    }
  }

  test("transform equivalence with map") {
    check2 { (fa: Coeval[Int], f: Int => Int) =>
      fa.redeem(ex => throw ex, f) <-> fa.map(f)
    }
  }

  test("transformWith can recover") {
    val dummy = new DummyException("dummy")
    val coeval = Coeval.raiseError[Int](dummy).redeemWith(_ => Coeval.now(1), Coeval.now)
    assertEquals(coeval.runTry(), Success(1))
  }

  test("transform can recover") {
    val dummy = new DummyException("dummy")
    val coeval = Coeval.raiseError[Int](dummy).redeem(_ => 1, identity)
    assertEquals(coeval.runTry(), Success(1))
  }

  test(">> is stack safe for infinite loops") {
    def looped: Coeval[Unit] = Coeval.unit >> looped
    val _ = looped
    assert(true)
  }

  test("flatMapLoop enables loops") {
    val random = Coeval(Random.nextInt())
    val loop = random.flatMapLoop(Vector.empty[Int]) { (a, list, continue) =>
      val newList = list :+ a
      if (newList.length < 5)
        continue(newList)
      else
        Coeval.now(newList)
    }
    assertEquals(loop.apply().size, 5)
  }

  test("fa *> fb <-> fa.flatMap(_ => fb)") {
    check2 { (fa: Coeval[Int], fb: Coeval[Int]) =>
      fa *> fb <-> fa.flatMap(_ => fb)
    }
  }

  test("fa <* fb <-> fa.flatMap(a => fb.map(_ => a))") {
    check2 { (fa: Coeval[Int], fb: Coeval[Int]) =>
      fa <* fb <-> fa.flatMap(a => fb.map(_ => a))
    }
  }
}
