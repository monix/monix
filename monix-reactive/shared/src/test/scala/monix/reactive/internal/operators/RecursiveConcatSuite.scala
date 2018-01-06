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
import scala.concurrent.duration.Duration.Zero
import scala.util.Success

object RecursiveConcatSuite extends BaseOperatorSuite {
  def range(from: Long, until: Long): Observable[Long] =
    Observable.defer {
      Observable.now(from) ++ (
        if (from + 1 < until) range(from + 1, until)
        else Observable.empty
      )
    }

  def count(sourceCount: Int) = sourceCount
  def sum(sourceCount: Int): Long = sourceCount.toLong * (sourceCount - 1) / 2

  def createObservable(sourceCount: Int) = {
    require(sourceCount > 0, "sourceCount should be strictly positive")
    if (sourceCount <= 1) None else Some {
      val o = range(0, sourceCount)
      Sample(o, count(sourceCount), sum(sourceCount), Zero, Zero)
    }
  }

  def brokenUserCodeObservable(sourceCount: Int, ex: Throwable) = None
  def observableInError(sourceCount: Int, ex: Throwable) = None
  def cancelableObservables() = Seq.empty

  test("stack safety") { implicit s =>
    val count = 10000
    val f = range(0, count).sumL.runAsync; s.tick()
    assertEquals(f.value, Some(Success(count.toLong * (count - 1) / 2)))
  }
}
