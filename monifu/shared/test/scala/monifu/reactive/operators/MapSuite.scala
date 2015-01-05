/*
 * Copyright (c) 2014 by its authors. Some rights reserved.
 * See the project homepage at
 *
 *     http://www.monifu.org/
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

import monifu.reactive.{DummyException, Observable}
import scala.concurrent.duration.Duration

object MapSuite extends BaseOperatorSuite {
  val waitForFirst = Duration.Zero
  val waitForNext = Duration.Zero
  def sum(count: Int): Long = count.toLong * (count + 1)

  def observable(count: Int) = {
    require(count > 0, "count should be strictly positive")
    Some {
      if (count == 1)
        Observable.unit(1L).map(_ * 2)
      else
        Observable.range(1, count+1, 1).map(_ * 2)
    }
  }

  def observableInError(count: Int, ex: Throwable) = {
    require(count > 0, "count should be strictly positive")
    Some {
      val ex = DummyException("dummy")
      if (count == 1)
        createObservableEndingInError(Observable.unit(1L), ex)
          .map(_ * 2)
      else
        createObservableEndingInError(Observable.range(1, count+1, 1), ex)
          .map(_ * 2)
    }
  }

  def brokenUserCodeObservable(count: Int, ex: Throwable) = {
    require(count > 0, "count should be strictly positive")
    Some {
      if (count == 1)
        Observable.unit(1).map(_ => throw ex)
      else
        Observable.range(1, count + 1, 1).map { x =>
          if (x == count)
            throw ex
          else
            x * 2
        }
    }
  }
}
