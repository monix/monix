/*
 * Copyright (c) 2014-2021 by The Monix Project Developers.
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
import monix.reactive.{Observable, Observer}
import monix.reactive.internal.operators.MaxSuite.{assert, assertEquals, test}

import scala.concurrent.duration._

object ThrottleLatestSuite extends BaseOperatorSuite {
  def sum(sourceCount: Int) = {
    sourceCount * (sourceCount - 1) / 2
  }
  def createObservable(sourceCount: Int) = Some {
    if (sourceCount == 1) {
      val o = Observable.now(100L).delayExecution(500.millis).throttleLatest(1.second, true)
      Sample(o, 1, 100, 500.millis, 1.second)
    } else {

      val o = Observable
        .intervalAtFixedRate(2.second)
        .take(sourceCount.toLong)
        .throttleLatest(1.second, true)

      Sample(o, sourceCount, sum(sourceCount), 2.second, 2.second)
    }
  }

  def observableInError(sourceCount: Int, ex: Throwable) = None
  def brokenUserCodeObservable(sourceCount: Int, ex: Throwable) = None

  override def cancelableObservables() = {
    val o = Observable.intervalAtFixedRate(2.second).throttleLatest(1.second, true)
    Seq(
      Sample(o, 1, 0, 0.seconds, 2.seconds),
      Sample(o, 2, 1, 2.seconds, 2.seconds)
    )
  }

  test("should emit last element onComplete if emitLast is set to true") { implicit s =>
    val source: Observable[Long] = Observable.intervalAtFixedRate(1.second).take(2).throttleLatest(5.second, true)
    var received = 0
    var wasCompleted = false
    source.unsafeSubscribeFn(new Observer[Long] {
      def onNext(elem: Long) = { received += 1; Continue }
      def onError(ex: Throwable) = ()
      def onComplete() = { wasCompleted = true }
    })

    s.tick(1.second)
    assertEquals(received, 2)
    assert(wasCompleted)
  }

  test("should not emit last element onComplete if emitLast is set to false") { implicit s =>
    val source: Observable[Long] = Observable.intervalAtFixedRate(1.second).take(2).throttleLatest(5.second, false)
    var received = 0
    var wasCompleted = false
    source.unsafeSubscribeFn(new Observer[Long] {
      def onNext(elem: Long) = { received += 1; Continue }
      def onError(ex: Throwable) = ()
      def onComplete() = { wasCompleted = true }
    })

    s.tick(1.second)
    assertEquals(received, 1)
    assert(wasCompleted)
  }
}
