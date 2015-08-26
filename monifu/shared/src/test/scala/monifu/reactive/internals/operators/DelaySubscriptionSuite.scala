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

package monifu.reactive.internals.operators

import monifu.reactive.Ack.Continue
import monifu.reactive.exceptions.DummyException
import monifu.reactive.{Observer, Observable}
import concurrent.duration._
import scala.concurrent.Future

object DelaySubscriptionSuite extends BaseOperatorSuite {
  def observable(sourceCount: Int) = Some {
    val o = Observable.range(0, sourceCount)
      .delaySubscription(1.second)

    Sample(o, count(sourceCount), sum(sourceCount), waitFirst, waitNext)
  }

  def count(sourceCount: Int) = sourceCount
  def sum(sourceCount: Int) = sourceCount * (sourceCount - 1) / 2
  def waitFirst = 1.second
  def waitNext = Duration.Zero

  def observableInError(sourceCount: Int, ex: Throwable) = None
  def brokenUserCodeObservable(sourceCount: Int, ex: Throwable) = None

  test("it delays") { implicit s =>
    val obs = Observable.unit(1).delaySubscription(1.second)
    var wasCompleted = false
    var received = 0

    obs.unsafeSubscribe(new Observer[Int] {
      def onError(ex: Throwable) = ()
      def onComplete() = wasCompleted = true

      def onNext(elem: Int) = {
        received += elem
        Continue
      }
    })

    s.tick()
    assertEquals(received, 0)
    s.tick(1.second)
    assertEquals(received, 1)
    assert(wasCompleted)
  }

  test("delaySubscription.onFuture triggering an error") { implicit s =>
    val obs = Observable.unit(1)
      .delaySubscription(Future { throw new DummyException("dummy") })

    var errorThrown: Throwable = null
    obs.unsafeSubscribe(new Observer[Int] {
      def onError(ex: Throwable) =
        errorThrown = ex
      def onComplete() = ()
      def onNext(elem: Int) = Continue
    })

    assertEquals(errorThrown, null)

    s.tick()
    assertEquals(errorThrown, DummyException("dummy"))
  }
}
