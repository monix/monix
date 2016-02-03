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

package monix.streams.broadcast

import minitest.TestSuite
import monix.execution.Scheduler
import monix.execution.schedulers.TestScheduler
import monix.streams.Ack.Continue
import monix.streams.exceptions.DummyException
import monix.streams.{Observable, Observer}

import scala.concurrent.Promise
import scala.util.Random


trait BaseChannelSuite extends TestSuite[TestScheduler] {
  case class Sample(channel: Subject[Long,Long] with Observable[Long], expectedSum: Long)

  def setup() = TestScheduler()
  def tearDown(s: TestScheduler) = {
    assert(s.state.get.tasks.isEmpty,
      "TestScheduler should have no pending tasks")
  }

  /**
   * Returns a sample channel that needs testing.
   */
  def alreadyTerminatedTest(expectedElems: Seq[Long])(implicit s: Scheduler): Sample

  /**
   * Returns a sample channel for the test of
   * continuous streaming.
   */
  def continuousStreamingTest(expectedElems: Seq[Long])(implicit s: Scheduler): Option[Sample]

  test("already completed and empty channel terminates observers") { implicit s =>
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

    val Sample(channel, expectedSum) = alreadyTerminatedTest(Seq.empty)
    channel.onComplete()

    channel.unsafeSubscribeFn(createObserver)
    channel.unsafeSubscribeFn(createObserver)
    channel.unsafeSubscribeFn(createObserver)

    s.tick()

    assertEquals(sum, expectedSum * 3)
    assertEquals(wereCompleted, 3)
  }

  test("failed empty channel terminates observers with an error") { implicit s =>
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

    val Sample(channel, _) = alreadyTerminatedTest(Seq.empty)
    channel.onError(DummyException("dummy"))
    s.tick()

    channel.unsafeSubscribeFn(createObserver)
    channel.unsafeSubscribeFn(createObserver)
    channel.unsafeSubscribeFn(createObserver)

    s.tick()

    assertEquals(sum, 0)
    assertEquals(wereCompleted, 3)
  }

  test("already completed but non-empty channel terminates new observers") { implicit s =>
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

    val Sample(channel, expectedSum) = alreadyTerminatedTest(elems)
    for (e <- elems) channel.onNext(e); channel.onComplete()
    s.tick()

    channel.unsafeSubscribeFn(createObserver)
    channel.unsafeSubscribeFn(createObserver)
    channel.unsafeSubscribeFn(createObserver)

    s.tick()

    assertEquals(sum, expectedSum * 3)
    assertEquals(wereCompleted, 3)
  }

  test("already failed but non-empty channel terminates new observers") { implicit s =>
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

    val Sample(channel, _) = alreadyTerminatedTest(elems)
    for (e <- elems) channel.onNext(e)
    channel.onError(DummyException("dummy"))

    s.tick()

    channel.unsafeSubscribeFn(createObserver)
    channel.unsafeSubscribeFn(createObserver)
    channel.unsafeSubscribeFn(createObserver)

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

    val Sample(channel, expectedSum) = alreadyTerminatedTest(Seq(elem))
    if (expectedSum == 0) ignore() else {
      channel.onNext(elem)
      channel.onComplete()
      s.tick()

      val promises = for (_ <- 0 until 3) yield {
        val (p, o) = createObserver
        channel.unsafeSubscribeFn(o)
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
    val elems = (0 until Random.nextInt(300) + 100).map(_.toLong).toSeq
    var wereCompleted = 0
    var totalOnNext = 0L

    def createObserver =
      new Observer[Long] {
        def onNext(elem: Long) = {
          if (elem > 10)
            throw DummyException("dummy")
          else {
            totalOnNext += elem
            Continue
          }
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
      case Some(Sample(channel, expectedSum)) =>
        var totalEmitted = 0L
        channel.doWork(totalEmitted += _).subscribe()

        channel.subscribe(createObserver)
        channel.subscribe(createObserver)
        channel.subscribe(createObserver)
        s.tick()

        for (e <- elems) channel.onNext(e); channel.onComplete()
        s.tick()

        assertEquals(wereCompleted, 3)
        assertEquals(totalEmitted, expectedSum)
        assertEquals(totalOnNext, 11 * 5 * 3)
    }
  }
}
