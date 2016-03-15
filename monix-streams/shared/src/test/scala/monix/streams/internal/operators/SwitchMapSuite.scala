/*
 * Copyright (c) 2014-2016 by its authors. Some rights reserved.
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

package monix.streams.internal.operators

import monix.streams.Observable
import scala.concurrent.duration._

object SwitchMapSuite extends BaseOperatorSuite {
  def createObservable(sourceCount: Int) = Some {
    val mainPeriod = 2.seconds + 500.millis
    val o = Observable.interval(mainPeriod)
      .switchMap(i => Observable.interval(1.second))
      .bufferTimed(mainPeriod).map(_.sum)
      .take(sourceCount)

    val sum = 3 * sourceCount
    Sample(o, sourceCount, sum, waitFirst, waitNext)
  }

  def waitFirst = 2.5.seconds
  def waitNext = 2.5.seconds

  def observableInError(sourceCount: Int, ex: Throwable) = Some {
    val mainPeriod = 2.seconds + 500.millis
    val o = createObservableEndingInError(Observable.interval(mainPeriod).take(sourceCount), ex)
      .switchMap(i => Observable.interval(1.second))
      .bufferTimed(mainPeriod).map(_.sum)

    val sum = 3 * (sourceCount-1)
    Sample(o, sourceCount-1, sum, waitFirst, waitNext)
  }

  def brokenUserCodeObservable(sourceCount: Int, ex: Throwable) = Some {
    val o = Observable.interval(2.5.seconds).switchMap(i => throw ex)
    Sample(o, 0, 0, waitFirst, waitNext)
  }

  override def cancelableObservables(): Seq[Sample] = {
    val sample1 = {
      val source = Observable.now(1L).delayOnComplete(2.seconds)
      source.switchMap(a => Observable.now(a).delaySubscription(1.second))
    }

    val sample2 = {
      val source = Observable.now(1L).delayOnNext(1.second).delayOnComplete(2.seconds)
      source.switchMap(a => Observable.now(a).delaySubscription(1.second))
    }

    Seq(
      Sample(sample1, 0, 0, 0.seconds, 0.seconds),
      Sample(sample1, 1, 1, 1.seconds, 0.seconds),
      Sample(sample2, 0, 0, 0.seconds, 0.seconds),
      Sample(sample2, 0, 0, 1.seconds, 0.seconds),
      Sample(sample2, 1, 1, 2.seconds, 0.seconds)
    )
  }

  test("source.switchMap(unit) == source") { implicit s =>
    val source = Observable.range(0, 100)
    val switched = source.switchMap(i => Observable.now(i))

    val r1 = source.foldLeftF(Seq.empty[Long])(_ :+ _).asFuture
    val r2 = switched.foldLeftF(Seq.empty[Long])(_ :+ _).asFuture
    s.tick()

    assertEquals(r2.value.get, r1.value.get)
  }
}
