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

object MapSuite extends BaseOperatorSuite {
  val waitForFirst = Duration.Zero
  val waitForNext = Duration.Zero
  
  def sum(sourceCount: Int): Long = sourceCount.toLong * (sourceCount + 1)
  def count(sourceCount: Int) = sourceCount

  def observable(sourceCount: Int) = {
    require(sourceCount > 0, "sourceCount should be strictly positive")
    Some {
      if (sourceCount == 1)
        Observable.unit(1L).map(_ * 2)
      else
        Observable.range(1, sourceCount+1, 1).map(_ * 2)
    }
  }

  def observableInError(sourceCount: Int, ex: Throwable) = {
    require(sourceCount > 0, "sourceCount should be strictly positive")
    Some {
      val ex = DummyException("dummy")
      if (sourceCount == 1)
        createObservableEndingInError(Observable.unit(1L), ex)
          .map(_ * 2)
      else
        createObservableEndingInError(Observable.range(1, sourceCount+1, 1), ex)
          .map(_ * 2)
    }
  }

  def brokenUserCodeObservable(sourceCount: Int, ex: Throwable) = {
    require(sourceCount > 0, "sourceCount should be strictly positive")
    Some {
      if (sourceCount == 1)
        Observable.unit(1).map(_ => throw ex)
      else
        Observable.range(1, sourceCount + 1, 1).map { x =>
          if (x == sourceCount)
            throw ex
          else
            x * 2
        }
    }
  }
}
