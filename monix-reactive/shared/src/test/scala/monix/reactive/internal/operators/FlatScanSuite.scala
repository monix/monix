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

import cats.laws._
import cats.laws.discipline._

import monix.eval.Task
import monix.reactive.Observable
import monix.execution.exceptions.DummyException

import scala.concurrent.duration._
import scala.concurrent.duration.Duration.Zero
import scala.util.Failure

class FlatScanSuite extends BaseOperatorSuite {
  def createObservable(sourceCount: Int) = Some {
    val o = Observable
      .range(0L, sourceCount.toLong)
      .flatScan(1L)((acc, elem) => Observable.repeat(acc + elem).take(3L))

    val sum = (0 until sourceCount).map(x => (1 to x).sum + 1L).sum * 3
    Sample(o, sourceCount * 3, sum, Zero, Zero)
  }

  def observableInError(sourceCount: Int, ex: Throwable) =
    if (sourceCount == 1) None
    else
      Some {
        val o = Observable
          .range(0L, sourceCount.toLong)
          .endWithError(ex)
          .flatScan(1L)((acc, elem) => Observable.fromIterable(Seq(1L, 1L, 1L)))

        Sample(o, sourceCount * 3, sourceCount * 3, Zero, Zero)
      }

  def brokenUserCodeObservable(sourceCount: Int, ex: Throwable) = Some {
    val o = Observable.range(0, sourceCount.toLong + 1).flatScan(1L) { (acc, elem) =>
      if (elem == sourceCount)
        throw ex
      else
        Observable.repeat(acc + elem).take(3L)
    }

    val sum = (0 until sourceCount).map(x => (1 to x).sum + 1L).sum * 3
    Sample(o, sourceCount * 3, sum, Zero, Zero)
  }

  override def cancelableObservables() = {
    val sample1 = Observable
      .range(0, 10)
      .flatScan(1L)((acc, e) => Observable.now(acc + e).delayExecution(1.second))
    val sample2 = Observable
      .range(0, 10)
      .delayOnNext(1.second)
      .flatScan(1L)((acc, e) => Observable.now(acc + e).delayExecution(1.second))

    Seq(
      Sample(sample1, 0, 0, 0.seconds, 0.seconds),
      Sample(sample2, 0, 0, 0.seconds, 0.seconds)
    )
  }

  fixture.test("should trigger error if the initial state triggers errors") { implicit s =>
    val ex = DummyException("dummy")
    val obs = Observable(1, 2, 3, 4).flatScan[Int](throw ex)((_, e) => Observable(e))
    val f = obs.runAsyncGetFirst; s.tick()
    assertEquals(f.value, Some(Failure(ex)))
  }

  fixture.test("flatScan0.drop(1) <-> flatScan") { implicit s =>
    check2 { (obs: Observable[Long], seed: Long) =>
      obs.flatScan0(seed)((a, b) => Observable(a, b)).drop(1) <->
        obs.flatScan(seed)((a, b) => Observable(a, b))
    }
  }

  fixture.test("flatScan0.headL <-> Task.pure(seed)") { implicit s =>
    check2 { (obs: Observable[Int], seed: Int) =>
      obs.flatScan0(seed)((_, _) => Observable.empty).headL <-> Task.pure(seed)
    }
  }
}
