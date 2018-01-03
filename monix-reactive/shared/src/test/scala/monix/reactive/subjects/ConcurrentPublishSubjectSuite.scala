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

import monix.execution.Scheduler
import monix.reactive.{MulticastStrategy, OverflowStrategy}
import OverflowStrategy.Unbounded

object ConcurrentPublishSubjectSuite extends BaseConcurrentSubjectSuite {
  def alreadyTerminatedTest(expectedElems: Seq[Long])(implicit s: Scheduler) = {
    val c = ConcurrentSubject[Long](MulticastStrategy.publish)
    Sample(c, 0)
  }

  def continuousStreamingTest(expectedElems: Seq[Long])(implicit s: Scheduler) = {
    val c = ConcurrentSubject.publish[Long](Unbounded)
    Some(Sample(c, expectedElems.sum))
  }
}
