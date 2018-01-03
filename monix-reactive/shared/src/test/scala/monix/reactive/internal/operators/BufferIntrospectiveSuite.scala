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

import minitest.TestSuite
import monix.execution.Ack
import monix.execution.Ack.Continue
import monix.execution.schedulers.TestScheduler
import monix.reactive.observers.Subscriber
import monix.reactive.subjects.PublishSubject
import monix.execution.atomic.Atomic
import scala.concurrent.{Future, Promise}

object BufferIntrospectiveSuite extends TestSuite[TestScheduler] {
  def setup() = TestScheduler()
  def tearDown(s: TestScheduler) = {
    assert(s.state.tasks.isEmpty,
      "TestScheduler should be left with no pending tasks")
  }

  test("it buffers while consumer is busy") { implicit s =>
    val subject = PublishSubject[Long]()
    val nextAck = Atomic(Promise[Ack]())
    var wasCompleted = 0
    var sum = 0L

    subject.bufferIntrospective(maxSize = 10)
      .unsafeSubscribeFn(new Subscriber[List[Long]] {
        implicit val scheduler = s

        def onNext(elem: List[Long]): Future[Ack] = {
          sum += elem.sum
          nextAck.get.future
        }

        def onError(ex: Throwable): Unit =
          throw new IllegalStateException("onError")
        def onComplete(): Unit =
          wasCompleted += 1
      })

    assertEquals(subject.onNext(1), Continue)
    s.tick()

    assertEquals(subject.onNext(2), Continue)
    assertEquals(subject.onNext(3), Continue)

    s.tick()
    assertEquals(sum, 1)

    nextAck.getAndSet(Promise()).success(Continue)
    s.tick()
    assertEquals(sum, 6)

    for (i <- 0 until 10) subject.onNext(1)
    s.tick()
    assertEquals(sum, 6)

    nextAck.getAndSet(Promise()).success(Continue)
    s.tick()
    assertEquals(sum, 16)

    subject.onComplete()
    nextAck.getAndSet(Promise()).success(Continue)
    s.tick()

    assertEquals(sum, 16)
    assertEquals(wasCompleted, 1)
  }
}
