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

package monix.cats

import cats.{CoflatMap, Monad, MonadError}
import minitest.SimpleTestSuite
import monix.cats.implicits._
import monix.execution.schedulers.TestScheduler
import monix.reactive.Observable
import scala.util.Success

object ObservableCatsSanitySuite extends SimpleTestSuite {
  test("Observable is Monad") {
    val ref = implicitly[Monad[Observable]]
    assert(ref != null)
  }

  test("Observable has Monad syntax") {
    implicit val s = TestScheduler()
    val observable = Observable.now(1)
    val product = observable.product(Observable.now(2))
    val f = product.runAsyncGetFirst; s.tick()
    assertEquals(f.value, Some(Success(Some((1,2)))))
  }

  test("Observable is MonadError") {
    val ref = implicitly[MonadError[Observable, Throwable]]
    assert(ref != null)
  }

  test("Observable is CoflatMap") {
    val ref = implicitly[CoflatMap[Observable]]
    assert(ref != null)
  }

  test("Observable has ApplicativeError syntax") {
    implicit val s = TestScheduler()
    val observable = Observable.now(1)
    val result = observable.handleError(_ => 2)
    val f = result.runAsyncGetFirst; s.tick()
    assertEquals(f.value, Some(Success(Some(1))))
  }
}