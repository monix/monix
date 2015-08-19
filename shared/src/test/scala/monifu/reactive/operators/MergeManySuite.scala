/*
 * Copyright (c) 2014-2015 Alexandru Nedelcu
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

import monifu.reactive.Observable
import scala.concurrent.duration.Duration.Zero

object MergeManySuite extends BaseOperatorSuite {
  def observable(sourceCount: Int) = Some {
    val o = Observable.range(0, sourceCount)
      .mergeMap(i => Observable.fromIterable(Seq(i,i,i,i)))
    Sample(o, count(sourceCount), sum(sourceCount), Zero, Zero)
  }

  def count(sourceCount: Int) =
    4 * sourceCount

  def observableInError(sourceCount: Int, ex: Throwable) = Some {
    val o = createObservableEndingInError(Observable.range(0, sourceCount), ex)
      .mergeMap(i => Observable.fromIterable(Seq(i, i, i, i)))
    Sample(o, count(sourceCount), sum(sourceCount), Zero, Zero)
  }

  def sum(sourceCount: Int) = {
    4L * sourceCount * (sourceCount - 1) / 2
  }

  def brokenUserCodeObservable(sourceCount: Int, ex: Throwable) = Some {
    val o = Observable.range(0, sourceCount).mergeMap { i =>
      if (i == sourceCount-1)
        throw ex
      else
        Observable.fromIterable(Seq(i,i,i,i))
    }

    Sample(o, count(sourceCount-1), sum(sourceCount-1), Zero, Zero)
  }
}

