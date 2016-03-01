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
import scala.concurrent.duration.Duration.Zero

object DistinctByKeySuite extends BaseOperatorSuite {
  case class Val(x: Long)
  def createObservable(sourceCount: Int) = Some {
    val seq = (0 until sourceCount)
      .flatMap(i => Seq(Val(i),Val(i),Val(i)))

    val o = Observable.from(seq).distinctByKey(_.x).map(_.x)
    Sample(o, count(sourceCount), sum(sourceCount), Zero, Zero)
  }

  def observableInError(sourceCount: Int, ex: Throwable) = Some {
    if (sourceCount == 1) {
      val o = Observable.now(Val(1)).endWithError(ex).distinctByKey(_.x).map(_.x)
      Sample(o, 1, 1, Zero, Zero)
    } else {
      val seq = (0 until sourceCount)
        .flatMap(i => Seq(Val(i),Val(i),Val(i)))

      val o = Observable.from(seq).endWithError(ex).distinctByKey(_.x).map(_.x)
      Sample(o, count(sourceCount), sum(sourceCount), Zero, Zero)
    }
  }

  def brokenUserCodeObservable(sourceCount: Int, ex: Throwable) = Some {
    val o = Observable.range(0, sourceCount)
      .flatMap(i => Observable.from(Seq(Val(i), Val(i), Val(i))))
      .distinctByKey(i => if (i.x == sourceCount-1) throw ex else i.x)
      .map(_.x)

    Sample(o, count(sourceCount-1), sum(sourceCount-1), Zero, Zero)
  }

  def count(sourceCount: Int) = sourceCount
  def sum(sourceCount: Int) = sourceCount * (sourceCount - 1) / 2

  override def cancelableObservables() = {
    val o = Observable.now(1L).delayOnNext(1.second).distinctByKey(x => x)
    Seq(Sample(o, 0, 0, 0.seconds, 0.seconds))
  }
}