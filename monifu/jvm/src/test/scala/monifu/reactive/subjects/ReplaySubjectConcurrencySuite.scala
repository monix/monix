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

package monifu.reactive.subjects

import java.util.concurrent.{TimeUnit, CountDownLatch}

import minitest.TestSuite
import monifu.concurrent.Scheduler
import monifu.reactive.Ack.Continue
import monifu.reactive.Observable
import monifu.reactive.observers.SynchronousObserver

object ReplaySubjectConcurrencySuite extends TestSuite[Scheduler] {
  def tearDown(env: Scheduler) = ()
  def setup() = {
    monifu.concurrent.Implicits.globalScheduler
  }

  test("subscribers should get everything") { implicit s =>
    val nrOfSubscribers = 100
    val completed = new CountDownLatch(nrOfSubscribers)
    def create(expectedSum: Long) = new SynchronousObserver[Int] {
      var received = 0L
      def onNext(elem: Int) = { received += elem; Continue }
      def onError(ex: Throwable): Unit = throw ex
      def onComplete(): Unit = {
        assertEquals(received, expectedSum)
        completed.countDown()
      }
    }

    val subject = ReplaySubject[Int]()
    subject.onSubscribe(create(40000))

    s.execute {
      Observable.range(0, 20000).map(_ => 2).onSubscribe(subject)
      subject.onSubscribe(create(40000))
    }

    for (_ <- 0 until (nrOfSubscribers - 2))
      s.execute(subject.onSubscribe(create(40000)))

    assert(completed.await(10, TimeUnit.SECONDS), "completed.await")
  }
}
