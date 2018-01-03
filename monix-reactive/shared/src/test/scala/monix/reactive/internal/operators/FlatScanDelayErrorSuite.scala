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

import monix.execution.exceptions.{CompositeException, DummyException}
import monix.reactive.Observable
import scala.concurrent.duration._
import scala.concurrent.duration.Duration._
import scala.util.Failure

object FlatScanDelayErrorSuite extends BaseOperatorSuite {
  case class SomeException(value: Long) extends RuntimeException

  def create(sourceCount: Int, ex: Throwable = null) = Some {
    val source = if (ex == null) Observable.range(0, sourceCount)
      else Observable.range(0, sourceCount).endWithError(ex)

    val o = source.flatScanDelayErrors(1L)((acc, elem) =>
      Observable.repeat(acc + elem).take(3)
        .endWithError(SomeException(10)))

    val recovered = o.onErrorHandleWith {
      case composite: CompositeException =>
        val sum = composite
          .errors.collect { case ex: SomeException => ex.value }
          .sum

        Observable.now(sum)
    }

    val sum = (0 until sourceCount).map(x => (1 to x).sum + 1L).sum * 3 +
      sourceCount * 10

    Sample(recovered, sourceCount * 3 + 1, sum, Zero, Zero)
  }

  def createObservable(sourceCount: Int) = create(sourceCount)
  def observableInError(sourceCount: Int, ex: Throwable) = None
  def brokenUserCodeObservable(sourceCount: Int, ex: Throwable) = None

  override def cancelableObservables() = {
    val sample1 = Observable.range(0, 10)
      .flatScanDelayErrors(1L)((acc,e) => Observable.now(acc+e).delaySubscription(1.second))
    val sample2 = Observable.range(0, 10).delayOnNext(1.second)
      .flatScanDelayErrors(1L)((acc,e) => Observable.now(acc+e).delaySubscription(1.second))

    Seq(
      Sample(sample1,0,0,0.seconds,0.seconds),
      Sample(sample2,0,0,0.seconds,0.seconds)
    )
  }

  test("should trigger error if the initial state triggers errors") { implicit s =>
    val ex = DummyException("dummy")
    val obs = Observable(1,2,3,4).flatScanDelayErrors[Int](throw ex)((_,e) => Observable(e))
    val f = obs.runAsyncGetFirst; s.tick()
    assertEquals(f.value, Some(Failure(ex)))
  }
}
