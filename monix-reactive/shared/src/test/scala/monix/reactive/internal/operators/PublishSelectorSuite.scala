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

package monix.reactive.internal.operators

import monix.execution.atomic.Atomic
import monix.reactive.{BaseTestSuite, Observable}
import scala.util.Success

object PublishSelectorSuite extends BaseTestSuite {
  test("publishSelector sanity test") { implicit s =>
    val isStarted = Atomic(0)
    val f = Observable.range(0, 1000)
      .doOnStart(_ => isStarted.increment())
      .publishSelector { source => Observable.merge(source, source, source) }
      .sumL
      .runAsync

    s.tick()
    assertEquals(f.value, Some(Success(500 * 999 * 3)))
    assertEquals(isStarted.get, 1)
  }

  test("treating Stop event") { implicit s =>
    val isStarted = Atomic(0)
    val isCanceled = Atomic(false)

    val f = Observable.range(0, 10000)
      .doOnStart(_ => isStarted.increment())
      .doOnSubscriptionCancel(() => isCanceled.set(true))
      .publishSelector { source => source.map(_ => 1) }
      .take(2000)
      .sumL
      .runAsync

    s.tick()
    assertEquals(f.value, Some(Success(2000)))
    assertEquals(isStarted.get, 1)
    assert(isCanceled.get, "isCanceled")
  }
}
