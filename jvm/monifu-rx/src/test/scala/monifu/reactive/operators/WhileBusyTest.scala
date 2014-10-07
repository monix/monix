/*
 * Copyright (c) 2014 by its authors. Some rights reserved.
 * See the project homepage at
 *
 *     http://www.monifu.org/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  	http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package monifu.reactive.operators

import java.util.concurrent.{TimeUnit, CountDownLatch}

import monifu.reactive.Ack.Continue
import monifu.reactive.{Ack, Observer}
import monifu.reactive.channels.PublishChannel
import org.scalatest.FunSpec
import concurrent.duration._
import monifu.concurrent.Implicits.globalScheduler
import scala.concurrent.{Await, Promise}

class WhileBusyTest extends FunSpec {
  describe("Observable.whileBusyDrop") {
    it("should work") {
      var dropped = 0
      var received = 0

      val ch = PublishChannel[Int]()
      val barrierOne = new CountDownLatch(3)
      val completed = new CountDownLatch(1)

      val p = Promise[Ack]()
      val future = p.future

      ch.whileBusyDrop(x => { dropped += x; barrierOne.countDown() })
        .subscribe(new Observer[Int] {

        def onNext(elem: Int) = {
          received += elem
          barrierOne.countDown()
          future
        }

        def onError(ex: Throwable) = ()
        def onComplete() = completed.countDown()
      })

      ch.pushNext(10)
      ch.pushNext(20)
      ch.pushNext(30)

      assert(barrierOne.await(5, TimeUnit.SECONDS), "barrierOne.await should succeed")

      assert(dropped === 50)
      assert(received === 10)

      p.success(Continue)
      Await.result(future, 5.seconds)

      ch.pushNext(40, 50)
      ch.pushComplete()

      assert(completed.await(5, TimeUnit.SECONDS), "completed.await should succeed")
      assert(dropped === 50)
      assert(received === 100)
    }
  }
}
