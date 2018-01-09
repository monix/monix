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
import monix.reactive.Notification.{OnComplete, OnError, OnNext}
import monix.execution.exceptions.DummyException
import monix.reactive.{Notification, Observable, Observer}
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.concurrent.duration.Duration.Zero

object MaterializeSuite extends BaseOperatorSuite {
  def createObservable(sourceCount: Int) = Some {
    val o = Observable.range(0, sourceCount).materialize.map {
      case OnNext(x) => x
      case OnComplete => 10L
      case OnError(ex) => throw ex
    }

    Sample(o, count(sourceCount), sum(sourceCount), Zero, Zero)
  }

  def sum(sourceCount: Int): Long = sourceCount.toLong * (sourceCount - 1) / 2 + 10
  def count(sourceCount: Int) = sourceCount + 1

  def brokenUserCodeObservable(sourceCount: Int, ex: Throwable) = None
  def observableInError(sourceCount: Int, ex: Throwable) = None

  override def cancelableObservables(): Seq[MaterializeSuite.Sample] = {
    val o = Observable.range(0, 100).delayOnNext(1.second).materialize.map {
      case OnNext(x) => x
      case OnComplete => 10L
      case OnError(ex) => throw ex
    }

    Seq(Sample(o,0,0,0.seconds,0.seconds))
  }

  test("materializeAttempt error") { implicit s =>
    val dummyEx = DummyException("dummy")
    val o = (Observable.now(1) ++ Observable.raiseError(dummyEx)).materialize
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
