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
import scala.util.{Failure, Success}

object BufferWithSelectorSuite extends BaseOperatorSuite {
  val waitNext = 1.second

  def count(sourceCount: Int) = {
    sourceCount / 10 + (if (sourceCount % 10 == 0) 0 else 1)
  }

  def sum(sourceCount: Int) = {
    sourceCount * (sourceCount - 1) / 2
  }

  def createObservable(sourceCount: Int) = Some {
    require(sourceCount > 0, "sourceCount must be strictly positive")
    val o = Observable.range(0, sourceCount)
      .bufferTimedWithPressure(waitNext, 10)
      .map(_.sum)

    Sample(o, count(sourceCount), sum(sourceCount), waitNext, waitNext)
  }

  def observableInError(sourceCount: Int, ex: Throwable) = Some {
    require(sourceCount > 0, "sourceCount must be strictly positive")
    val cnt = (sourceCount / 10) * 10 + 1
    val o = Observable.range(0, cnt).endWithError(ex)
      .bufferTimedWithPressure(waitNext, 10)
      .map(_.sum)

    Sample(o, count(cnt-1), sum(cnt-1), waitNext, waitNext)
  }

  override def cancelableObservables() = {
    val o = Observable.range(0, 1000)
      .bufferTimedWithPressure(waitNext, 10)
      .map(_.sum)

    Seq(
      Sample(o, 0, 0, 0.seconds, 0.seconds),
      Sample(o, 1, 5 * 9, waitNext, 0.seconds)
    )
  }

  def brokenUserCodeObservable(sourceCount: Int, ex: Throwable) = None

  test("selector onComplete should complete the resulting observable") { implicit s =>
    val selector = Observable.intervalAtFixedRate(1.second, 1.second).take(2)
    val f = Observable.range(0, 10000)
      .bufferWithSelector(selector, 10)
      .map(_.sum)
      .sumF
      .runAsyncGetFirst

    s.tick(2.seconds)
    assertEquals(f.value, Some(Success(Some(10 * 19))))
  }

  test("selector onError should terminate the resulting observable") { implicit s =>
    val ex = DummyException("dummy")
    val selector = Observable.intervalAtFixedRate(1.second, 1.second)
      .take(2).endWithError(ex)

    val f = Observable.range(0, 10000)
      .bufferWithSelector(selector, 10)
      .map(_.sum)
      .sumF
      .runAsyncGetFirst

    s.tick(2.seconds)
    assertEquals(f.value, Some(Failure(ex)))
  }
}
