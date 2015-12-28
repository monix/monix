/*
 * Copyright (c) 2014-2015 by its authors. Some rights reserved.
 * See the project homepage at: http://www.monifu.org
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

package monifu.subjects

import java.util.concurrent.{TimeUnit, CountDownLatch}
import minitest.TestSuite
import monifu.concurrent.Scheduler
import monifu.Ack.Continue
import monifu.Observable
import monifu.observers.SynchronousObserver

object ReplaySubjectConcurrencySuite extends TestSuite[Scheduler] {
  def tearDown(env: Scheduler) = ()
  def setup() = {
    monifu.concurrent.Implicits.globalScheduler
  }

  test("subscribers should get everything") { implicit s =>
    val nrOfSubscribers = 100
    val signalsPerSubscriber = 20000L
    val completed = new CountDownLatch(nrOfSubscribers)

    def createObserver = new SynchronousObserver[Int] {
      var received = 0L
      def onNext(elem: Int) = { received += elem; Continue }
      def onError(ex: Throwable): Unit = throw ex
      def onComplete(): Unit = {
        assertEquals(received, signalsPerSubscriber * 2)
        completed.countDown()
      }
    }

    val subject = ReplaySubject[Int]()
    subject.unsafeSubscribeFn(createObserver)

    s.execute {
      Observable.range(0, signalsPerSubscriber).map(_ => 2).unsafeSubscribeFn(subject)
      subject.unsafeSubscribeFn(createObserver)
    }

    for (_ <- 0 until (nrOfSubscribers - 2))
      s.execute(subject.unsafeSubscribeFn(createObserver))

    assert(completed.await(60, TimeUnit.SECONDS), "completed.await")
  }
}
