/*
 * Copyright (c) 2014-2022 Monix Contributors.
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

import cats.laws._
import cats.laws.discipline._
import monix.execution.Ack
import monix.execution.Ack.Continue
import monix.execution.exceptions.DummyException
import monix.reactive.{ Observable, Observer }

import scala.concurrent.duration.Duration.Zero
import scala.concurrent.duration._
import scala.concurrent.{ Future, Promise }

object ConcatMapIterableSuite extends BaseOperatorSuite {
  def sum(sourceCount: Int): Long = (1 to sourceCount).flatMap(i => List(i, i * 10)).sum.toLong
  def count(sourceCount: Int) = 2 * sourceCount

  def createObservable(sourceCount: Int) = {
    require(sourceCount > 0, "sourceCount should be strictly positive")
    Some {
      val o =
        if (sourceCount == 1)
          Observable.now(1L).concatMapIterable(i => List(i, i * 10))
        else
          Observable.range(1, sourceCount.toLong + 1, 1).concatMapIterable(i => List(i, i * 10))

      Sample(o, count(sourceCount), sum(sourceCount), Zero, Zero)
    }
  }

  def observableInError(sourceCount: Int, ex: Throwable) = {
    require(sourceCount > 0, "sourceCount should be strictly positive")
    Some {
      val ex = DummyException("dummy")
      val o =
        if (sourceCount == 1)
          createObservableEndingInError(Observable.now(1L), ex)
            .concatMapIterable(i => List(i, i * 10))
        else
          createObservableEndingInError(Observable.range(1, sourceCount.toLong + 1, 1), ex)
            .concatMapIterable(i => List(i, i * 10))

      Sample(o, count(sourceCount), sum(sourceCount), Zero, Zero)
    }
  }

  def brokenUserCodeObservable(sourceCount: Int, ex: Throwable) = {
    require(sourceCount > 0, "sourceCount should be strictly positive")
    Some {
      val o =
        if (sourceCount == 1)
          Observable.now(1).concatMapIterable(_ => throw ex)
        else
          Observable.range(1, sourceCount.toLong + 1, 1).concatMapIterable { x =>
            if (x == sourceCount)
              throw ex
            else
              List(x, x * 10)
          }

      Sample(o, count(sourceCount - 1), sum(sourceCount - 1), Zero, Zero)
    }
  }

  override def cancelableObservables(): Seq[Sample] = {
    val obs = Observable.range(0, 1000).delayOnNext(1.second).concatMapIterable(i => List(i, i * 10))
    Seq(Sample(obs, 0, 0, 0.seconds, 0.seconds))
  }

  test("should delay onComplete, for last element") { implicit s =>
    val p = List(Promise[Ack](), Promise[Ack]())
    var wasCompleted = false

    createObservable(1) match {
      case Some(Sample(obs, _, _, waitForFirst, waitForNext)) =>
        var onNextReceived = 0

        obs.unsafeSubscribeFn(new Observer[Long] {
          def onNext(elem: Long): Future[Ack] = { onNextReceived += 1; p(onNextReceived - 1).future }
          def onError(ex: Throwable): Unit = throw new IllegalStateException()
          def onComplete(): Unit = wasCompleted = true
        })

        assert(!wasCompleted)
        s.tick(waitForFirst)
        assert(onNextReceived == 1)
        p(0).success(Continue)
        s.tick(waitForNext)
        assert(onNextReceived == 2)
        p(1).success(Continue)
        s.tick(waitForNext)
        assert(wasCompleted)

      case _ =>
        fail()
    }
  }

  test("Observable.concatMapIterable is equivalent with Observable.flatMap + Observable.fromIterable") { implicit s =>
    check1 { (list: List[List[Int]]) =>
      val obs = Observable.fromIterable(list)
      val resultViaMapConcat = obs.concatMapIterable(identity).reduce(_ + _).lastL
      val resultViaFlatMap = obs.flatMap(Observable.fromIterable).reduce(_ + _).lastL

      resultViaMapConcat <-> resultViaFlatMap
    }
  }
}
