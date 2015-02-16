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

import monifu.concurrent.atomic.Atomic
import monifu.concurrent.cancelables.BooleanCancelable
import monifu.reactive.Observable
import scala.concurrent.duration.Duration

object TakeWhileNotCanceledSuite extends BaseOperatorSuite {
  val waitForFirst = Duration.Zero
  val waitForNext = Duration.Zero

  def sum(count: Int): Long =
    count.toLong * (count + 1) / 2

  def observable(count: Int) = {
    require(count > 0, "count should be strictly positive")
    Some {
      val c = BooleanCancelable()
      if (count == 1)
        Observable.range(1, 10).takeWhileNotCanceled(c)
          .map { x => c.cancel(); x }
      else
        Observable.range(1, count * 2).takeWhileNotCanceled(c)
          .map { x => if (x == count) c.cancel(); x }
    }
  }

  def brokenUserCodeObservable(count: Int, ex: Throwable) = {
    require(count > 0, "count should be strictly positive")
    Some {
      val c = new BooleanCancelable {
        def cancel() = false
        val counter = Atomic(0)

        def isCanceled =
          if (counter.incrementAndGet() < count)
            false
          else
            throw ex
      }

      Observable.range(1, count * 2)
        .takeWhileNotCanceled(c)
    }
  }

  def observableInError(count: Int, ex: Throwable) = {
    require(count > 0, "count should be strictly positive")
    Some {
      val c = BooleanCancelable()
      if (count == 1)
        createObservableEndingInError(Observable.unit(1), ex)
          .takeWhileNotCanceled(c)
      else
        createObservableEndingInError(Observable.range(1, count + 1), ex)
          .takeWhileNotCanceled(c)
    }
  }
}
