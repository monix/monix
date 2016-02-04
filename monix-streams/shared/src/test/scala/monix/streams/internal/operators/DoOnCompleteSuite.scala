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

import org.sincron.atomic.Atomic
import monix.streams.Observable
import Observable.now
import monix.streams.Observable
import scala.concurrent.duration.Duration.Zero

object DoOnCompleteSuite extends BaseOperatorSuite {
  def createObservable(sourceCount: Int) = Some {
    val o = Observable.unsafeCreate[Long] { s =>
      import s.scheduler
      val sum = Atomic(0L)

      Observable.range(0, sourceCount)
        .doWork(sum.add)
        .doOnComplete(now(sum.get).subscribe(s))
        .subscribe()
    }

    Sample(o, count(sourceCount), sum(sourceCount), Zero, Zero)
  }

  def count(sourceCount: Int) = 1
  def observableInError(sourceCount: Int, ex: Throwable) = None

  def sum(sourceCount: Int) = {
    (0 until sourceCount).sum
  }

  def brokenUserCodeObservable(sourceCount: Int, ex: Throwable) = Some {
    val o = Observable.now((0 until sourceCount).sum.toLong).doOnComplete(throw ex)
    Sample(o, count(sourceCount), sum(sourceCount), Zero, Zero)
  }
}
