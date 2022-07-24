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
import scala.util.Success

class TransformerSuite extends BaseOperatorSuite {
  def sum(sourceCount: Int): Long = (0 until sourceCount).sum.toLong * 2
  def count(sourceCount: Int) = sourceCount

  def dummyTransformer(ob: Observable[Long]): Observable[Long] =
    ob.map(_ * 2)
  def aT(ob: Observable[Long]): Observable[Long] =
    ob.map(_ * 1)

  def createObservable(sourceCount: Int) = Some {
    val o =
      Observable
        .range(0L, sourceCount.toLong)
        .transform(dummyTransformer)

    Sample(o, count(sourceCount), sum(sourceCount), Zero, Zero)
  }

  def observableInError(sourceCount: Int, ex: Throwable) = {
    require(sourceCount > 0, "sourceCount should be strictly positive")
    Some {
      val ex = DummyException("dummy")
      val o =
        if (sourceCount == 1)
          createObservableEndingInError(Observable.now(1L), ex).transform(dummyTransformer)
        else
          createObservableEndingInError(Observable.range(1, sourceCount.toLong + 1, 1), ex)
            .transform(dummyTransformer)

      Sample(o, count(sourceCount), sum(sourceCount), Zero, Zero)
    }
  }

  def brokenUserCodeObservable(sourceCount: Int, ex: Throwable) =
    Option.empty

  override def cancelableObservables(): Seq[Sample] = {
    val obs = Observable.range(0, 1000).delayOnNext(1.second).map(_ + 1)
    Seq(Sample(obs, 0, 0, 0.seconds, 0.seconds))
  }

  fixture.test("should transform the observable using the given transformer") { implicit s =>
    val l = List(1L, 2, 3, 4)
    val f = Observable.from(l).transform(dummyTransformer).toListL.runToFuture
    s.tick()
    assertEquals(f.value, Some(Success(l.map(_ * 2))))
  }
}
