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

import scala.concurrent.duration.Duration.Zero

object MergeManySuite extends BaseOperatorSuite {
  def createObservable(sourceCount: Int) = Some {
    val o = Observable.range(0, sourceCount)
      .mergeMap(i => Observable.fromIterable(Seq(i,i,i,i)))
    Sample(o, count(sourceCount), sum(sourceCount), Zero, Zero)
  }

  def count(sourceCount: Int) =
    4 * sourceCount

  def observableInError(sourceCount: Int, ex: Throwable) = Some {
    val o = createObservableEndingInError(Observable.range(0, sourceCount), ex)
      .mergeMap(i => Observable.now(i))
    Sample(o, sourceCount, sourceCount * (sourceCount - 1) / 2, Zero, Zero)
  }

  def sum(sourceCount: Int) = {
    4L * sourceCount * (sourceCount - 1) / 2
  }

  def brokenUserCodeObservable(sourceCount: Int, ex: Throwable) = Some {
    val o = Observable.range(0, sourceCount)
      .mergeMap(x => if (x == 1) throw ex else Observable.now(1L))
    Sample(o, 1, 1, Zero, Zero)
  }
}

