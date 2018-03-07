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

import minitest.TestSuite
import monix.execution.Ack
import monix.execution.Ack.Stop
import monix.execution.schedulers.TestScheduler
import monix.reactive.{Observable, Observer}
import scala.concurrent.{Future, Promise}
import scala.util.Success

object ExecuteOnObservableSuite extends TestSuite[TestScheduler] {
  def setup() = TestScheduler()
  def tearDown(s: TestScheduler) = {
    assert(s.state.tasks.isEmpty,
      "TestScheduler should be left with no pending tasks")
  }

  test("executeOn must execute async if forceAsync=true") { implicit s =>
    val s2 = TestScheduler()
    val obs = Observable.now(10).executeOn(s2)
    val p = Promise[Int]()

    obs.subscribe(new Observer[Int] {
      def onError(ex: Throwable): Unit = p.failure(ex)
      def onComplete(): Unit = ()
      def onNext(elem: Int): Future[Ack] = { p.success(elem); Stop }
    })

    val f = p.future
    assertEquals(f.value, None)
    s.tick()
    assertEquals(f.value, None)
    s2.tick()
    assertEquals(f.value, Some(Success(10)))
  }

  test("executeOn should not force async if forceAsync=false") { implicit s =>
    val s2 = TestScheduler()
    val obs = Observable.now(10).executeOn(s2, forceAsync = false)
    val p = Promise[Int]()

    obs.subscribe(new Observer[Int] {
      def onError(ex: Throwable): Unit = p.failure(ex)
      def onComplete(): Unit = ()
      def onNext(elem: Int): Future[Ack] = { p.success(elem); Stop }
    })

    val f = p.future
    assertEquals(f.value, Some(Success(10)))
  }

  test("executeOn should inject scheduler") { implicit s =>
    val s2 = TestScheduler()
    val obs = Observable.now(10).executeAsync.executeOn(s2, forceAsync = false)
    val p = Promise[Int]()

    obs.subscribe(new Observer[Int] {
      def onError(ex: Throwable): Unit = p.failure(ex)
      def onComplete(): Unit = ()
      def onNext(elem: Int): Future[Ack] = { p.success(elem); Stop }
    })

    val f = p.future
    assertEquals(f.value, None)
    s.tick()
    assertEquals(f.value, None)
    s2.tick()
    assertEquals(f.value, Some(Success(10)))
  }
}
