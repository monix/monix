/*
 * Copyright (c) 2014-2017 by its authors. Some rights reserved.
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

import monix.eval.Task
import monix.execution.exceptions.DummyException
import org.scalacheck.Test.Parameters

object IterantFilterSuite extends BaseTestSuite {
  override lazy val checkConfig: Parameters =
    super.checkConfig.withMaxSize(64)

  test("Iterant.filter <=> List.filter") { implicit s =>
    check2 { (stream: Iterant[Task, Int], p: Int => Boolean) =>
      val received = stream.filter(p).toListL
      val expected = stream.toListL.map(_.filter(p))
      received === expected
    }
  }

  test("Iterant.filter protects against user error") { implicit s =>
    check1 { (stream: Iterant[Task, Int]) =>
      val dummy = DummyException("dummy")
      val received = (stream ++ Iterant[Task].now(1)).filter(_ => throw dummy)
      received === Iterant[Task].raiseError(dummy)
    }
  }

  test("Iterant.filter flatMap equivalence") { implicit s =>
    check2 { (stream: Iterant[Task, Int], p: Int => Boolean) =>
      val received = stream.filter(p)
      val expected = stream.flatMap(x => if (p(x)) Iterant[Task].now(x) else Iterant[Task].empty)
      received === expected
    }
  }
}
