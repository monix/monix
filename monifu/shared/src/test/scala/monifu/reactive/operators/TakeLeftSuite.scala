/*
 * Copyright (c) 2015 Alexandru Nedelcu
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package monifu.reactive.operators

import monifu.reactive.{DummyException, Observable}
import scala.concurrent.duration.Duration

object TakeLeftSuite extends BaseOperatorSuite {
  val waitForFirst = Duration.Zero
  val waitForNext = Duration.Zero

  def sum(count: Int): Long = count.toLong * (count + 1) / 2

  def observable(count: Int) = {
    require(count > 0, "count should be strictly positive")
    Some {
      if (count == 1)
        Observable.range(1, 10).take(1)
      else
        Observable.range(1, count * 2).take(count)
    }
  }

  def observableInError(count: Int, ex: Throwable) = {
    require(count > 0, "count should be strictly positive")
    Some {
      val ex = DummyException("dummy")
      if (count == 1)
        createObservableEndingInError(Observable.range(1, 10).take(1), ex)
      else
        createObservableEndingInError(Observable.range(1, count * 2).take(count), ex)
    }
  }

  def brokenUserCodeObservable(count: Int, ex: Throwable) =
    None
}
