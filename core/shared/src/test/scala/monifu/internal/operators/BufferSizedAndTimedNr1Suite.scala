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

package monifu.internal.operators

import monifu.Ack.Continue
import monifu.exceptions.DummyException
import monifu.{Observable, Observer}
import scala.concurrent.duration._

object BufferSizedAndTimedNr1Suite extends BaseOperatorSuite {
  val waitNext = 1.second
  val waitFirst = 1.second

  def sum(sourceCount: Int) = {
    val total = (sourceCount * 10 - 1).toLong
    total * (total + 1) / 2
  }

  def count(sourceCount: Int) = {
    sourceCount
  }

  def createObservable(sourceCount: Int) = {
    require(sourceCount > 0, "sourceCount must be strictly positive")
    Some {
      val o = Observable.intervalAtFixedRate(100.millis)
        .take(sourceCount * 10)
        .buffer(1.second, maxSize = 20)
        .map(_.sum)
      
      Sample(o, count(sourceCount), sum(sourceCount), waitFirst, waitNext)
    }
  }

  def observableInError(sourceCount: Int, ex: Throwable) = {
    require(sourceCount > 0, "sourceCount must be strictly positive")
    Some {
      val o = Observable.intervalAtFixedRate(100.millis)
        .map(x => if (x == sourceCount * 10 - 1) throw ex else x)
        .take(sourceCount * 10)
        .buffer(1.second, maxSize = 20)
        .map(_.sum)

      Sample(o, count(sourceCount), sum(sourceCount), waitFirst, waitNext)
    }
  }

  def brokenUserCodeObservable(sourceCount: Int, ex: Throwable) =
    None

  test("should emit buffer onComplete") { implicit s =>
    val sourceCount = 157

    val obs = Observable.intervalAtFixedRate(100.millis)
      .take(sourceCount * 10)
      .buffer(2.seconds, 100)
      .map(_.sum)

    var wasCompleted = false
    var received = 0
    var total = 0L

    obs.onSubscribe(new Observer[Long] {
      def onNext(elem: Long) = {
        received += 1
        total += elem
        Continue
      }

      def onError(ex: Throwable): Unit = ()
      def onComplete(): Unit = wasCompleted = true
    })

    s.tick(waitFirst + waitNext * (sourceCount - 1))
    assertEquals(received, sourceCount / 2 + 1)
    assertEquals(total, sum(sourceCount))
    s.tick(waitNext)
    assert(wasCompleted)
  }

  test("should emit buffer onError") { implicit s =>
    val sourceCount = 157

    val obs =
      createObservableEndingInError(
        Observable.intervalAtFixedRate(100.millis).take(sourceCount * 10),
        DummyException("dummy"))
      .buffer(2.seconds, 100)
      .map(_.sum)

    var errorThrown: Throwable = null
    var received = 0
    var total = 0L

    obs.onSubscribe(new Observer[Long] {
      def onNext(elem: Long) = {
        received += 1
        total += elem
        Continue
      }

      def onError(ex: Throwable): Unit = errorThrown = ex
      def onComplete(): Unit = ()
    })

    s.tick(waitFirst + waitNext * (sourceCount - 1))
    assertEquals(received, sourceCount / 2 + 1)
    assertEquals(total, sum(sourceCount))
    s.tick(waitNext)
    assertEquals(errorThrown, DummyException("dummy"))
  }

  test("should throw on negative timespan") { implicit s =>
    intercept[IllegalArgumentException] {
      Observable.intervalAtFixedRate(100.millis)
        .buffer(Duration.Zero - 1.second, 10)
    }
  }
}
