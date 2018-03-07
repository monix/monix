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
import monix.execution.exceptions.UpstreamTimeoutException
import monix.reactive.{Observable, Observer}
import monix.execution.exceptions.DummyException
import monix.reactive.subjects.PublishSubject
import scala.concurrent.TimeoutException
import scala.concurrent.duration._

object TimeoutOnSlowUpstreamSuite extends BaseOperatorSuite {
  def createObservable(sourceCount: Int) = Some {
    val source = Observable.now(sourceCount.toLong).delayOnComplete(1.hour)
    val o = source.timeoutOnSlowUpstream(1.second).onErrorHandleWith {
      case UpstreamTimeoutException(_) =>
        Observable.now(20L)
    }

    Sample(o, 2, sourceCount + 20, Duration.Zero, 1.second)
  }

  def observableInError(sourceCount: Int, ex: Throwable) = {
    val ex = DummyException("dummy")
    val source = Observable.now(sourceCount.toLong).endWithError(ex)
    val o = source.timeoutOnSlowUpstream(1.second)
    Some(Sample(o, 1, 1, Duration.Zero, 1.second))
  }

  def brokenUserCodeObservable(sourceCount: Int, ex: Throwable) =
    None

  override def cancelableObservables() = {
    val o = Observable.now(1L).delayOnComplete(1.hour).timeoutOnSlowUpstream(1.second)
    Seq(Sample(o,1,1,0.seconds,0.seconds))
  }

  test("should emit timeout after time passes") { implicit s =>
    val p = PublishSubject[Int]()
    var received = 0
    var errorThrown: Throwable = null

    p.timeoutOnSlowUpstream(10.seconds).subscribe(new Observer.Sync[Int] {
      def onComplete() = ()
      def onError(ex: Throwable) = {
        errorThrown = ex
      }

      def onNext(elem: Int) = {
        received += elem
        Continue
      }
    })

    p.onNext(1)
    assertEquals(received, 1)

    s.tick(9.seconds)
    p.onNext(2)
    assertEquals(received, 3)

    s.tick(9.seconds)
    assertEquals(received, 3)
    assertEquals(errorThrown, null)

    s.tick(1.second)
    assert(errorThrown != null && errorThrown.isInstanceOf[TimeoutException],
      "errorThrown should be a TimeoutException")
  }
}
