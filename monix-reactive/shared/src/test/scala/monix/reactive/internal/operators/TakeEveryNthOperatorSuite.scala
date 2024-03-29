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
import scala.concurrent.duration.Duration

object TakeEveryNthOperatorSuite extends BaseOperatorSuite {
  override def createObservable(sourceCount: Int) = Some {
    val sCount = if (sourceCount > 4) sourceCount else 4
    val count = sCount / 4
    val sum = 4L * count * (count + 1) / 2
    val obs = Observable.range(1, sCount.toLong + 1).takeEveryNth(4)
    Sample(obs, count, sum, Duration.Zero, Duration.Zero)
  }

  override def brokenUserCodeObservable(sourceCount: Int, ex: Throwable) = None
  override def observableInError(sourceCount: Int, ex: Throwable) = None
  override def cancelableObservables() = Nil
}
