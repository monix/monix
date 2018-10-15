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

import cats.laws._
import cats.laws.discipline._
import cats.effect.IO
import monix.reactive.Observable
import scala.concurrent.duration._

object ScanEffectSuite extends BaseOperatorSuite {
  def createObservable(sourceCount: Int) = Some {
    val o = Observable.range(0, sourceCount).scanEvalF(IO.pure(0L)) {
      (s, x) => IO(s + x)
    }

    Sample(o, count(sourceCount), sum(sourceCount), waitFirst, waitNext)
  }

  def count(sourceCount: Int) =
    sourceCount
  def sum(sourceCount: Int) =
    0.until(sourceCount).scan(0)(_ + _).sum

  def waitFirst = Duration.Zero
  def waitNext = Duration.Zero

  def observableInError(sourceCount: Int, ex: Throwable) =
    if (sourceCount == 1) None else Some {
      val o = createObservableEndingInError(Observable.range(0, sourceCount), ex)
        .scanEvalF(IO.pure(0L)) { (s, x) => IO(s + x) }

      Sample(o, count(sourceCount), sum(sourceCount), waitFirst, waitNext)
    }

  def brokenUserCodeObservable(sourceCount: Int, ex: Throwable) = Some {
    val o = Observable.range(0, sourceCount)
      .scanEvalF(IO.pure(0L)) { (s, i) =>
        if (i == sourceCount-1)
          throw ex
        else
          IO(s + i)
      }

    Sample(o, count(sourceCount-1), sum(sourceCount-1), waitFirst, waitNext)
  }

  override def cancelableObservables(): Seq[Sample] = {
    val sample = Observable.range(0, 100)
      .delayOnNext(1.second)
      .scanEvalF(IO.pure(0L))((s, i) => IO(s + i))

    Seq(
      Sample(sample, 0, 0, 0.seconds, 0.seconds),
      Sample(sample, 1, 1, 1.seconds, 0.seconds)
    )
  }


  test("scanEval0.headL.toIO <-> seed") { implicit s =>
    check2 { (obs: Observable[Int], seed: IO[Int]) =>
      obs.scanEval0F(seed)((a, b) => IO.pure(a + b)).headL.toIO <-> seed
    }
  }

  test("scanEval0.drop(1) <-> scanEval") { implicit s =>
    check2 { (obs: Observable[Int], seed: IO[Int]) =>
      obs.scanEval0F(seed)((a, b) => IO.pure(a + b)).drop(1) <-> obs.scanEvalF(seed)((a, b) => IO.pure(a + b))
    }
  }
}
