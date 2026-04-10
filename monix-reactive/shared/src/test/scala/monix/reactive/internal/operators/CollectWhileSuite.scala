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

import scala.concurrent.duration._
import scala.concurrent.duration.Duration.Zero
import scala.concurrent.{ Future, Promise }

object CollectWhileSuite extends BaseOperatorSuite {
  def sum(sourceCount: Int): Long =
    sourceCount.toLong * (sourceCount + 1) / 2

  def count(sourceCount: Int) =
    sourceCount

  def createObservable(sourceCount: Int) = {
    require(sourceCount > 0, "sourceCount should be strictly positive")
    Some {
      val o =
        if (sourceCount == 1)
          Observable.range(1, 10).collectWhile { case x if x <= 1 => x }
        else
          Observable.range(1, sourceCount.toLong * 2 + 1).collectWhile { case x if x <= sourceCount => x }

      Sample(o, count(sourceCount), sum(sourceCount), Zero, Zero)
    }
  }

  def observableInError(sourceCount: Int, ex: Throwable) = {
    require(sourceCount > 0, "sourceCount should be strictly positive")
    Some {
      val ex = DummyException("dummy")
      val o = createObservableEndingInError(Observable.range(1, sourceCount.toLong + 1), ex).collectWhile {
        case x if x <= sourceCount * 2 => x
      }

      Sample(o, count(sourceCount), sum(sourceCount), Zero, Zero)
    }
  }

  def brokenUserCodeObservable(sourceCount: Int, ex: Throwable) = {
    require(sourceCount > 0, "sourceCount should be strictly positive")
    Some {
      val ex = DummyException("dummy")
      val o = Observable.range(1L, sourceCount.toLong * 2).collectWhile {
        case x if x < sourceCount => x
        case _ => throw ex
      }

      Sample(o, count(sourceCount - 1), sum(sourceCount - 1), Zero, Zero)
    }
  }

  override def cancelableObservables(): Seq[Sample] = {
    val s = Observable.range(1, 10).delayOnNext(1.second).collectWhile {
      case x if x <= 1 => x
    }
    Seq(Sample(s, 0, 0, 0.seconds, 0.seconds))
  }

  test("should not call onComplete multiple times for 1 element") { implicit s =>
    val p = Promise[Continue.type]()
    var wasCompleted = 0

    createObservable(1) match {
      case Some(Sample(obs, _, _, waitForFirst, waitForNext)) =>
        var onNextReceived = false

        obs.unsafeSubscribeFn(new Observer[Long] {
          def onNext(elem: Long): Future[Ack] = { onNextReceived = true; p.future }
          def onError(ex: Throwable): Unit = throw new IllegalStateException()
          def onComplete(): Unit = wasCompleted += 1
        })

        s.tick(waitForFirst)
        assert(onNextReceived)
        p.success(Continue)
        s.tick(waitForNext)
        assertEquals(wasCompleted, 1)

      case _ =>
        fail()
    }
  }

  test("should only invoke the partial function once per element") { implicit s =>
    var invocationCount = 0
    var result: Int = 0
    var wasCompleted = false
    val f: Int => Option[Int] = x => {
      invocationCount += 1
      if (x % 2 == 0) Some(x) else None
    }
    val pf = Function.unlift(f)
    Observable
      .now(2)
      .collectWhile(pf)
      .unsafeSubscribeFn(new Observer[Int] {
        def onNext(elem: Int): Future[Ack] = { result = elem; Continue }
        def onError(ex: Throwable): Unit = throw new IllegalStateException()
        def onComplete(): Unit = wasCompleted = true
      })
    s.tick()
    assert(wasCompleted)
    assertEquals(result, 2)
    assertEquals(invocationCount, 1)
  }

  test("Observable.collectWhile <=> Observable.takeWhile.collect ") { implicit s =>
    check2 { (stream: Observable[Option[Int]], f: Int => Int) =>
      val result = stream.collectWhile { case Some(x) => f(x) }.toListL
      val expected = stream.takeWhile(_.isDefined).collect { case Some(x) => f(x) }.toListL

      result <-> expected
    }
  }
}
