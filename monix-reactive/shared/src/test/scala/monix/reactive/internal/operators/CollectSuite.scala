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
import monix.execution.exceptions.DummyException
import monix.reactive.{Observable, Observer}

import scala.concurrent.duration._
import scala.concurrent.{Future, Promise}

object CollectSuite extends BaseOperatorSuite {
  val waitFirst = Duration.Zero
  val waitNext = Duration.Zero

  def count(sourceCount: Int) = {
    sourceCount
  }

  def sum(sourceCount: Int): Long =
    sourceCount.toLong * (sourceCount + 1)

  def createObservable(sourceCount: Int) = {
    require(sourceCount > 0, "sourceCount should be strictly positive")
    Some {
      val o = if (sourceCount == 1)
        Observable.now(2L)
          .collect { case x if x % 2 == 0 => x }
      else
        Observable.range(1, sourceCount * 2 + 1, 1)
          .collect { case x if x % 2 == 0 => x }

      Sample(o, count(sourceCount), sum(sourceCount), waitFirst, waitNext)
    }
  }

  def observableInError(sourceCount: Int, ex: Throwable) = {
    require(sourceCount > 0, "sourceCount should be strictly positive")
    Some {
      val ex = DummyException("dummy")
      val o = if (sourceCount == 1)
        createObservableEndingInError(Observable.now(2L), ex)
          .collect { case x if x % 2 == 0 => x }
      else
        createObservableEndingInError(Observable.range(1, sourceCount * 2 + 1, 1), ex)
          .collect { case x if x % 2 == 0 => x }

      Sample(o, count(sourceCount), sum(sourceCount), waitFirst, waitNext)
    }
  }

  def brokenUserCodeObservable(sourceCount: Int, ex: Throwable) = {
    require(sourceCount > 0, "sourceCount should be strictly positive")
    Some {
      val o = if (sourceCount == 1)
        Observable.now(1L).collect { case _ => throw ex }
      else
        Observable.range(1, sourceCount * 2 + 1, 1).collect {
          case x if x % 2 == 0 =>
            if (x == sourceCount * 2)
              throw ex
            else
              x
        }

      Sample(o, count(sourceCount-1), sum(sourceCount-1), waitFirst, waitNext)
    }
  }

  override def cancelableObservables(): Seq[CollectSuite.Sample] = {
    val o = Observable.range(0, Platform.recommendedBatchSize)
      .delayOnNext(1.second)
      .collect { case x if x % 2 == 0 => x }

    Seq(Sample(o, 0, 0, 0.seconds, 0.seconds))
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

        s.tick(waitForFirst)
        assert(onNextReceived)
        assert(wasCompleted)
        p.success(Continue)
        s.tick(waitForNext)
    }
  }
}
