/*
 * Copyright (c) 2014-2022 Monix Contributors.
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

object CombineLatestListSuite extends BaseOperatorSuite {

  val NumberOfObservables: Int = 10000 // We want to test with MANY observables to ensure it is stack-safe.

  def createObservable(sourceCount: Int) = Some {
    val source = Observable.range(0L, sourceCount.toLong)
    val sources = (1 to NumberOfObservables).map(_ => Observable.now(1L))
    val o: Observable[Long] =
      Observable.combineLatestList((sources :+ source): _*).map { seq =>
        seq.sum
      }

    val sum = (0 until sourceCount).map(_ + NumberOfObservables).sum
    Sample(o, sourceCount, sum.toLong, Zero, Zero)
  }

  def observableInError(sourceCount: Int, ex: Throwable) = None
  def brokenUserCodeObservable(sourceCount: Int, ex: Throwable) = None

  override def cancelableObservables(): Seq[Sample] = {
    val sample1 = {
      val sources =
        (0 until NumberOfObservables).map(_ => Observable.range(0, 10).delayOnNext(1.second))
      Observable.combineLatestList(sources: _*).map { seq =>
        seq.sum
      }
    }

    Seq(Sample(sample1, 0, 0, 0.seconds, 0.seconds))
  }
}
