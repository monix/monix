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

import monix.execution.exceptions.DummyException
import monix.reactive.Observable

import scala.concurrent.duration.Duration.Zero
import scala.concurrent.duration._
import scala.util.Failure

object MapAccumulateSuite extends BaseOperatorSuite {
  def createObservable(sourceCount: Int) = Some {
    val o = Observable
      .range(0L, sourceCount.toLong)
      .mapAccumulate(0L)((acc, elem) => (acc + elem, acc * elem))
    Sample(o, count(sourceCount), sum(sourceCount), Zero, Zero)
  }

  def count(sourceCount: Int) =
    sourceCount

  def observableInError(sourceCount: Int, ex: Throwable) = Some {
    val o = createObservableEndingInError(Observable.range(0L, sourceCount.toLong), ex)
      .mapAccumulate(0L)((acc, elem) => (acc + elem, acc * elem))

    Sample(o, count(sourceCount), sum(sourceCount), Zero, Zero)
  }

  def sum(sourceCount: Int) = {
    (0 until sourceCount)
      .map(c => (0 until c).map(_.toLong).sum * c.toLong)
      .sum
  }

  def brokenUserCodeObservable(sourceCount: Int, ex: Throwable) = Some {
    val o = Observable.range(0L, sourceCount.toLong).mapAccumulate(0L) { (acc, elem) =>
      if (elem == sourceCount - 1)
        throw ex
      else
        (acc + elem, acc * elem)
    }

    Sample(o, count(sourceCount - 1), sum(sourceCount - 1), Zero, Zero)
  }

  override def cancelableObservables() = {
    val sample = Observable
      .range(1, 100)
      .delayOnNext(1.second)
      .mapAccumulate(0L)((acc, elem) => (acc + elem, acc * elem))

    Seq(Sample(sample, 0, 0, 0.seconds, 0.seconds))
  }

  test("should trigger error if the initial state triggers errors") { implicit s =>
    val ex = DummyException("dummy")
    val obs = Observable(1, 2, 3, 4).mapAccumulate[Int, Int](throw ex)((acc, elem) => (acc + elem, acc * elem))
    val f = obs.runAsyncGetFirst; s.tick()
    assertEquals(f.value, Some(Failure(ex)))
  }
}
