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

package monix.reactive.internal.builders

import minitest.TestSuite
import monix.execution.Ack
import monix.execution.Ack.Stop
import monix.execution.internal.Platform
import monix.execution.ExecutionModel.SynchronousExecution
import monix.execution.schedulers.TestScheduler
import monix.reactive.{Observable, Observer}
import scala.concurrent.{Future, Promise}
import scala.util.Success

object ExecuteWithForkObservableSuite extends TestSuite[TestScheduler]  {
  def setup() = TestScheduler()
  def tearDown(s: TestScheduler) = {
    assert(s.state.tasks.isEmpty,
      "TestScheduler should be left with no pending tasks")
  }

  test("Observable.now.executeWithFork should execute async") { implicit s =>
    val obs = Observable.now(10).executeWithFork
    val p = Promise[Int]()

    obs.subscribe(new Observer[Int] {
      def onError(ex: Throwable): Unit = p.failure(ex)
      def onComplete(): Unit = ()
      def onNext(elem: Int): Future[Ack] = { p.success(elem); Stop }
    })

    val f = p.future
    assertEquals(f.value, None)
    s.tick()
    assertEquals(f.value, Some(Success(10)))
  }

  test("Observer.executeWithModel should work") { implicit s =>
    val count = Platform.recommendedBatchSize * 4
    val obs = Observable.range(0, count).executeWithModel(SynchronousExecution)
    val sum = obs.sumL.runAsync

    s.tickOne()
    assertEquals(sum.value, Some(Success(count * (count - 1) / 2)))
  }
}
