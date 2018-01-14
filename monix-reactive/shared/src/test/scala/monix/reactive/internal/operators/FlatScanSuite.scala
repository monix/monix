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

import monix.reactive.Observable
import monix.execution.exceptions.DummyException
import scala.concurrent.duration._
import scala.concurrent.duration.Duration.Zero
import scala.util.Failure

object FlatScanSuite extends BaseOperatorSuite {
  def createObservable(sourceCount: Int) = Some {
    val o = Observable.range(0, sourceCount)
      .flatScan(1L)((acc, elem) => Observable.repeat(acc + elem).take(3))

    val sum = (0 until sourceCount).map(x => (1 to x).sum + 1L).sum * 3
    Sample(o, sourceCount * 3, sum, Zero, Zero)
  }

  def observableInError(sourceCount: Int, ex: Throwable) =
    if (sourceCount == 1) None else Some {
      val o = Observable.range(0, sourceCount).endWithError(ex)
        .flatScan(1L)((acc, elem) => Observable.fromIterable(Seq(1L,1L,1L)))

      Sample(o, sourceCount * 3 - 2, sourceCount * 3 - 2, Zero, Zero)
    }

  def brokenUserCodeObservable(sourceCount: Int, ex: Throwable) = Some {
    val o = Observable.range(0, sourceCount+1).flatScan(1L) { (acc, elem) =>
      if (elem == sourceCount) throw ex else
        Observable.repeat(acc + elem).take(3)
    }

    val sum = (0 until sourceCount).map(x => (1 to x).sum + 1L).sum * 3
    Sample(o, sourceCount * 3, sum, Zero, Zero)
  }

  override def cancelableObservables() = {
    val sample1 = Observable.range(0, 10)
      .flatScan(1L)((acc,e) => Observable.now(acc+e).delaySubscription(1.second))
    val sample2 = Observable.range(0, 10).delayOnNext(1.second)
      .flatScan(1L)((acc,e) => Observable.now(acc+e).delaySubscription(1.second))

    Seq(
      Sample(sample1,0,0,0.seconds,0.seconds),
      Sample(sample2,0,0,0.seconds,0.seconds)
    )
  }

  test("should trigger error if the initial state triggers errors") { implicit s =>
    val ex = DummyException("dummy")
    val obs = Observable(1,2,3,4).flatScan[Int](throw ex)((_,e) => Observable(e))
    val f = obs.runAsyncGetFirst; s.tick()
    assertEquals(f.value, Some(Failure(ex)))
  }
}
