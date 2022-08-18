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

import cats.effect.IO
import cats.laws._
import cats.laws.discipline._
import monix.eval.{ Coeval, Task }

object IterantLiftMapSuite extends BaseTestSuite {
  test("liftMap(f) converts Iterant[Coeval, ?] to Iterant[Task, ?]") { implicit s =>
    check2 { (list: List[Int], idx: Int) =>
      val source = arbitraryListToIterant[Coeval, Int](list, idx)
      val expected = arbitraryListToIterant[Task, Int](list, idx)

      val r = source.mapK(Coeval.liftTo[Task])
      r <-> expected
    }
  }

  test("liftMap(f) converts Iterant[Task, ?] to Iterant[IO, ?]") { implicit s =>
    check2 { (list: List[Int], idx: Int) =>
      val source = arbitraryListToIterant[Task, Int](list, idx)
      val expected = arbitraryListToIterant[IO, Int](list, idx)

      val r = source.mapK(Task.liftTo[IO])
      r <-> expected
    }
  }
}
