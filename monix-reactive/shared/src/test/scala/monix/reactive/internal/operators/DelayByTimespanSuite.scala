/*
 * Copyright (c) 2014-2018 by The Monix Project Developers.
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

package monix.reactive.internal.operators

import monix.execution.Ack.Continue
import monix.reactive.Observable
import monix.execution.exceptions.DummyException
import monix.reactive.observers.Subscriber
import scala.concurrent.duration._

object DelayByTimespanSuite extends BaseOperatorSuite {
  def createObservable(cnt: Int) = Some {
    val sourceCount = 20
    val source = Observable.range(0, sourceCount)
    val o = source.delayOnNext(1.second)
    val c = sourceCount
    Sample(o, c, c * (c-1) / 2, 1.second, 1.second)
  }

  def observableInError(sourceCount: Int, ex: Throwable) = Some {
    val source = createObservableEndingInError(Observable.range(0, sourceCount), ex)
    val o = source.delayOnNext(1.second)
    val c = sourceCount
    Sample(o, c-1, (c-1) * (c-2) / 2, 1.second, 1.second)
  }

  def brokenUserCodeObservable(sourceCount: Int, ex: Throwable) =
    None

  override def cancelableObservables() = {
    val o = Observable.now(1L).delayOnNext(1.second)
    Seq(Sample(o, 0, 0, 0.seconds, 0.seconds))
  }

  test("works for empty observables triggering onComplete") { implicit s =>
    val source: Observable[Long] = Observable.empty.delayOnNext(1.second)
    var wasCompleted = 0

    source.unsafeSubscribeFn(new Subscriber[Long] {
      val scheduler = s
      def onNext(elem: Long) = {
        if (1==1) fail("onNext should not happen")
        Continue
      }
      def onError(ex: Throwable): Unit =
        fail("onError should not happen")

      def onComplete(): Unit = wasCompleted += 1
    })

    assertEquals(wasCompleted, 1)
  }

  test("works for empty observables triggering onError") { implicit s =>
    val dummy = DummyException("dummy")
    val source: Observable[Long] = Observable.raiseError(dummy).delayOnNext(1.second)
    var errorThrown: Throwable = null

    source.unsafeSubscribeFn(new Subscriber[Long] {
      val scheduler = s
      def onNext(elem: Long) = {
        if (1==1) fail("onNext should not happen")
        Continue
      }

      def onError(ex: Throwable): Unit =
        errorThrown = ex
      def onComplete(): Unit =
        fail("onComplete should not happen")
    })

    assertEquals(errorThrown, dummy)
  }
}
