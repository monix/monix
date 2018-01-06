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
import monix.eval.Task
import monix.execution.exceptions.DummyException
import monix.execution.internal.Platform
import org.scalacheck.Test
import org.scalacheck.Test.Parameters

object IterantSkipSuspendSuite extends BaseTestSuite {
  override lazy val checkConfig: Parameters = {
    if (Platform.isJVM)
      Test.Parameters.default.withMaxSize(256)
    else
      Test.Parameters.default.withMaxSize(32)
  }

  test("Iterant[Task].skipSuspend mirrors the source") { implicit s =>
    check2 { (list: List[Int], idx: Int) =>
      val stream = arbitraryListToIterant[Task, Int](list, math.abs(idx) + 1)
      Iterant[Task].suspend(stream.skipSuspendL) <-> stream
    }
  }

  test("Iterant.skipSuspend protects against broken batches") { implicit s =>
    check1 { (iter: Iterant[Task, Int]) =>
      val dummy = DummyException("dummy")
      val suffix = Iterant[Task].nextBatchS[Int](new ThrowExceptionBatch(dummy), Task.now(Iterant[Task].empty), Task.unit)
      val stream = iter.onErrorIgnore ++ suffix
      val received = stream.skipSuspendL
      Iterant[Task].suspend(received) <-> Iterant[Task].haltS[Int](Some(dummy))
    }
  }

  test("Iterant.skipSuspend protects against broken cursors") { implicit s =>
    check1 { (iter: Iterant[Task, Int]) =>
      val dummy = DummyException("dummy")
      val suffix = Iterant[Task].nextCursorS[Int](new ThrowExceptionCursor(dummy), Task.now(Iterant[Task].empty), Task.unit)
      val stream = iter.onErrorIgnore ++ suffix
      val received = stream.skipSuspendL
      Iterant[Task].suspend(received) <-> Iterant[Task].haltS[Int](Some(dummy))
    }
  }
}
