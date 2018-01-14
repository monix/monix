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

package monix.reactive.subjects

import minitest.laws.Checkers
import monix.execution.Scheduler
import monix.reactive.MulticastStrategy
import monix.reactive.OverflowStrategy.Unbounded

import scala.util.Success

object ConcurrentReplayLimitedSubjectSuite extends BaseConcurrentSubjectSuite with Checkers {
  def alreadyTerminatedTest(expectedElems: Seq[Long])(implicit s: Scheduler) = {
    val c = ConcurrentSubject[Long](MulticastStrategy.replayLimited(expectedElems.size + 1), Unbounded)
    Sample(c, expectedElems.sum)
  }

  def continuousStreamingTest(expectedElems: Seq[Long])(implicit s: Scheduler) = {
    val c = ConcurrentSubject.replayLimited[Long](expectedElems.size + 1)
    Some(Sample(c, expectedElems.sum))
  }

  test("should replay only last n elements") { implicit s =>
    check1 { (list: Seq[Long]) =>
      val capacity = 10
      val c = ConcurrentSubject.replayLimited[Long](capacity, list, Unbounded)

      val sum = c.sumL.runAsync
      c.onComplete()
      s.tick()

      sum.value == Some(Success(list.takeRight(capacity).sum))
    }
  }
}