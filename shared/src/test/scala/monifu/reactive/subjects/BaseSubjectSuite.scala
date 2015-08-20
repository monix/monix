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

import minitest.TestSuite
import monifu.concurrent.atomic.{Atomic, AtomicLong}
import monifu.concurrent.schedulers.TestScheduler
import monifu.reactive.Ack.{Cancel, Continue}
import monifu.reactive.{Observable, DummyException, Observer, Subject}

import scala.concurrent.Promise
import scala.util.Random

trait BaseSubjectSuite extends TestSuite[TestScheduler] {
  case class Sample(subject: Subject[Long, Long], expectedSum: Long)

  def setup() = TestScheduler()
  def tearDown(s: TestScheduler) = {
    assert(s.state.get.tasks.isEmpty,
      "TestScheduler should have no pending tasks")
  }

  /**
   * Returns a sample subject that needs testing.
   */
  def alreadyTerminatedTest(expectedElems: Seq[Long]): Sample

  /**
   * Returns a sample subject for the test of
   * continuous streaming.
   */
  def continuousStreamingTest(expectedElems: Seq[Long]): Option[Sample]

  test("already completed and empty subject terminates observers") { implicit s =>
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

    val Sample(subject, expectedSum) = alreadyTerminatedTest(Seq.empty)
    subject.onComplete()

    subject.unsafeSubscribe(createObserver)
    subject.unsafeSubscribe(createObserver)
    subject.unsafeSubscribe(createObserver)

    s.tick()

    assertEquals(sum, expectedSum * 3)
    assertEquals(wereCompleted, 3)
  }

  test("failed empty subject terminates observers with an error") { implicit s =>
    var wereCompleted = 0
    var sum = 0L

    def createObserver = new Observer[Long] {
      def onNext(elem: Long) = {
        sum += elem
        Continue
      }

      def onComplete() = ()
      def onError(ex: Throwable) = ex match {
        case DummyException("dummy") =>
          wereCompleted += 1
        case _ =>
          ()
      }
    }

    val Sample(subject, _) = alreadyTerminatedTest(Seq.empty)
    subject.onError(DummyException("dummy"))

    subject.unsafeSubscribe(createObserver)
    subject.unsafeSubscribe(createObserver)
    subject.unsafeSubscribe(createObserver)

    s.tick()

    assertEquals(sum, 0)
    assertEquals(wereCompleted, 3)
  }

  test("already completed but non-empty subject terminates new observers") { implicit s =>
    val elems = (0 until 20).map(_ => Random.nextLong())
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

    val Sample(subject, expectedSum) = alreadyTerminatedTest(elems)
    Observable.fromIterable(elems).unsafeSubscribe(subject)
    s.tick()

    subject.unsafeSubscribe(createObserver)
    subject.unsafeSubscribe(createObserver)
    subject.unsafeSubscribe(createObserver)

    s.tick()

    assertEquals(sum, expectedSum * 3)
    assertEquals(wereCompleted, 3)
  }

  test("already failed but non-empty subject terminates new observers") { implicit s =>
    val elems = (0 until 20).map(_ => Random.nextLong())
    var wereCompleted = 0

    def createObserver = new Observer[Long] {
      def onNext(elem: Long) = Continue
      def onComplete() = ()
      def onError(ex: Throwable) = ex match {
        case DummyException("dummy") =>
          wereCompleted += 1
        case _ =>
          ()
      }
    }

    val Sample(subject, _) = alreadyTerminatedTest(elems)
    Observable.fromIterable(elems)
      .endWithError(DummyException("dummy"))
      .unsafeSubscribe(subject)

    s.tick()

    subject.unsafeSubscribe(createObserver)
    subject.unsafeSubscribe(createObserver)
    subject.unsafeSubscribe(createObserver)

    s.tick()
    assertEquals(wereCompleted, 3)
  }

  test("already completed observer should do back-pressure") { implicit s =>
    val elem = 100L
    var wereCompleted = 0
    var sum = 0L

    def createObserver = {
      val p = Promise[Continue]()
      val observer = new Observer[Long] {
        def onNext(elem: Long) =
          p.future.flatMap { c =>
            sum += elem
            Continue
          }

        def onError(ex: Throwable) = ()
        def onComplete() = wereCompleted += 1
      }

      (p, observer)
    }

    val Sample(subject, expectedSum) = alreadyTerminatedTest(Seq(elem))
    if (expectedSum == 0) ignore() else {
      Observable.unit(elem).unsafeSubscribe(subject)
      s.tick()

      val promises = for (_ <- 0 until 3) yield {
        val (p, o) = createObserver
        subject.unsafeSubscribe(o)
        p
      }

      s.tick()
      assertEquals(wereCompleted, 0)

      for ((p, idx) <- promises.zipWithIndex) {
        p.success(Continue)
        s.tick()

        assertEquals(expectedSum * (idx + 1), sum)
        assertEquals(wereCompleted, idx + 1)
      }
    }
  }

  test("should remove subscribers that triggered errors") { implicit s =>
    val elems = (0 until Random.nextInt(300) + 10).map(_.toLong).toSeq
    var wereCompleted = 0
    var totalReceived = 0

    def createObserver =
      new Observer[Long] {
        var received = 0
        def onNext(elem: Long) = {
          totalReceived += 1
          received += 1
          if (received > 10)
            throw DummyException("dummy")
          else
            Continue
        }

        def onComplete() = ()
        def onError(ex: Throwable) = ex match {
          case DummyException("dummy") =>
            wereCompleted += 1
          case _ =>
            ()
        }
      }

    continuousStreamingTest(elems) match {
      case None => ignore()
      case Some(Sample(subject, expectedSum)) =>
        var totalEmitted = 0L
        subject.doWork(totalEmitted += _).subscribe()

        subject.subscribe(createObserver)
        subject.subscribe(createObserver)
        subject.subscribe(createObserver)
        s.tick()

        Observable.fromIterable(elems).unsafeSubscribe(subject)
        s.tick()

        assertEquals(wereCompleted, 3)
        assertEquals(totalEmitted, expectedSum)
        assertEquals(totalReceived, 33)
    }
  }

  test("should protect onNext after onCompleted") { implicit s =>
    val Sample(subject, _) = alreadyTerminatedTest(Seq.empty)
    subject.onComplete()

    assertEquals(subject.onNext(1), Cancel)
    assertEquals(subject.onNext(2), Cancel)
    assertEquals(subject.onNext(2), Cancel)
  }

  test("should protect onNext after onError") { implicit s =>
    val Sample(subject, _) = alreadyTerminatedTest(Seq.empty)
    subject.onError(DummyException("dummy"))

    assertEquals(subject.onNext(1), Cancel)
    assertEquals(subject.onNext(2), Cancel)
    assertEquals(subject.onNext(2), Cancel)
  }
}
