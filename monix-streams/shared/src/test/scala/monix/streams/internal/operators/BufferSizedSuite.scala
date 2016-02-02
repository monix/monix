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

package monix.streams.internal.operators

import monix.streams.{Observer, Observable, Ack}
import monix.streams.Ack.Continue
import monix.streams.exceptions.DummyException
import monix.streams.Observer
import scala.concurrent.duration.Duration

object BufferSizedSuite extends BaseOperatorSuite {
  val waitNext = Duration.Zero
  val waitFirst = Duration.Zero

  def sum(sourceCount: Int) = {
    val total = (sourceCount * 10 - 1).toLong
    total * (total + 1) / 2
  }

  def count(sourceCount: Int) = {
    sourceCount
  }

  def createObservable(sourceCount: Int) = {
    require(sourceCount > 0, "count must be strictly positive")
    Some {
      val o = Observable.range(0, sourceCount * 10).buffer(10).map(_.sum)
      Sample(o, count(sourceCount), sum(sourceCount), waitFirst, waitNext)
    }
  }

  def observableInError(sourceCount: Int, ex: Throwable) = {
    require(sourceCount > 0, "count must be strictly positive")
    Some {
      val o = Observable.range(0, sourceCount * 10 + 1)
        .map(x => if (x == sourceCount * 10) throw ex else x)
        .buffer(10).map(_.sum)

      Sample(o, count(sourceCount), sum(sourceCount), waitFirst, waitNext)
    }
  }

  def brokenUserCodeObservable(sourceCount: Int, ex: Throwable) =
    None

  test("should emit buffer onComplete") { implicit s =>
    val count = 157
    val obs = Observable.range(0, count * 10).buffer(20).map(_.sum)

    var wasCompleted = false
    var received = 0
    var total = 0L

    obs.unsafeSubscribeFn(new Observer[Long] {
      def onNext(elem: Long) = {
        received += 1
        total += elem
        Continue
      }

      def onError(ex: Throwable): Unit = ()
      def onComplete(): Unit = wasCompleted = true
    })

    s.tick(waitFirst + waitNext * (count - 1))
    assertEquals(received, count / 2 + 1)
    assertEquals(total, sum(count))
    s.tick(waitNext)
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

    obs.unsafeSubscribeFn(new Observer[Long] {
      def onNext(elem: Long) = {
        received += 1
        total += elem
        Continue
      }

      def onError(ex: Throwable): Unit = errorThrown = ex
      def onComplete(): Unit = ()
    })

    s.tick(waitFirst + waitNext * (count - 1))
    assertEquals(received, count / 2 + 1)
    assertEquals(total, sum(count))
    s.tick(waitNext)
    assertEquals(errorThrown, DummyException("dummy"))
  }
}
