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

import monix.execution.Ack
import monix.execution.Ack.Continue
import monix.execution.internal.Platform
import monix.reactive.{Observable, Observer}
import scala.concurrent.duration._
import scala.concurrent.{Future, Promise}

object BufferTimedOrCountedSuite extends BaseOperatorSuite {
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
        .bufferTimedAndCounted(1.second, maxCount = 20)
        .map(_.sum)

      Sample(o, count(sourceCount), sum(sourceCount), waitFirst, waitNext)
    }
  }

  def observableInError(sourceCount: Int, ex: Throwable) = {
    require(sourceCount > 0, "sourceCount must be strictly positive")
    Some {
      val o = createObservableEndingInError(Observable
        .intervalAtFixedRate(100.millis, 100.millis).take(sourceCount), ex)
        .bufferTimedAndCounted(1.second, maxCount = 20)
        .map(_.sum)

      Sample(o, count(sourceCount/10), sum(sourceCount/10), waitFirst, waitNext)
    }
  }

  def brokenUserCodeObservable(sourceCount: Int, ex: Throwable) =
    None

  override def cancelableObservables(): Seq[Sample] = {
    val o = Observable.range(0, Platform.recommendedBatchSize)
      .delayOnNext(1.second)
      .bufferTimedAndCounted(1.second, maxCount = 20)
      .map(_.sum)

    Seq(Sample(o, 0, 0, 0.seconds, 0.seconds))
  }

  test("should emit buffer onComplete") { implicit s =>
    val sourceCount = 157

    val obs = Observable.intervalAtFixedRate(100.millis)
      .take(sourceCount * 10)
      .bufferTimedAndCounted(2.seconds, 100)
      .map(_.sum)

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

    s.tick(waitFirst + waitNext * (sourceCount - 1))
    assertEquals(received, sourceCount / 2 + 1)
    assertEquals(total, sum(sourceCount))
    s.tick(waitNext)
    assert(wasCompleted)
  }

  test("should throw on negative timespan") { implicit s =>
    intercept[IllegalArgumentException] {
      Observable.intervalAtFixedRate(100.millis)
        .bufferTimedAndCounted(Duration.Zero - 1.second, 10)
    }
  }

  test("should not do back-pressure for onComplete, for 1 element") { implicit s =>
    val p = Promise[Continue.type]()
    var wasCompleted = false

    createObservable(1) match {
      case ref@Some(Sample(obs, count, sum, waitForFirst, waitForNext)) =>
        var onNextReceived = false

        obs.unsafeSubscribeFn(new Observer[Long] {
          def onNext(elem: Long): Future[Ack] = {
            onNextReceived = true; p.future
          }

          def onError(ex: Throwable): Unit = throw new IllegalStateException()

          def onComplete(): Unit = wasCompleted = true
        })

        s.tick(waitForFirst)
        assert(wasCompleted)
        assert(onNextReceived)
        p.success(Continue)
        s.tick(waitForNext)
    }
  }
}
