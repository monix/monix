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

import monix.eval.Task
import monix.execution.atomic.AtomicInt
import monix.execution.exceptions.DownstreamTimeoutException
import monix.reactive.Observable
import monix.execution.exceptions.DummyException
import scala.concurrent.duration._

class TimeoutOnSlowDownstreamSuite extends BaseOperatorSuite {
  def createObservable(sourceCount: Int) = Some {
    val source = Observable.now(sourceCount.toLong).delayOnComplete(1.hour)
    val o = source
      .timeoutOnSlowDownstream(1.second)
      .delayOnNext(30.minutes)
      .onErrorRecoverWith {
        case DownstreamTimeoutException(_) =>
          Observable.now(20L)
      }
    Sample(o, 1, 20, 1.second, 0.seconds)
  }

  def observableInError(sourceCount: Int, ex: Throwable) = {
    val ex = DummyException("dummy")
    val source = Observable.now(sourceCount.toLong).endWithError(ex)
    val o = source.timeoutOnSlowDownstream(1.second)
    Some(Sample(o, 1, 1, Duration.Zero, 1.second))
  }

  def brokenUserCodeObservable(sourceCount: Int, ex: Throwable) =
    None

  override def cancelableObservables() = {
    val o = Observable
      .now(1L)
      .delayOnNext(30.minutes)
      .delayOnComplete(1.hour)
      .timeoutOnSlowDownstream(1.second)

    Seq(Sample(o, 0, 0, 0.seconds, 0.seconds))
  }

  fixture.test("Observable.timeoutOnSlowDownstreamTo should timeout on slow downstream") { implicit sc =>
    val backup = AtomicInt(0)

    Observable
      .fromIterable(List(1, 2))
      .timeoutOnSlowDownstreamTo(1.second, Observable.fromTask(Task.eval(backup.increment())))
      .delayOnNext(2.seconds)
      .completedL
      .runToFuture

    sc.tick(4.seconds)
    assertEquals(backup.get(), 1)
  }

  fixture.test("Observable.timeoutOnSlowDownstreamTo should complete successfully") { implicit sc =>
    val backup = AtomicInt(0)

    Observable
      .fromIterable(List(1, 2))
      .timeoutOnSlowDownstreamTo(2.seconds, Observable.fromTask(Task.eval(backup.increment())))
      .delayOnNext(1.second)
      .completedL
      .runToFuture

    sc.tick(2.seconds)
    assertEquals(backup.get(), 0)
  }
}
