/*
 * Copyright (c) 2014-2015 by its authors. Some rights reserved.
 * See the project homepage at: http://www.monifu.org
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

package monifu.reactive.operators

import monifu.reactive.Ack.{Continue, Cancel}
import monifu.reactive.{Observer, Observable}
import concurrent.duration._

object DebounceSuite extends BaseOperatorSuite {
  def waitFirst = 1.second
  def waitNext = 1.second

  def observable(sourceCount: Int) = Some {
    val source = Observable.create[Long](_.observer.onNext(1L))
    val o = source.debounce(1.second).take(sourceCount)
    Sample(o, count(sourceCount), sum(sourceCount), waitFirst, waitNext)
  }

  def count(sourceCount: Int) = {
    sourceCount
  }

  def sum(sourceCount: Int) = {
    sourceCount
  }

  def observableInError(sourceCount: Int, ex: Throwable) = None
  def brokenUserCodeObservable(sourceCount: Int, ex: Throwable) = None

  test("should cancel the source when downstream cancels") { implicit s =>
    var wasCanceled = false
    var wasCompleted = false
    var received = 0L
    val source = Observable.unit(1L) ++ Observable.unitDelayed(10.seconds + 500.millis, 2L)
    val obs = source.doOnCanceled { wasCanceled = true }.debounce(1.second)

    obs.subscribe(new Observer[Long] {
      def onNext(elem: Long) = {
        received += elem
        if (received == 10) Cancel else Continue
      }

      def onError(ex: Throwable) = ()
      def onComplete() = { wasCompleted = true }
    })

    s.tick(10.seconds)
    assertEquals(received, 10)
    assertEquals(wasCanceled, false)

    s.tick(500.millis)
    assertEquals(received, 10)
    assertEquals(wasCanceled, true)
    assertEquals(wasCompleted, false)
  }
}
