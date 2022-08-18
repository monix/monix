/*
 * Copyright (c) 2014-2021 by The Monix Project Developers.
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

package monix.tail

import minitest.TestSuite
import monix.eval.Task
import monix.execution.Scheduler
import monix.execution.Scheduler.global
import monix.execution.atomic.Atomic
import monix.execution.exceptions.DummyException
import monix.execution.internal.Platform
import org.reactivestreams.{ Publisher, Subscriber, Subscription }

import scala.concurrent.{ Future, Promise }

object IterantFromReactiveStreamAsyncSuite extends TestSuite[Scheduler] {
  def setup(): Scheduler = global
  def tearDown(env: Scheduler): Unit = ()

  def testRangeConsumptionWithAsyncTasks(request: Int, eagerBuffer: Boolean, async: Boolean)(implicit sc: Scheduler) = {
    def test() = {
      val count = if (async || Platform.isJS) 1000 else 5000
      val pub = new RangePublisher(0 until count, None)
      val f = Iterant[Task]
        .fromReactivePublisher(pub, request, eagerBuffer)
        .mapEval(x => if (async) Task.evalAsync(x) else Task(x))
        .sumL
        .runToFuture

      for (r <- f) yield {
        assertEquals(r, (count * (count - 1)) / 2)
      }
    }

    def loop(n: Int): Future[Unit] =
      if (n > 0) test().flatMap(_ => loop(n - 1))
      else Future.successful(())

    loop(if (Platform.isJVM) 100 else 1)
  }

  testAsync("convert range publisher with requestCount=1,   eagerBuffer=true  and async tasks") { implicit sc =>
    testRangeConsumptionWithAsyncTasks(request = 1, eagerBuffer = true, async = true)
  }

  testAsync("convert range publisher with requestCount=1,   eagerBuffer=false and async tasks") { implicit sc =>
    testRangeConsumptionWithAsyncTasks(request = 1, eagerBuffer = false, async = true)
  }

  testAsync("convert range publisher with requestCount=16,  eagerBuffer=true  and async tasks") { implicit sc =>
    testRangeConsumptionWithAsyncTasks(request = 16, eagerBuffer = true, async = true)
  }

  testAsync("convert range publisher with requestCount=16,  eagerBuffer=false and async tasks") { implicit sc =>
    testRangeConsumptionWithAsyncTasks(request = 16, eagerBuffer = false, async = true)
  }

  testAsync("convert range publisher with requestCount=256, eagerBuffer=true  and async tasks") { implicit sc =>
    testRangeConsumptionWithAsyncTasks(request = 256, eagerBuffer = true, async = true)
  }

  testAsync("convert range publisher with requestCount=256, eagerBuffer=false and async tasks") { implicit sc =>
    testRangeConsumptionWithAsyncTasks(request = 256, eagerBuffer = false, async = true)
  }

  testAsync("convert range publisher with requestCount=1,   eagerBuffer=true  and sync  tasks") { implicit sc =>
    testRangeConsumptionWithAsyncTasks(request = 1, eagerBuffer = true, async = false)
  }

  testAsync("convert range publisher with requestCount=1,   eagerBuffer=false and sync  tasks") { implicit sc =>
    testRangeConsumptionWithAsyncTasks(request = 1, eagerBuffer = false, async = false)
  }

  testAsync("convert range publisher with requestCount=16,  eagerBuffer=true  and sync  tasks") { implicit sc =>
    testRangeConsumptionWithAsyncTasks(request = 16, eagerBuffer = true, async = false)
  }

  testAsync("convert range publisher with requestCount=16,  eagerBuffer=false and sync  tasks") { implicit sc =>
    testRangeConsumptionWithAsyncTasks(request = 16, eagerBuffer = false, async = false)
  }

  testAsync("convert range publisher with requestCount=256, eagerBuffer=true  and sync  tasks") { implicit sc =>
    testRangeConsumptionWithAsyncTasks(request = 256, eagerBuffer = true, async = false)
  }

  testAsync("convert range publisher with requestCount=256, eagerBuffer=false and sync  tasks") { implicit sc =>
    testRangeConsumptionWithAsyncTasks(request = 256, eagerBuffer = false, async = false)
  }

  def testStreamEndingInError(request: Int, eagerBuffer: Boolean, async: Boolean)(implicit sc: Scheduler) = {
    def test() = {
      val dummy = DummyException("dummy")
      val count = 1000
      val pub = new RangePublisher(0 until count, Some(dummy))
      val effect = Atomic(0)
      val f = Iterant[Task]
        .fromReactivePublisher(pub, request, eagerBuffer)
        .mapEval(x => if (async) Task.evalAsync(effect.increment(x)) else Task(effect.increment(x)))
        .completedL
        .attempt
        .runToFuture

      for (r <- f) yield {
        assertEquals(effect.get(), (count * (count - 1)) / 2)
        assertEquals(r, Left(dummy))
      }
    }

    def loop(n: Int): Future[Unit] =
      if (n > 0) test().flatMap(_ => loop(n - 1))
      else Future.successful(())

    loop(if (Platform.isJVM) {
      if (async) 100 else 1000
    } else {
      1
    })
  }

  testAsync("stream ending in error with requestCount=1,   eagerBuffer=true  and async tasks") { implicit sc =>
    testStreamEndingInError(1, eagerBuffer = true, async = true)
  }

  testAsync("stream ending in error with requestCount=1,   eagerBuffer=false and async tasks") { implicit sc =>
    testStreamEndingInError(1, eagerBuffer = false, async = true)
  }

  testAsync("stream ending in error with requestCount=16,  eagerBuffer=true  and async tasks") { implicit sc =>
    testStreamEndingInError(16, eagerBuffer = true, async = true)
  }

  testAsync("stream ending in error with requestCount=16,  eagerBuffer=false and async tasks") { implicit sc =>
    testStreamEndingInError(16, eagerBuffer = false, async = true)
  }

  testAsync("stream ending in error with requestCount=256, eagerBuffer=true  and async tasks") { implicit sc =>
    testStreamEndingInError(256, eagerBuffer = true, async = true)
  }

  testAsync("stream ending in error with requestCount=256, eagerBuffer=false and async tasks") { implicit sc =>
    testStreamEndingInError(256, eagerBuffer = false, async = true)
  }

  testAsync("stream ending in error with requestCount=1,   eagerBuffer=true  and sync  tasks") { implicit sc =>
    testStreamEndingInError(1, eagerBuffer = true, async = false)
  }

  testAsync("stream ending in error with requestCount=1,   eagerBuffer=false and sync  tasks") { implicit sc =>
    testStreamEndingInError(1, eagerBuffer = false, async = false)
  }

  testAsync("stream ending in error with requestCount=16,  eagerBuffer=true  and sync  tasks") { implicit sc =>
    testStreamEndingInError(16, eagerBuffer = true, async = false)
  }

  testAsync("stream ending in error with requestCount=16,  eagerBuffer=false and sync  tasks") { implicit sc =>
    testStreamEndingInError(16, eagerBuffer = false, async = false)
  }

  testAsync("stream ending in error with requestCount=256, eagerBuffer=true  and sync  tasks") { implicit sc =>
    testStreamEndingInError(256, eagerBuffer = true, async = false)
  }

  testAsync("stream ending in error with requestCount=256, eagerBuffer=false and sync  tasks") { implicit sc =>
    testStreamEndingInError(256, eagerBuffer = false, async = false)
  }

  class RangePublisher(from: Int, until: Int, step: Int, finish: Option[Throwable], onCancel: Promise[Unit])(
    implicit sc: Scheduler
  ) extends Publisher[Int] {

    def this(range: Range, finish: Option[Throwable])(implicit sc: Scheduler) =
      this(range.start, range.end, range.step, finish, null)

    def this(range: Range, finish: Option[Throwable], onCancel: Promise[Unit])(implicit sc: Scheduler) =
      this(range.start, range.end, range.step, finish, onCancel)

    def subscribe(s: Subscriber[_ >: Int]): Unit = {
      s.onSubscribe(new Subscription { self =>
        private[this] val finished = Atomic(false)
        private[this] val cancelled = Atomic(false)
        private[this] val requested = Atomic(0L)
        private[this] var index = from

        def isInRange(x: Long, until: Long, step: Long): Boolean = {
          (step > 0 && x < until) || (step < 0 && x > until)
        }

        def request(n: Long): Unit = {
          if (requested.getAndAdd(n) == 0)
            sc.execute(new Runnable {
              def run(): Unit = {
                var requested = self.requested.get()
                var toSend = requested
                var isCanceled = self.cancelled.get() && self.finished.get()

                while (toSend > 0 && isInRange(index.toLong, until.toLong, step.toLong) && !isCanceled) {
                  s.onNext(index)
                  index += step
                  toSend -= 1

                  if (toSend == 0) {
                    requested = self.requested.subtractAndGet(requested)
                    toSend = requested
                  } else if (toSend % 100 == 0) {
                    isCanceled = self.cancelled.get()
                  }
                }

                if (
                  !isInRange(index.toLong, until.toLong, step.toLong) &&
                  !isCanceled &&
                  finished.compareAndSet(expect = false, update = true)
                ) {
                  finish match {
                    case None =>
                      s.onComplete()
                    case Some(e) =>
                      s.onError(e)
                  }
                }
              }
            })
        }

        def cancel(): Unit = {
          cancelled.set(true)
          if (onCancel != null) {
            onCancel.success(())
            ()
          }
        }
      })
    }
  }
}
