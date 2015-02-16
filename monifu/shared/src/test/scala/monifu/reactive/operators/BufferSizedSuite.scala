/*
 * Copyright (c) 2015 Alexandru Nedelcu
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package monifu.reactive.operators

import monifu.reactive.Ack.Continue
import monifu.reactive.{Observer, DummyException, Observable}

import scala.concurrent.duration.Duration
import scala.util.Random

object BufferSizedSuite extends BaseOperatorSuite {
  val waitForNext = Duration.Zero
  val waitForFirst = Duration.Zero

  def sum(count: Int) = {
    val total = (count * 10 - 1).toLong
    total * (total + 1) / 2
  }

  def observable(count: Int) = {
    require(count > 0, "count must be strictly positive")
    Some {
      Observable.range(0, count * 10).buffer(10).map(_.sum)
    }
  }

  def observableInError(count: Int, ex: Throwable) = {
    require(count > 0, "count must be strictly positive")
    Some {
      Observable.range(0, count * 10 + 1)
        .map(x => if (x == count * 10) throw ex else x)
        .buffer(10).map(_.sum)
    }
  }

  def brokenUserCodeObservable(count: Int, ex: Throwable) =
    None

  test("should emit buffer onComplete") { implicit s =>
    val count = 157
    val obs = Observable.range(0, count * 10).buffer(20).map(_.sum)

    var wasCompleted = false
    var received = 0
    var total = 0L

    obs.unsafeSubscribe(new Observer[Long] {
      def onNext(elem: Long) = {
        received += 1
        total += elem
        Continue
      }

      def onError(ex: Throwable): Unit = ()
      def onComplete(): Unit = wasCompleted = true
    })

    s.tick(waitForFirst + waitForNext * (count - 1))
    assertEquals(received, count / 2 + 1)
    assertEquals(total, sum(count))
    s.tick(waitForNext)
    assert(wasCompleted)
  }

  test("should emit buffer onError") { implicit s =>
    val count = 157
    val obs = Observable.range(0, count * 10 + 1)
      .map(x => if (x == count * 10) throw DummyException("dummy") else x)
      .buffer(20).map(_.sum)

    var errorThrown: Throwable = null
    var received = 0
    var total = 0L

    obs.unsafeSubscribe(new Observer[Long] {
      def onNext(elem: Long) = {
        received += 1
        total += elem
        Continue
      }

      def onError(ex: Throwable): Unit = errorThrown = ex
      def onComplete(): Unit = ()
    })

    s.tick(waitForFirst + waitForNext * (count - 1))
    assertEquals(received, count / 2 + 1)
    assertEquals(total, sum(count))
    s.tick(waitForNext)
    assertEquals(errorThrown, DummyException("dummy"))
  }
}
