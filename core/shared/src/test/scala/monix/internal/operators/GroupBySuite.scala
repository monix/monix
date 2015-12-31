/*
 * Copyright (c) 2014-2016 by its authors. Some rights reserved.
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

package monix.internal.operators

import monix.Ack.{Cancel, Continue}
import monix.{Ack, Observer, Observable}
import monix.subjects.PublishSubject
import scala.concurrent.Future
import scala.concurrent.duration.Duration.Zero
import scala.concurrent.duration._

object GroupBySuite extends BaseOperatorSuite {
  def createObservable(sourceCount: Int) = Some {
    val o = Observable.range(0, sourceCount)
      .groupBy(_ % 5)
      .mergeMap(o => o.map(x => o.key + x))

    Sample(o, count(sourceCount), sum(sourceCount), Zero, Zero)
  }

  def count(sourceCount: Int) =
    sourceCount

  def sum(sourceCount: Int) = {
    (0 until sourceCount).map(x => x + x % 5).sum
  }

  def observableInError(sourceCount: Int, ex: Throwable) = Some {
    val o = createObservableEndingInError(Observable.range(0, sourceCount), ex)
      .groupBy(_ % 5)
      .flatMap(o => o.map(x => o.key + x))

    Sample(o, count(sourceCount), sum(sourceCount), Zero, Zero)
  }

  def brokenUserCodeObservable(sourceCount: Int, ex: Throwable) = Some {
    val o = Observable.range(1, sourceCount)
      .groupBy(x => if (x == 2) throw ex else x)
      .concat

    Sample(o, 1, 1, Zero, Zero)
  }

  test("on complete the key should get recycled") { implicit s =>
    var received = 0
    var wasCompleted = 0
    var fallbackTick = 0
    var nextShouldCancel = false

    def fallbackObservable: Observable[Nothing] =
      Observable.unsafeCreate { s =>
        fallbackTick += 1
        Observable.empty.unsafeSubscribeFn(s)
      }

    val ch = PublishSubject[Int]().groupBy(_ % 2)
      .mergeMap(_.timeout(10.seconds, fallbackObservable))

    ch.unsafeSubscribeFn(new Observer[Int] {
      def onNext(elem: Int): Future[Ack] =
        if (nextShouldCancel) Cancel else {
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
}

