/*
 * Copyright (c) 2014-2021 by The Monix Project Developers.
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
import scala.util.Failure

object FoldWhileObservableSuite extends BaseOperatorSuite {
  def createObservable(sourceCount: Int) = Some {
    val n = sourceCount / 2
    val obs =
      Observable
        .range(0L, sourceCount.toLong)
        .foldWhileLeft(0L)((acc, e) => if (e < n) Left(acc + e) else Right(acc + e))

    Sample(obs, 1, n * (n + 1) / 2, 0.seconds, 0.seconds)
  }

  def observableInError(sourceCount: Int, ex: Throwable) = Some {
    val obs = Observable
      .range(0L, sourceCount.toLong)
      .endWithError(ex)
      .foldWhileLeft(0L)((acc, e) => Left(acc + e))

    Sample(obs, 0, 0, 0.seconds, 0.seconds)
  }

  def cancelableObservables() = {
    val obs = Observable
      .range(0, 1000)
      .delayExecution(1.seconds)
      .foldWhileLeft(0L)((acc, e) => Left(acc + e))

    Seq(Sample(obs, 0, 0, 0.seconds, 0.seconds))
  }

  def brokenUserCodeObservable(sourceCount: Int, ex: Throwable) = Some {
    val obs = Observable
      .range(0L, sourceCount.toLong)
      .endWithError(ex)
      .foldWhileLeft(0L)((_, _) => throw ex)

    Sample(obs, 0, 0, 0.seconds, 0.seconds)
  }

  test("should trigger error if the initial state triggers errors") { implicit s =>
    val ex = DummyException("dummy")
    val obs = Observable(1, 2, 3, 4)
      .foldWhileLeft((throw ex): Int)((acc, e) => Left(acc + e))

    val f = obs.runAsyncGetFirst; s.tick()
    assertEquals(f.value, Some(Failure(ex)))
  }
}
