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

import minitest.SimpleTestSuite
import monix.execution.Scheduler.Implicits.global
import java.util.concurrent.TimeUnit

object IOSimpleTest extends SimpleTestSuite {
  def testEffect(name: String)(f: => IO[Unit]): Unit =
    testAsync(name)(f.unsafeRunToFuture())

  testEffect("simple flatMap") {
    for {
      x1 <- IO.pure(1)
      x2 <- IO.pure(2)
      x3 <- IO.pure(3)
      x4 <- IO.async0[Int] { (sc, cb) =>
        sc.execute(() => cb.onSuccess(4))
      }
      x5 <- IO.delay(5)
      x6 <- IO.cont0[Int, Int] { (sc, cb, get) =>
        IO.delay {
          sc.scheduleOnce(1, TimeUnit.SECONDS, () => cb.onSuccess(3 + 3))
        }.flatMap { _ =>
          get
        }
      }
      x7 <- IO.delay(7)
      x8 <- IO.cont0[Int, Int] { (sc, cb, get) =>
        cb.onSuccess(8)
        get
      }
    } yield {
      assertEquals(
        x1 + x2 + x3 + x4 + x5 + x6 + x7 + x8,
        (1 to 8).sum
      )
    }
  }
}
