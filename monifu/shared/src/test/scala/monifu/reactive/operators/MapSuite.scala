/*
 * Copyright (c) 2014-2015 by its authors. Some rights reserved.
 * See the project homepage at: http://www.monifu.org
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
import scala.concurrent.duration.Duration.Zero

object MapSuite extends BaseOperatorSuite {
  def sum(sourceCount: Int): Long = sourceCount.toLong * (sourceCount + 1)
  def count(sourceCount: Int) = sourceCount

  def observable(sourceCount: Int) = {
    require(sourceCount > 0, "sourceCount should be strictly positive")
    Some {
      val o = if (sourceCount == 1)
        Observable.unit(1L).map(_ * 2)
      else
        Observable.range(1, sourceCount+1, 1).map(_ * 2)

      Sample(o, count(sourceCount), sum(sourceCount), Zero, Zero)
    }
  }

  def observableInError(sourceCount: Int, ex: Throwable) = {
    require(sourceCount > 0, "sourceCount should be strictly positive")
    Some {
      val ex = DummyException("dummy")
      val o = if (sourceCount == 1)
        createObservableEndingInError(Observable.unit(1L), ex)
          .map(_ * 2)
      else
        createObservableEndingInError(Observable.range(1, sourceCount+1, 1), ex)
          .map(_ * 2)

      Sample(o, count(sourceCount), sum(sourceCount), Zero, Zero)
    }
  }

  def brokenUserCodeObservable(sourceCount: Int, ex: Throwable) = {
    require(sourceCount > 0, "sourceCount should be strictly positive")
    Some {
      val o = if (sourceCount == 1)
        Observable.unit(1).map(_ => throw ex)
      else
        Observable.range(1, sourceCount + 1, 1).map { x =>
          if (x == sourceCount)
            throw ex
          else
            x * 2
        }

      Sample(o, count(sourceCount-1), sum(sourceCount-1), Zero, Zero)
    }
  }
}
