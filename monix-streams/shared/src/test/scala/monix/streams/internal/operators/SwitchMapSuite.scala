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
import concurrent.duration._

object SwitchMapSuite extends BaseOperatorSuite {
  def createObservable(sourceCount: Int) = Some {
    val count = sourceCount / 2 * 2 + 1
    val o = Observable.interval(2.seconds)
      .switchMap(i => Observable.interval(1.second).map(_ => i).take(2) ++
        Observable.empty.delaySubscription(1.second))
      .take(count)

    val sum = (0 until count).flatMap(x => Seq(x,x)).take(count).sum
    Sample(o, count, sum, waitFirst, waitNext)
  }

  def waitFirst = 1.seconds
  def waitNext = 1.second

  def observableInError(sourceCount: Int, ex: Throwable) = Some {
    if (sourceCount > 1) {
      val count = 10
      val source = Observable.now(1).delayOnComplete(count.seconds)
      val o = source.switchMap { x =>
        createObservableEndingInError(
          Observable.interval(1.seconds).take(count), ex)
      }

      val sum = count * (count-1) / 2
      Sample(o, count, sum, 0.seconds, 1.seconds)
    } else {
      val o = Observable.now(1L).delayOnComplete(1.seconds)
        .switchMap(x => Observable.now(x).endWithError(ex))
      Sample(o, 1, 1, 0.seconds, 1.seconds)
    }
  }

  def brokenUserCodeObservable(sourceCount: Int, ex: Throwable) = Some {
    val o = Observable.interval(1.second).switchMap { x =>
      if (x == 2) throw ex else Observable.now(x)
    }

    Sample(o, 2, 1, 1.seconds, 1.seconds)
  }

  test("source.switchMap(unit) == source") { implicit s =>
    val source = Observable.range(0, 100)
    val switched = source.switchMap(i => Observable.now(i))

    val r1 = source.foldLeft(Seq.empty[Long])(_ :+ _).asFuture
    val r2 = switched.foldLeft(Seq.empty[Long])(_ :+ _).asFuture
    s.tick()

    assertEquals(r2.value.get, r1.value.get)
  }
}
