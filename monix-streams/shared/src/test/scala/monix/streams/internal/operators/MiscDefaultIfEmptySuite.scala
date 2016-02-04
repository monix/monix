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

import monix.streams.{Observer, Observable, Ack}
import monix.streams.Ack.Continue
import monix.streams.exceptions.DummyException
import monix.streams.Observer
import scala.concurrent.duration.Duration.Zero
import scala.util.Success

object MiscDefaultIfEmptySuite extends BaseOperatorSuite {
  def createObservable(sourceCount: Int) = Some {
    val o = (Observable.empty : Observable[Long])
      .defaultIfEmpty(222L)

    Sample(o, count(sourceCount), sum(sourceCount), Zero, Zero)
  }

  def count(sourceCount: Int) = 1
  def sum(sourceCount: Int) = 222L
  def brokenUserCodeObservable(sourceCount: Int, ex: Throwable) = None

  def observableInError(sourceCount: Int, ex: Throwable) = Some {
    val o = Observable.unsafeCreate[Long] { subscriber =>
      implicit val s = subscriber.scheduler

      subscriber.onNext(222L).onComplete {
        case Success(Continue) =>
          Observable.error(DummyException("dummy"))
            .defaultIfEmpty(0L)
            .unsafeSubscribeFn(subscriber)
        case _ =>
          ()
      }
    }

    Sample(o, count(sourceCount), sum(sourceCount), Zero, Zero)
  }

  test("should not emit default if not empty") { implicit s =>
    val obs = Observable.now(1).defaultIfEmpty(2)
    var received = 0
    var wasCompleted = false

    obs.unsafeSubscribeFn(new Observer[Int] {
      def onError(ex: Throwable) = ()

      def onComplete() = {
        wasCompleted = true
      }

      def onNext(elem: Int) = {
        received += elem
        Continue
      }
    })

    assertEquals(received, 1)
    assert(wasCompleted)
  }
}
