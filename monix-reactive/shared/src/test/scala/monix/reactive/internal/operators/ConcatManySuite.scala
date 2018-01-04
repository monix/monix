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

object ConcatManySuite extends BaseOperatorSuite {
  def createObservable(sourceCount: Int) = Some {
    val o = Observable.range(0, sourceCount)
      .flatMap(i => Observable.fromIterable(Seq(i,i,i)))

    Sample(o, count(sourceCount), sum(sourceCount), waitFirst, waitNext)
  }

  def count(sourceCount: Int) =
    sourceCount * 3

  def waitFirst = Duration.Zero
  def waitNext = Duration.Zero

  def observableInError(sourceCount: Int, ex: Throwable) =
    if (sourceCount == 1) None else Some {
      val o = createObservableEndingInError(Observable.range(0, sourceCount), ex)
        .flatMap(_ => Observable.fromIterable(Seq(1L, 1L, 1L)))

      Sample(o, count(sourceCount), count(sourceCount)-2, waitFirst, waitNext)
    }

  def sum(sourceCount: Int) = {
    3L * sourceCount * (sourceCount - 1) / 2
  }

  def brokenUserCodeObservable(sourceCount: Int, ex: Throwable) = Some {
    val o = Observable.range(0, sourceCount).flatMap { i =>
      if (i == sourceCount-1)
        throw ex
      else
        Observable.fromIterable(Seq(i,i,i))
    }

    Sample(o, count(sourceCount-1), sum(sourceCount-1), waitFirst, waitNext)
  }

  override def cancelableObservables(): Seq[Sample] = {
    val sourceCount = Platform.recommendedBatchSize*3
    val o = Observable.range(0, sourceCount)
      .flatMap(i => Observable
        .range(0, sourceCount).map(_ => 1L)
        .delaySubscription(1.second))

    val count = Platform.recommendedBatchSize*3
    Seq(Sample(o, count, count, 1.seconds, 0.seconds))
  }

  test("concatMap should be cancelable before main stream has finished") { implicit s =>
    val source = Observable(1L,2L).concatMap { x =>
      Observable.intervalWithFixedDelay(1.second, 1.second).map(_ + x)
    }

    var total = 0L
    val subscription = source.unsafeSubscribeFn(
      new Observer.Sync[Long] {
        def onNext(elem: Long): Ack = {
          total += elem
          Continue
        }

        def onError(ex: Throwable): Unit = throw ex
        def onComplete(): Unit = ()
      })

    s.tick(10.seconds)
    assertEquals(total, 5 * 11L)
    subscription.cancel()

    s.tick()
    assertEquals(total, 5 * 11L)
    assert(s.state.tasks.isEmpty, "tasks.isEmpty")
  }

  test("concatMap should be cancelable after main stream has finished") { implicit s =>
    val source = Observable.now(1L).concatMap { x =>
      Observable.intervalWithFixedDelay(1.second, 1.second).map(_ + x)
    }

    var total = 0L
    val subscription = source.unsafeSubscribeFn(
      new Observer.Sync[Long] {
        def onNext(elem: Long): Ack = {
          total += elem
          Continue
        }

        def onError(ex: Throwable): Unit = throw ex
        def onComplete(): Unit = ()
      })

    s.tick(10.seconds)
    assertEquals(total, 5 * 11L)
    subscription.cancel()

    s.tick()
    assertEquals(total, 5 * 11L)
    assert(s.state.tasks.isEmpty, "tasks.isEmpty")
  }
}
