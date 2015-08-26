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

package monifu.reactive.internals.operators

import monifu.concurrent.atomic.Atomic
import monifu.reactive.Observable
import monifu.reactive.Observable.unit
import scala.concurrent.duration.Duration.Zero

object DoOnCompleteSuite extends BaseOperatorSuite {
  def observable(sourceCount: Int) = Some {
    val o = Observable.create[Long] { s =>
      implicit val ec = s.scheduler
      val sum = Atomic(0L)

      Observable.range(0, sourceCount)
        .doWork(sum.add)
        .doOnComplete(unit(sum.get).subscribe(s.observer))
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
    val o = Observable.unit((0 until sourceCount).sum.toLong).doOnComplete(throw ex)
    Sample(o, count(sourceCount), sum(sourceCount), Zero, Zero)
  }
}
