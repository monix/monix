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

package monix.streams.internal.operators

import monix.streams.Ack.{Continue, Cancel}
import monix.streams.Notification.{OnComplete, OnError, OnNext}
import monix.streams.exceptions.DummyException
import monix.streams.{Ack, Notification, Observer, Observable}
import scala.concurrent.Future
import scala.concurrent.duration.Duration.Zero

object MaterializeSuite extends BaseOperatorSuite {
  def createObservable(sourceCount: Int) = Some {
    val o = Observable.unsafeCreate[Long] { subscriber =>
      import subscriber.scheduler

      val source: Observable[Notification[Long]] =
        Observable.range(0, sourceCount).materialize

      source.subscribe(new Observer[Notification[Long]] {
        def onError(ex: Throwable) = ()
        def onComplete() = ()

        def onNext(elem: Notification[Long]) = elem match {
          case OnNext(e) =>
            subscriber.onNext(e)
          case OnError(ex) =>
            subscriber.onError(ex)
            Cancel
          case OnComplete =>
            subscriber.onComplete()
            Cancel
        }
      })
    }

    Sample(o, count(sourceCount), sum(sourceCount), Zero, Zero)
  }

  def sum(sourceCount: Int): Long = sourceCount.toLong * (sourceCount - 1) / 2
  def count(sourceCount: Int) = sourceCount

  def brokenUserCodeObservable(sourceCount: Int, ex: Throwable) = None
  def observableInError(sourceCount: Int, ex: Throwable) = None

  test("materialize error") { implicit s =>
    val dummyEx = DummyException("dummy")
    val o = (Observable.now(1) ++ Observable.error(dummyEx)).materialize
    var received = 0
    var errorThrown: Throwable = null
    var isComplete = false

    o.unsafeSubscribeFn(new Observer[Notification[Int]] {
      def onNext(elem: Notification[Int]): Future[Ack] = {
        elem match {
          case Notification.OnNext(int) => received += int
          case Notification.OnError(ex) => errorThrown = ex
          case Notification.OnComplete => ()
        }
        Continue
      }

      def onComplete(): Unit = isComplete = true
      def onError(ex: Throwable): Unit = throw ex
    })

    s.tick()
    assertEquals(received, 1)
    assertEquals(errorThrown, dummyEx)
    assertEquals(isComplete, true)
  }
}
