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

import monifu.reactive.Observable
import scala.concurrent.duration._

object BufferSizedAndTimedNr2Suite extends BaseOperatorSuite {
  val waitForNext = 1.second
  val waitForFirst = 1.second

  def sum(sourceCount: Int) = {
    val total = (sourceCount * 10 - 1).toLong
    total * (total + 1) / 2
  }

  def count(sourceCount: Int) = {
    sourceCount
  }

  def observable(sourceCount: Int) = {
    require(sourceCount > 0, "count must be strictly positive")
    Some {
      Observable.intervalAtFixedRate(100.millis)
        .take(sourceCount * 10)
        .bufferSizedAndTimed(10, 2.seconds)
        .map(_.sum)
    }
  }

  def observableInError(sourceCount: Int, ex: Throwable) = {
    require(sourceCount > 0, "count must be strictly positive")
    Some {
      Observable.intervalAtFixedRate(100.millis)
        .map(x => if (x == sourceCount * 10 - 1) throw ex else x)
        .take(sourceCount * 10)
        .bufferSizedAndTimed(10, 2.seconds)
        .map(_.sum)
    }
  }

  def brokenUserCodeObservable(sourceCount: Int, ex: Throwable) =
    None
}
