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
import monix.reactive.{Observable, Observer}
import scala.concurrent.duration._
import scala.concurrent.duration.Duration.Zero

object MergeManySuite extends BaseOperatorSuite {
  def createObservable(sourceCount: Int) = Some {
    val o = Observable.range(0, sourceCount)
      .mergeMap(i => Observable.fromIterable(Seq(i,i,i,i)))
    Sample(o, count(sourceCount), sum(sourceCount), Zero, Zero)
  }

  def count(sourceCount: Int) =
    4 * sourceCount

  def observableInError(sourceCount: Int, ex: Throwable) = {
    val o = Observable.range(0, sourceCount).mergeMap(_ => Observable.raiseError(ex))
    Some(Sample(o, 0, 0, Zero, Zero))
  }

  def sum(sourceCount: Int) = {
    4L * sourceCount * (sourceCount - 1) / 2
  }

  def brokenUserCodeObservable(sourceCount: Int, ex: Throwable) = Some {
    val o = Observable.range(0, sourceCount).mergeMap(x => throw ex)
    Sample(o, 0, 0, Zero, Zero)
  }

  override def cancelableObservables(): Seq[Sample] = {
    val sample1 =  Observable.range(1, 100)
      .mergeMap(x => Observable.range(0,100).delaySubscription(2.second))
    val sample2 = Observable.range(0, 100).delayOnNext(1.second)
      .mergeMap(x => Observable.range(0,100).delaySubscription(2.second))

    Seq(
      Sample(sample1, 0, 0, 0.seconds, 0.seconds),
      Sample(sample1, 0, 0, 1.seconds, 0.seconds),
      Sample(sample2, 0, 0, 0.seconds, 0.seconds),
      Sample(sample2, 0, 0, 1.seconds, 0.seconds)
    )
  }

  test("mergeMap should be cancelable after main stream has finished") { implicit s =>
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