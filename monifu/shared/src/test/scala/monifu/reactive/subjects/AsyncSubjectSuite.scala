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

package monifu.reactive.subjects

import monifu.concurrent.atomic.{Atomic, AtomicLong}
import monifu.reactive.Ack.Continue
import monifu.reactive.exceptions.DummyException
import monifu.reactive.{Observable, Observer}

object AsyncSubjectSuite extends BaseSubjectSuite {
  def alreadyTerminatedTest(expectedElems: Seq[Long]) = {
    val s = AsyncSubject[Long]()
    Sample(s, expectedElems.lastOption.getOrElse(0))
  }

  def continuousStreamingTest(expectedElems: Seq[Long]) = None

  test("while active, keep adding subscribers, but don't emit anything") { implicit s =>
    var wereCompleted = 0
    var sum = 0L

    def createObserver = new Observer[Long] {
      def onNext(elem: Long) = {
        sum += elem
        Continue
      }

      def onError(ex: Throwable) = ()
      def onComplete() = {
        wereCompleted += 1
      }
    }

    val subject = AsyncSubject[Long]()
    subject.unsafeSubscribe(createObserver)
    subject.unsafeSubscribe(createObserver)
    subject.unsafeSubscribe(createObserver)

    subject.onNext(10)
    subject.onNext(20)
    subject.onNext(30)

    s.tick()
    assertEquals(sum, 0)
    assertEquals(wereCompleted, 0)

    subject.onComplete()
    subject.onComplete()

    assertEquals(sum, 30 * 3)
    assertEquals(wereCompleted, 3)

    subject.unsafeSubscribe(createObserver)
    assertEquals(sum, 30 * 4)
    assertEquals(wereCompleted, 4)
  }

  test("should interrupt on error without emitting anything") { implicit s =>
    var wereCompleted = 0
    var sum = 0L

    def createObserver = new Observer[Long] {
      def onNext(elem: Long) = {
        sum += elem
        Continue
      }

      def onComplete() = ()
      def onError(ex: Throwable) = ex match {
        case DummyException("dummy1") =>
          wereCompleted += 1
        case _ =>
          ()
      }
    }

    val subject = AsyncSubject[Long]()
    subject.unsafeSubscribe(createObserver)
    subject.unsafeSubscribe(createObserver)
    subject.unsafeSubscribe(createObserver)

    subject.onNext(10)
    subject.onNext(20)
    subject.onNext(30)

    s.tick()
    assertEquals(sum, 0)
    assertEquals(wereCompleted, 0)

    subject.onError(DummyException("dummy1"))
    subject.onError(DummyException("dummy2"))

    assertEquals(sum, 0)
    assertEquals(wereCompleted, 3)

    subject.unsafeSubscribe(createObserver)
    assertEquals(sum, 0)
    assertEquals(wereCompleted, 4)
  }

  test("should interrupt when empty") { implicit s =>
    var wereCompleted = 0
    var sum = 0L

    def createObserver = new Observer[Long] {
      def onNext(elem: Long) = {
        sum += elem
        Continue
      }

      def onComplete() = wereCompleted += 1
      def onError(ex: Throwable) = ()
    }

    val subject = AsyncSubject[Long]()
    subject.unsafeSubscribe(createObserver)
    subject.unsafeSubscribe(createObserver)
    subject.unsafeSubscribe(createObserver)

    subject.onComplete()

    assertEquals(sum, 0)
    assertEquals(wereCompleted, 3)

    subject.unsafeSubscribe(createObserver)
    assertEquals(sum, 0)
    assertEquals(wereCompleted, 4)
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
}
