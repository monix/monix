/*
 * Copyright (c) 2014-2016 by its authors. Some rights reserved.
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

import monix.execution.atomic.{Atomic, AtomicInt}
import monix.execution.internal.Platform

import scala.util.{Failure, Success, Try}

object TaskFlatMapSuite extends BaseTestSuite {
  test("runAsync flatMap loop is cancelable") { implicit s =>
    val maxCount = Platform.recommendedBatchSize * 4
    val expected = Platform.recommendedBatchSize * 2 - 1

    def loop(count: AtomicInt): Task[Unit] =
      if (count.getAndIncrement() >= maxCount) Task.unit else
        Task.unit.flatMap(_ => loop(count))

    val atomic = Atomic(0)
    val f = loop(atomic).runAsync

    assertEquals(atomic.get, Platform.recommendedBatchSize)
    f.cancel()
    s.tickOne()
    assertEquals(atomic.get, expected)

    s.tick()
    assertEquals(atomic.get, expected)
    assertEquals(f.value, None)
  }

  test("runAsync(callback) flatMap loop is cancelable") { implicit s =>
    val maxCount = Platform.recommendedBatchSize * 4
    val expected = Platform.recommendedBatchSize * 2 - 1

    def loop(count: AtomicInt): Task[Unit] =
      if (count.getAndIncrement() >= maxCount) Task.unit else
        Task.unit.flatMap(_ => loop(count))

    val atomic = Atomic(0)
    var result = Option.empty[Try[Unit]]

    val c = loop(atomic).runAsync(new Callback[Unit] {
      def onSuccess(value: Unit): Unit =
        result = Some(Success(value))
      def onError(ex: Throwable): Unit =
        result = Some(Failure(ex))
    })

    c.cancel()
    s.tickOne()
    assertEquals(atomic.get, expected)

    s.tick()
    assertEquals(atomic.get, expected)
  }
}
