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
import monix.eval.Task

import scala.concurrent.duration._
import scala.util.Random

object DropUntilSuite extends BaseOperatorSuite {
  val waitFirst = 2500.millis
  val waitNext = 500.millis

  def sum(sourceCount: Int) =
    (0 until sourceCount).map(_ + 5).sum
  def count(sourceCount: Int) =
    sourceCount

  def createObservable(sourceCount: Int) = Some {
    require(sourceCount > 0, "sourceCount should be strictly positive")

    val signal = Task.eval(1).delayExecution(2300.millis)
    val o = Observable.intervalAtFixedRate(500.millis)
      .take(sourceCount + 5)
      .dropUntil(Observable.fromTask(signal))

    Sample(o, count(sourceCount), sum(sourceCount), waitFirst, waitNext)
  }

  def observableInError(sourceCount: Int, ex: Throwable) = {
    require(sourceCount > 0, "sourceCount should be strictly positive")
    Some {
      val source = Observable.intervalAtFixedRate(500.millis)
        .take(sourceCount + 5)

      val signal = Task.eval(1).delayExecution(2300.millis)
      val o = createObservableEndingInError(source, ex)
        .dropUntil(Observable.fromTask(signal))

      Sample(o, count(sourceCount), sum(sourceCount), waitFirst, waitNext)
    }
  }

  def brokenUserCodeObservable(sourceCount: Int, ex: Throwable) = None

  override def cancelableObservables(): Seq[Sample] = {
    val signal = Task.eval(1).delayExecution(2300.millis)
    val o = Observable.intervalAtFixedRate(500.millis)
      .dropUntil(Observable.fromTask(signal))

    Seq(Sample(o, 0, 0, 0.seconds, 0.seconds))
  }

  test("if signal ends in error, then end in error") { implicit s =>
    var received = 0
    var errorThrown: Throwable = null
    val sourceCount = Random.nextInt(300) + 100
    val dummy = DummyException("dummy")

    val signal = Task.raiseError(dummy).delayExecution(2300.millis)
    val o = Observable.intervalAtFixedRate(500.millis)
      .take(sourceCount + 5)
      .dropUntil(Observable.fromTask(signal))

    o.unsafeSubscribeFn(new Observer[Long] {
      def onNext(elem: Long) = {
        received += 1
        Continue
      }

      def onError(ex: Throwable): Unit =
        errorThrown = ex

      def onComplete(): Unit =
        throw new IllegalStateException
    })

    s.tick(2500.millis)
    assertEquals(received, 0)
    assertEquals(errorThrown, dummy)
  }
}
