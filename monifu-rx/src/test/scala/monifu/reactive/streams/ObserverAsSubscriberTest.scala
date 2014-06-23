/*
 * Copyright (c) 2014 by its authors. Some rights reserved.
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

package monifu.reactive.streams

import java.util.concurrent.{TimeUnit, CountDownLatch}

import monifu.reactive.Ack.Continue
import monifu.reactive.{Observable, Ack, Observer}
import org.scalatest.FunSpec
import monifu.concurrent.Scheduler.Implicits.global
import scala.concurrent.Future


class ObserverAsSubscriberTest extends FunSpec {

  describe("ObserverAsSubscriber") {
    it("should work") {
      var sum = 0
      val completed = new CountDownLatch(1)

      val subscriber = ObserverAsSubscriber(new Observer[Int] {
        def onNext(elem: Int): Future[Ack] = {
          sum += elem
          Continue
        }

        def onError(ex: Throwable): Unit = {
          global.reportFailure(ex)
        }

        def onComplete(): Unit = {
          completed.countDown()
        }
      })

      Observable.range(1, 100001).subscribe(subscriber)

      assert(completed.await(10, TimeUnit.SECONDS), "completed.await should have succeeded")
      assert(sum === 50000 * 100001)
    }
  }
}
