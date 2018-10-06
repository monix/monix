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
import monix.execution.exceptions.DummyException
import monix.reactive.{Observable, Observer}

import scala.concurrent.Future
import scala.concurrent.duration._

object DelayExecutionSuite extends BaseOperatorSuite {
  def createObservable(sourceCount: Int) = Some {
    val o = Observable.range(0, sourceCount)
      .delayExecution(1.second)

    Sample(o, count(sourceCount), sum(sourceCount), waitFirst, waitNext)
  }

  def count(sourceCount: Int) = sourceCount
  def sum(sourceCount: Int) = sourceCount * (sourceCount - 1) / 2
  def waitFirst = 1.second
  def waitNext = Duration.Zero

  def observableInError(sourceCount: Int, ex: Throwable) = None
  def brokenUserCodeObservable(sourceCount: Int, ex: Throwable) = None



  test("it delays") { implicit s =>
    val obs = Observable.now(1).delayExecution(1.second)
    var wasCompleted = false
    var received = 0

    obs.unsafeSubscribeFn(new Observer[Int] {
      def onError(ex: Throwable) = ()
      def onComplete() = wasCompleted = true

      def onNext(elem: Int) = {
        received += elem
        Continue
      }
    })

    s.tick()
    assertEquals(received, 0)
    s.tick(1.second)
    assertEquals(received, 1)
    assert(wasCompleted)
  }

  test("delayExecution.onFuture triggering an error") { implicit s =>
    val obs = Observable.now(1).delayExecutionWithF(Future { throw DummyException("dummy") })

    var errorThrown: Throwable = null
    obs.unsafeSubscribeFn(new Observer[Int] {
      def onError(ex: Throwable) =
        errorThrown = ex
      def onComplete() = ()
      def onNext(elem: Int) = Continue
    })

    assertEquals(errorThrown, null)

    s.tick()
    assertEquals(errorThrown, DummyException("dummy"))
  }

  def cancelableObservables() = {
    val o = Observable.range(0, 10).delayExecution(1.second)
    Seq(Sample(o, 0, 0, 0.seconds, 0.seconds))
  }
}
