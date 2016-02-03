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

import monix.execution.Scheduler
import monix.streams.Ack.Continue
import monix.streams.Observer
import monix.streams.OverflowStrategy.Unbounded
import monix.streams.exceptions.DummyException

object AsyncChannelSuite extends BaseChannelSuite {
  def alreadyTerminatedTest(expectedElems: Seq[Long])(implicit s: Scheduler) = {
    val c = AsyncSubject[Long](Unbounded)
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

    val channel = AsyncSubject[Long](Unbounded)
    channel.unsafeSubscribeFn(createObserver)
    channel.unsafeSubscribeFn(createObserver)
    channel.unsafeSubscribeFn(createObserver)

    for (x <- Seq(10, 20, 30)) channel.onNext(x)

    s.tick()
    assertEquals(sum, 0)
    assertEquals(wereCompleted, 0)

    channel.onComplete()
    channel.onComplete()
    s.tick()

    assertEquals(sum, 30 * 3)
    assertEquals(wereCompleted, 3)

    channel.unsafeSubscribeFn(createObserver)
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

    val channel = AsyncSubject[Long](Unbounded)
    channel.unsafeSubscribeFn(createObserver)
    channel.unsafeSubscribeFn(createObserver)
    channel.unsafeSubscribeFn(createObserver)

    channel.onNext(10)
    channel.onNext(20)
    channel.onNext(30)

    s.tick()
    assertEquals(sum, 0)
    assertEquals(wereCompleted, 0)

    channel.onError(DummyException("dummy1"))
    channel.onError(DummyException("dummy2"))

    s.tick()
    assertEquals(sum, 0)
    assertEquals(wereCompleted, 3)

    channel.unsafeSubscribeFn(createObserver)
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

    val channel = AsyncSubject[Long](Unbounded)
    channel.unsafeSubscribeFn(createObserver)
    channel.unsafeSubscribeFn(createObserver)
    channel.unsafeSubscribeFn(createObserver)

    channel.onComplete()

    s.tick()
    assertEquals(sum, 0)
    assertEquals(wereCompleted, 3)

    channel.unsafeSubscribeFn(createObserver)

    s.tick()
    assertEquals(sum, 0)
    assertEquals(wereCompleted, 4)
  }
}
