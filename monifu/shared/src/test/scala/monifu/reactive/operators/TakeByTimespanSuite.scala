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

import monifu.reactive.Ack.{Cancel, Continue}
import monifu.reactive.{DummyException, Observer, Observable}
import monifu.concurrent.extensions._
import concurrent.duration._
import scala.concurrent.Future

object TakeByTimespanSuite extends BaseOperatorSuite {
  val waitForFirst = Duration.Zero
  val waitForNext = 1.second

  def brokenUserCodeObservable(sourceCount: Int, ex: Throwable) = None

  def sum(sourceCount: Int) =
    sourceCount.toLong * (sourceCount - 1) / 2

  def count(sourceCount: Int) =
    sourceCount

  def observable(sourceCount: Int) = {
    require(sourceCount > 0, "sourceCount should be strictly positive")

    Some(Observable.intervalAtFixedRate(1.second)
      .take(1.second * sourceCount))
  }

  def observableInError(sourceCount: Int, ex: Throwable) = {
    require(sourceCount > 0, "sourceCount should be strictly positive")
    Some {
      val source = if (sourceCount == 1)
        createObservableEndingInError(Observable.range(1, 10).take(1), ex)
      else
        createObservableEndingInError(Observable.range(1, sourceCount * 2).take(sourceCount), ex)

      source.take(1.day)
    }
  }

  test("should complete even if no element was emitted") { implicit s =>
    var wasCompleted = false

    Observable.never.take(1.second).unsafeSubscribe(new Observer[Any] {
      def onNext(elem: Any) = Continue
      def onError(ex: Throwable) = ()
      def onComplete() = wasCompleted = true
    })

    s.tick()
    assert(!wasCompleted)
    s.tick(1.second)
    assert(wasCompleted)
  }

  test("should cancel if downstream cancels") { implicit s =>
    var received = 0

    Observable.intervalAtFixedRate(1.second).take(10.seconds).subscribe(
      new Observer[Long] {
        def onNext(elem: Long) =
          Future.delayedResult(100.millis) {
            received += 1
            if (received < 3) Continue else Cancel
          }

        def onError(ex: Throwable) =
          throw new IllegalStateException()

        def onComplete() =
          throw new IllegalStateException()
      })

    s.tick(100.millis)
    assertEquals(received, 1)
    s.tick(1.second)
    assertEquals(received, 2)
    s.tick(1.second)
    assertEquals(received, 3)
  }
}
