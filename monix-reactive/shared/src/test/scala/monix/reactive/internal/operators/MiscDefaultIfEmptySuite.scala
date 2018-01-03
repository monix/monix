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

import monix.execution.Ack.Continue
import monix.reactive.{Observable, Observer}
import scala.concurrent.duration._
import scala.concurrent.duration.Duration.Zero

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
    val o = Observable.raiseError(ex).defaultIfEmpty(10L)
    Sample(o, 0, 0, Zero, Zero)
  }

  override def cancelableObservables() = {
    val o = Observable.empty.delayOnComplete(1.second).defaultIfEmpty(1L)
    Seq(Sample(o, 0, 0, 0.seconds, 0.seconds))
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
