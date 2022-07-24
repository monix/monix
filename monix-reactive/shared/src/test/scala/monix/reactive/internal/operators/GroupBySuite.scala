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

import monix.eval.Task
import monix.execution.Ack
import monix.execution.Ack.{ Continue, Stop }
import monix.reactive.subjects.PublishSubject
import monix.reactive.{ Observable, Observer }
import scala.concurrent.Future
import scala.concurrent.duration.Duration.Zero
import scala.concurrent.duration._

class GroupBySuite extends BaseOperatorSuite {
  def createObservable(sourceCount: Int) = Some {
    val o = Observable
      .range(0L, sourceCount.toLong)
      .groupBy(_ % 5)
      .mergeMap(o => o.map(x => o.key + x))

    Sample(o, count(sourceCount), sum(sourceCount), Zero, Zero)
  }

  def count(sourceCount: Int) =
    sourceCount

  def sum(sourceCount: Int) = {
    (0 until sourceCount).map(x => x + x % 5).sum
  }

  def observableInError(sourceCount: Int, ex: Throwable) =
    if (sourceCount <= 1) None
    else {
      val source = Observable.range(0L, sourceCount.toLong) ++ Observable.raiseError(ex).executeAsync
      val o = source.groupBy(_ % 5).mergeMap(o => o.map(x => o.key + x))

      Some(Sample(o, count(sourceCount), sum(sourceCount), Zero, Zero))
    }

  def brokenUserCodeObservable(sourceCount: Int, ex: Throwable) = Some {
    val o = Observable.range(0L, sourceCount.toLong).groupBy(x => (throw ex): Long).concat
    Sample(o, 0, 0, Zero, Zero)
  }

  fixture.test("on complete the key should get recycled") { implicit s =>
    var received = 0
    var wasCompleted = 0
    var fallbackTick = 0
    var nextShouldCancel = false

    def fallbackObservable: Observable[Nothing] =
      Observable.empty.doOnSubscribeF { () =>
        fallbackTick += 1
      }

    val ch = PublishSubject[Int]()
    val obs = ch
      .groupBy(_ % 2)
      .mergeMap(
        _.timeoutOnSlowUpstream(10.seconds)
          .onErrorFallbackTo(fallbackObservable)
      )

    obs.unsafeSubscribeFn(new Observer[Int] {
      def onNext(elem: Int): Future[Ack] =
        if (nextShouldCancel) Stop
        else {
          received += elem
          Continue
        }

      def onError(ex: Throwable): Unit = ()
      def onComplete(): Unit = { wasCompleted += 1 }
    })

    ch.onNext(1); s.tick()
    assertEquals(received, 1)
    // at this point it should timeout
    s.tick(10.second)
    assertEquals(received, 1)
    assertEquals(fallbackTick, 1)

    ch.onNext(11); s.tick()
    assertEquals(received, 12)
    assertEquals(fallbackTick, 1)
    // at this point it should timeout again
    s.tick(10.second)
    assertEquals(received, 12)
    assertEquals(fallbackTick, 2)

    nextShouldCancel = true
    // this should have no effect
    ch.onNext(21); s.tick()
    assertEquals(received, 12)
    s.tick(10.second)
    assertEquals(fallbackTick, 3)
  }

  fixture.test("on error groups should also error") { implicit s =>
    var groupsErrored = 0

    Observable(1, 2, 3)
      .mapEval {
        case n if n < 3 => Task.pure(n)
        case _ => Task.raiseError(new RuntimeException)
      }
      .groupBy(identity)
      .mapEval(_.completedL.onErrorHandleWith(_ => Task(groupsErrored += 1)))
      .runAsyncGetLast

    s.tick()
    assertEquals(groupsErrored, 2)
  }

  override def cancelableObservables() = {
    val sample = Observable.range(0, 100).delayOnNext(1.second).groupBy(_ % 5).concat
    Seq(Sample(sample, 0, 0, 0.second, 0.second))
  }
}
