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

package monix.eval

import cats.laws._
import cats.laws.discipline._

object CoevalSequenceSuite extends BaseTestSuite {
  test("Coeval.sequence") { implicit s =>
    check1 { (numbers: List[Int]) =>
      val coeval = Coeval.sequence(numbers.map(x => Coeval(x)))
      coeval <-> Coeval(numbers)
    }
  }

  test("Coeval.traverse") { implicit s =>
    check1 { (numbers: List[Int]) =>
      val coeval = Coeval.traverse(numbers)(x => Coeval(x))
      coeval <-> Coeval(numbers)
    }
  }

  test("Coeval.zipList") { implicit s =>
    check1 { (numbers: List[Int]) =>
      val coeval = Coeval.zipList(numbers.map(x => Coeval(x)):_*)
      coeval <-> Coeval(numbers)
    }
  }
}
