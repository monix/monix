/*
 * Copyright (c) 2014-2015 Alexandru Nedelcu
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

package monifu.reactive.subjects

import monifu.concurrent.atomic.{Atomic, AtomicLong}
import monifu.reactive.Ack.Continue
import monifu.reactive.{Observable, Observer}

import scala.util.Success

object PublishSubjectSuite extends BaseSubjectSuite {
  def alreadyTerminatedTest(expectedElems: Seq[Long]) = {
    val s = PublishSubject[Long]()
    Sample(s, 0)
  }

  def continuousStreamingTest(expectedElems: Seq[Long]) = {
    val s = PublishSubject[Long]()
    Some(Sample(s, expectedElems.sum))
  }

  test("should not subscribe the same observer instance twice") { implicit s =>
    val Sample(subject, _) = alreadyTerminatedTest(Seq.empty)
    var wereCompleted = 0

    def createObserver(sum: AtomicLong) = new Observer[Long] {
      def onNext(elem: Long) = {
        sum.add(elem)
        Continue
      }

      def onError(ex: Throwable) = ()
      def onComplete() = wereCompleted += 1
    }

    val sum1 = Atomic(0L)
    val observer1 = createObserver(sum1)
    val sum2 = Atomic(0L)
    val observer2 = createObserver(sum2)

    subject.unsafeSubscribe(observer1)
    subject.unsafeSubscribe(observer2)
    subject.unsafeSubscribe(observer2)

    Observable.range(0, 1000).unsafeSubscribe(subject)
    s.tick()

    assertEquals(wereCompleted, 2)
    assertEquals(sum1.get, sum2.get)
  }

  test("issue #50") { implicit s =>
    val p = PublishSubject[Int]()
    var received = 0

    Observable.merge(p).subscribe(new Observer[Int] {
      def onNext(elem: Int) = {
        received += elem
        Continue
      }

      def onError(ex: Throwable) = ()
      def onComplete() = ()
    })

    s.tick() // merge operation happens async

    val f = p.onNext(1)
    assertEquals(f.value, Some(Success(Continue)))
    assertEquals(received, 0)

    s.tick()
    assertEquals(received, 1)
  }
}