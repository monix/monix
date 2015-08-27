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

package monifu.reactive.channels

import monifu.concurrent.Scheduler
import monifu.concurrent.atomic.{Atomic, AtomicLong}
import monifu.reactive.Ack.Continue
import monifu.reactive.OverflowStrategy.Unbounded
import monifu.reactive.Observer
import monifu.reactive.exceptions.DummyException

object AsyncChannelSuite extends BaseChannelSuite {
  def alreadyTerminatedTest(expectedElems: Seq[Long])(implicit s: Scheduler) = {
    val c = AsyncChannel[Long](Unbounded)
    Sample(c, expectedElems.lastOption.getOrElse(0))
  }

  def continuousStreamingTest(expectedElems: Seq[Long])(implicit s: Scheduler) = None

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

    val channel = AsyncChannel[Long](Unbounded)
    channel.onSubscribe(createObserver)
    channel.onSubscribe(createObserver)
    channel.onSubscribe(createObserver)

    channel.pushNext(10, 20, 30)

    s.tick()
    assertEquals(sum, 0)
    assertEquals(wereCompleted, 0)

    channel.pushComplete()
    channel.pushComplete()
    s.tick()

    assertEquals(sum, 30 * 3)
    assertEquals(wereCompleted, 3)

    channel.onSubscribe(createObserver)
    s.tick()

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

    val channel = AsyncChannel[Long](Unbounded)
    channel.onSubscribe(createObserver)
    channel.onSubscribe(createObserver)
    channel.onSubscribe(createObserver)

    channel.pushNext(10)
    channel.pushNext(20)
    channel.pushNext(30)

    s.tick()
    assertEquals(sum, 0)
    assertEquals(wereCompleted, 0)

    channel.pushError(DummyException("dummy1"))
    channel.pushError(DummyException("dummy2"))

    s.tick()
    assertEquals(sum, 0)
    assertEquals(wereCompleted, 3)

    channel.onSubscribe(createObserver)
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

    val channel = AsyncChannel[Long](Unbounded)
    channel.onSubscribe(createObserver)
    channel.onSubscribe(createObserver)
    channel.onSubscribe(createObserver)

    channel.pushComplete()

    s.tick()
    assertEquals(sum, 0)
    assertEquals(wereCompleted, 3)

    channel.onSubscribe(createObserver)

    s.tick()
    assertEquals(sum, 0)
    assertEquals(wereCompleted, 4)
  }

  test("should not subscribe the same observer instance twice") { implicit s =>
    val Sample(channel, _) = alreadyTerminatedTest(Seq.empty)
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

    channel.onSubscribe(observer1)
    channel.onSubscribe(observer2)
    channel.onSubscribe(observer2)

    for (i <- 0 until 1000) channel.pushNext(i)
    channel.pushComplete()

    s.tick()
    assertEquals(wereCompleted, 2)
    assertEquals(sum1.get, sum2.get)
  }
}
