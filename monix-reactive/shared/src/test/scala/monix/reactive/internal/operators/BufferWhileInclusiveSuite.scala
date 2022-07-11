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

import monix.execution.Ack
import monix.execution.Ack.Continue
import monix.execution.exceptions.DummyException
import monix.execution.internal.Platform
import monix.reactive.{ Observable, Observer }

import scala.concurrent.{ Future, Promise }
import scala.concurrent.duration.Duration
import scala.concurrent.duration._

import scala.util.Success

object BufferWhileInclusiveSuite extends BaseOperatorSuite {
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
      val o =
        Observable.range(1L, sourceCount.toLong * 10).bufferWhileInclusive(_ % 10 != 0).map(_.sum)

      Sample(o, count(sourceCount), sum(sourceCount), waitFirst, waitNext)
    }
  }

  def observableInError(sourceCount: Int, ex: Throwable) = {
    require(sourceCount > 0, "count must be strictly positive")
    Some {
      val o = createObservableEndingInError(Observable.range(0L, sourceCount.toLong * 10), ex)
        .bufferWhileInclusive(_ % 10 != 0)
        .map(_.sum)

      Sample(o, count(sourceCount), sum(sourceCount), waitFirst, waitNext)
    }
  }

  def brokenUserCodeObservable(sourceCount: Int, ex: Throwable): Some[Sample] = {
    require(sourceCount > 0, "sourceCount should be strictly positive")
    Some {
      val ex = DummyException("dummy")
      val o = Observable
        .range(1L, sourceCount.toLong * 2)
        .bufferWhileInclusive { x =>
          if (x < sourceCount) true else throw ex
        }
        .map(_.sum)

      Sample(o, 0, 0, waitFirst, waitNext)
    }
  }

  override def cancelableObservables() = {
    val o = Observable
      .range(0L, Platform.recommendedBatchSize.toLong)
      .delayOnNext(1.second)
      .bufferWhileInclusive(_ <= 1)
      .map(_.sum)

    Seq(Sample(o, 0, 0, 0.seconds, 0.seconds))
  }

  test("should emit buffer onComplete") { implicit s =>
    val count = 157
    val obs = Observable.range(1L, count.toLong * 10).bufferWhileInclusive(_ % 20 != 0).map(_.sum)

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

    s.tick(waitFirst + waitNext * (count - 1).toLong)
    assertEquals(received, count / 2 + 1)
    assertEquals(total, sum(count))
    s.tick(waitNext)
    assert(wasCompleted)
  }

  test("should drop buffer onError") { implicit s =>
    val count = 157
    val dummy = DummyException("dummy")
    val obs = createObservableEndingInError(Observable.range(0L, count.toLong * 10), dummy)
      .bufferWhileInclusive(_ % 20 != 0)
      .map(_.sum)

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

    s.tick(waitFirst + waitNext * (count - 1).toLong)
    assertEquals(received, 156 / 2 + 1)
    assertEquals(total, sum(156) + 1560)
    s.tick(waitNext)
    assertEquals(errorThrown, dummy)
  }

  test("should not do back-pressure for onComplete, for 1 element") { implicit s =>
    val p = Promise[Continue.type]()
    var wasCompleted = false

    createObservable(1) match {
      case Some(Sample(obs, _, _, waitForFirst, waitForNext)) =>
        var onNextReceived = false

        obs.unsafeSubscribeFn(new Observer[Long] {
          def onNext(elem: Long): Future[Ack] = { onNextReceived = true; p.future }
          def onError(ex: Throwable): Unit = throw new IllegalStateException()
          def onComplete(): Unit = wasCompleted = true
        })

        assert(wasCompleted)
        s.tick(waitForFirst)
        assert(onNextReceived)
        p.success(Continue)
        s.tick(waitForNext)

      case _ =>
        fail()
    }
  }

  test("should work for scaladoc example") { implicit s =>
    val f =
      Observable(1, 1, 1, 2, 2, 1, 3)
        .bufferWhileInclusive(_ == 1)
        .toListL
        .runToFuture

    assertEquals(f.value, Some(Success(List(List(1, 1, 1, 2), List(2), List(1, 3)))))
  }
}
