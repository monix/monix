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

object DebounceFlattenSuite extends BaseOperatorSuite {
  def createObservable(sourceCount: Int) = Some {
    val o = Observable.now(1L).delayOnComplete(1.day)
      .debounceTo(1.second, (x: Long) => Observable.interval(1.second).map(_ => x))
      .take(sourceCount)

    val count = sourceCount
    val sum = sourceCount
    Sample(o, count, sum, 1.second, 1.second)
  }

  def observableInError(sourceCount: Int, ex: Throwable) = None
  def brokenUserCodeObservable(sourceCount: Int, ex: Throwable) = None

  override def cancelableObservables(): Seq[Sample] = {
    val sample = Observable.now(1L).delayOnComplete(1.day)
      .debounceTo(1.second, (x: Long) => Observable.interval(1.second).map(_ => 1L))
      .take(10)

    Seq(Sample(sample, 2, 2, 2.seconds, 2.seconds))
  }
}
