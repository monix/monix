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

package monix.internal.builders

import minitest.TestSuite
import monix.execution.Ack
import monix.execution.schedulers.TestScheduler
import Ack.Continue
import monix.{Observable, Observer}
import scala.concurrent.Future

object FromRunnableSuite extends TestSuite[TestScheduler] {
  def setup() = TestScheduler()
  def tearDown(s: TestScheduler): Unit = {
    assert(s.state.get.tasks.isEmpty,
      "TestScheduler should have no pending tasks")
  }

  test("should work") { implicit s =>
    var wasCompleted = 0
    var received = 0

    var i = 0
    val obs = Observable.fromRunnable(new Runnable {
      def run(): Unit = i += 1
    })

    obs.unsafeSubscribeFn(new Observer[Unit] {
      def onNext(elem: Unit): Future[Ack] = {
        received += i
        Continue
      }

      def onError(ex: Throwable): Unit = ()
      def onComplete(): Unit = wasCompleted += 1
    })

    s.tickOne()
    assertEquals(wasCompleted, 1)
    assertEquals(received, 1)

    obs.unsafeSubscribeFn(new Observer[Unit] {
      def onNext(elem: Unit): Future[Ack] = {
        received += i
        Continue
      }

      def onError(ex: Throwable): Unit = ()
      def onComplete(): Unit = wasCompleted += 1
    })

    s.tickOne()
    assertEquals(wasCompleted, 2)
    assertEquals(received, 3)
  }
}
