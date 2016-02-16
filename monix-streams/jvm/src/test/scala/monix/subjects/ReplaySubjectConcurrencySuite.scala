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

package monix.subjects

import java.util.concurrent.{TimeUnit, CountDownLatch}
import minitest.TestSuite
import monix.execution.{Ack, Scheduler}
import monix.streams.Observable
import monix.streams.broadcast.ReplayProcessor
import monix.streams.observers.SyncObserver
import monix.execution.Ack.Continue
import monix.streams.internal.concurrent.RunnableAction

object ReplaySubjectConcurrencySuite extends TestSuite[Scheduler] {
  def tearDown(env: Scheduler) = ()
  def setup() = {
    monix.execution.Scheduler.Implicits.global
  }

  test("subscribers should get everything") { implicit s =>
    val nrOfSubscribers = 100
    val signalsPerSubscriber = 20000L
    val completed = new CountDownLatch(nrOfSubscribers)

    def createObserver = new SyncObserver[Int] {
      var received = 0L
      def onNext(elem: Int) = { received += elem; Continue }
      def onError(ex: Throwable): Unit = throw ex
      def onComplete(): Unit = {
        assertEquals(received, signalsPerSubscriber * 2)
        completed.countDown()
      }
    }

    val subject = ReplayProcessor[Int]()
    subject.unsafeSubscribeFn(createObserver)

    s.execute(RunnableAction {
      Observable.range(0, signalsPerSubscriber).map(_ => 2).unsafeSubscribeFn(subject)
      subject.unsafeSubscribeFn(createObserver)
    })

    for (_ <- 0 until (nrOfSubscribers - 2))
      s.execute(RunnableAction(subject.unsafeSubscribeFn(createObserver)))

    assert(completed.await(60, TimeUnit.SECONDS), "completed.await")
  }
}
