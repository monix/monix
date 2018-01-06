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

import monix.reactive.Observable
import scala.concurrent.duration._
import scala.concurrent.duration.Duration.Zero

object DropByPredicateWithIndexSuite extends BaseOperatorSuite {
  def createObservable(sourceCount: Int) = {
    require(sourceCount > 0, "sourceCount should be strictly positive")
    Some {
      val o = Observable.range(1, sourceCount * 2)
        .dropWhileWithIndex((e, idx) => e < sourceCount)

      Sample(o, count(sourceCount), sum(sourceCount), Zero, Zero)
    }
  }

  def sum(sourceCount: Int): Long =
    (1 until sourceCount * 2).drop(sourceCount-1).sum

  def count(sourceCount: Int) =
    sourceCount

  def observableInError(sourceCount: Int, ex: Throwable) = {
    require(sourceCount > 0, "sourceCount should be strictly positive")
    Some {
      val o = createObservableEndingInError(Observable.range(1, sourceCount + 2), ex)
        .dropWhileWithIndex((e, idx) => idx == 0)

      Sample(o, count(sourceCount), sum(sourceCount), Zero, Zero)
    }
  }

  def brokenUserCodeObservable(sourceCount: Int, ex: Throwable) = Some {
    val o = Observable.range(1, sourceCount * 2)
      .dropWhileWithIndex { (e, idx) =>
        if (e < sourceCount) true else throw ex
      }

    Sample(o, 0, 0, Zero, Zero)
  }

  override def cancelableObservables() = {
    val o = Observable.range(1, 1000).delayOnNext(1.second)
      .dropWhileWithIndex((e,i) => e < 100)

    Seq(Sample(o, 0, 0, 0.seconds, 0.seconds))
  }
}