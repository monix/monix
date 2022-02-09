/*
 * Copyright (c) 2014-2021 by The Monix Project Developers.
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

import scala.concurrent.duration.Duration.Zero;

class ConsecutiveGroupBySuite extends BaseOperatorSuite {
  def createObservable(sourceCount: Int): Some[Sample] = Some {
    val o = Observable
      .range(0L, sourceCount.toLong)
      .consecutiveGroupBy(a => a / 5)
      .flatMap { case (i, o) => o.map(_ + i) }

    Sample(o, count(sourceCount), sum(sourceCount), Zero, Zero)
  }

  def count(sourceCount: Int) =
    sourceCount

  def sum(sourceCount: Int) = {
    (0 until sourceCount).map(x => x).sum + sourceCount / 5
  }

  /** Optionally build an observable that simulates an error in user
    * code (if such a thing is possible for the tested operator.
    *
    * It first emits elements, followed by an error triggered
    * within the user-provided portion of the operator.
    */
  override def brokenUserCodeObservable(sourceCount: Int, ex: Throwable): Option[Sample] = None

  /** Optionally builds an observable that first emits the
    * items and then ends in error triggered by user code
    * (only for operators that execute user specified code).
    */
  override def observableInError(sourceCount: Int, ex: Throwable): Option[Sample] = None

  /** Optionally return a sequence of observables
    * that can be canceled.
    */
  override def cancelableObservables(): Seq[Sample] = Seq()
}
