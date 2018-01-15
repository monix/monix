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
import monix.execution.Ack.Continue
import monix.execution.{Ack, Scheduler}
import monix.execution.schedulers.TestScheduler
import monix.reactive.Observable
import monix.reactive.OverflowStrategy.Unbounded
import monix.reactive.observers.Subscriber
import scala.concurrent.Future

object ExecuteOnSuite extends TestSuite[TestScheduler] {
  def setup() = TestScheduler()
  def tearDown(s: TestScheduler) = {
    assert(s.state.tasks.isEmpty,
      "TestScheduler should be left with no pending tasks")
  }

  test("it works") { implicit s =>
    val other = TestScheduler()
    val nr = s.executionModel.recommendedBatchSize * 2
    val expectedSum = nr.toLong * (nr - 1) / 2
    var receivedOnNext: Long = 0
    var finallyReceived: Long = 0

    val forked =
      Observable.range(0, nr)
        .sumF.doOnNext(sum => receivedOnNext = sum)
        .executeOn(other)

    val obs =
      forked.asyncBoundary(Unbounded)

    obs.unsafeSubscribeFn(new Subscriber[Long] {
      implicit val scheduler: Scheduler = s
      def onError(ex: Throwable): Unit = throw ex
      def onComplete(): Unit = ()

      def onNext(elem: Long): Future[Ack] = {
        finallyReceived = elem
        Continue
      }
    })

    assertEquals(finallyReceived, 0)
    assertEquals(receivedOnNext, 0)

    // Not going to work
    s.tick()
    assertEquals(finallyReceived, 0)
    assertEquals(receivedOnNext, 0)

    // Should trigger processing, but not final result
    // because of the async boundary
    other.tick()
    assertEquals(finallyReceived, 0)
    assertEquals(receivedOnNext, expectedSum)

    // We should now have it all!
    s.tick()
    assertEquals(finallyReceived, expectedSum)
  }
}
