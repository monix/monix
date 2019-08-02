/*
 * Copyright (c) 2014-2019 by The Monix Project Developers.
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

package monix.reactive.internal.operators

import monix.execution.Ack.Continue
import monix.reactive.{BaseTestSuite, Observable}
object UnfoldSuite extends BaseTestSuite {

  test("should be exception-proof") { implicit s =>
    val dummy = new RuntimeException("dummy")
    var received = 0

    Observable.unfold(0)(i => if (i < 20) Some((i, i + 1)) else throw dummy).subscribe { _: Int =>
      received += 1
      Continue
    }

    assertEquals((0 until received).toList, (0 to 19).toList)
    assertEquals(s.state.lastReportedError, dummy)
  }

  test("should execute 10 times then return None") { implicit s =>
    var received = 0

    Observable.unfold(0)(i => if (i < 10) Some((i, i + 1)) else None).subscribe { _: Int =>
      received += 1
      Continue
    }

    assertEquals((0 until received).toList, (0 to 9).toList)
  }
}
