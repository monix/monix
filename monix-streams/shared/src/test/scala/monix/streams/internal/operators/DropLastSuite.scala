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

object DropLastSuite extends BaseOperatorSuite {
  def createObservable(sourceCount: Int) = {
    require(sourceCount > 0, "sourceCount should be strictly positive")
    Some {
      val o = Observable.range(0, sourceCount).dropLast(10)
      Sample(o, count(sourceCount-10), sum(sourceCount-10), Zero, Zero)
    }
  }

  def sum(sourceCount: Int): Long = {
    sourceCount * (sourceCount - 1) / 2
  }

  def count(sourceCount: Int) =
    sourceCount - 10

  def observableInError(sourceCount: Int, ex: Throwable) = {
    require(sourceCount > 0, "sourceCount should be strictly positive")
    Some {
      val o = Observable.range(0, sourceCount).endWithError(ex).dropLast(1)
      Sample(o, count(sourceCount), sum(sourceCount), Zero, Zero)
    }
  }

  def brokenUserCodeObservable(sourceCount: Int, ex: Throwable) =
    None

  override def cancelableObservables() = {
    val o = Observable.range(1, 10).delayOnNext(1.second).dropLast(5)
    Seq(Sample(o, 0, 0, Zero, Zero))
  }
}