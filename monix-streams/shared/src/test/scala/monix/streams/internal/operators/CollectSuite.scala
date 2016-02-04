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

import monix.streams.exceptions.DummyException
import monix.streams.Observable
import scala.concurrent.duration.Duration

object CollectSuite extends BaseOperatorSuite {
  val waitFirst = Duration.Zero
  val waitNext = Duration.Zero

  def count(sourceCount: Int) = {
    sourceCount
  }

  def sum(sourceCount: Int): Long =
    sourceCount.toLong * (sourceCount + 1)

  def createObservable(sourceCount: Int) = {
    require(sourceCount > 0, "sourceCount should be strictly positive")
    Some {
      val o = if (sourceCount == 1)
        Observable.now(2L)
          .collect { case x if x % 2 == 0 => x }
      else
        Observable.range(1, sourceCount * 2 + 1, 1)
          .collect { case x if x % 2 == 0 => x }

      Sample(o, count(sourceCount), sum(sourceCount), waitFirst, waitNext)
    }
  }

  def observableInError(sourceCount: Int, ex: Throwable) = {
    require(sourceCount > 0, "sourceCount should be strictly positive")
    Some {
      val ex = DummyException("dummy")
      val o = if (sourceCount == 1)
        createObservableEndingInError(Observable.now(2L), ex)
          .collect { case x if x % 2 == 0 => x }
      else
        createObservableEndingInError(Observable.range(1, sourceCount * 2 + 1, 1), ex)
          .collect { case x if x % 2 == 0 => x }

      Sample(o, count(sourceCount), sum(sourceCount), waitFirst, waitNext)
    }
  }

  def brokenUserCodeObservable(sourceCount: Int, ex: Throwable) = {
    require(sourceCount > 0, "sourceCount should be strictly positive")
    Some {
      val o = if (sourceCount == 1)
        Observable.now(1L).collect { case x => throw ex }
      else
        Observable.range(1, sourceCount * 2 + 1, 1).collect {
          case x if x % 2 == 0 =>
            if (x == sourceCount * 2)
              throw ex
            else
              x
        }

      Sample(o, count(sourceCount-1), sum(sourceCount-1), waitFirst, waitNext)
    }
  }
}
