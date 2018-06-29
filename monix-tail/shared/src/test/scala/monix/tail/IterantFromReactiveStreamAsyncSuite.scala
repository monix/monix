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

package monix.tail

import minitest.TestSuite
import monix.eval.Task
import monix.execution.Scheduler
import monix.execution.Scheduler.global
import monix.execution.atomic.Atomic
import org.reactivestreams.{Publisher, Subscriber, Subscription}

import scala.concurrent.Promise

object IterantFromReactiveStreamAsyncSuite extends TestSuite[Scheduler] {
  def setup(): Scheduler = global
  def tearDown(env: Scheduler): Unit = ()

  testAsync("convert range(0, 10000) publisher with requestCount = 1") { implicit sc =>
    val pub = new RangePublisher(0 until 10000, None)
    val f = Iterant[Task].fromReactivePublisher(pub, 1).sumL.runAsync

    for (r <- f) yield {
      assertEquals(r, (10000L * 9999) / 2)
    }
  }

  testAsync("convert range(0, 10000) publisher with requestCount = 1 and async tasks") { implicit sc =>
    val pub = new RangePublisher(0 until 10000, None)
    val f = Iterant[Task].fromReactivePublisher(pub, 1).mapEval(Task.evalAsync(_)).sumL.runAsync

    for (r <- f) yield {
      assertEquals(r, (10000L * 9999) / 2)
    }
  }

  testAsync("convert range(0, 10000) publisher with requestCount = 256") { implicit sc =>
    val pub = new RangePublisher(0 until 10000, None)
    val f = Iterant[Task].fromReactivePublisher(pub, 256).sumL.runAsync

    for (r <- f) yield {
      assertEquals(r, (10000L * 9999) / 2)
    }
  }

  testAsync("convert range(0, 10000) publisher with requestCount = 256 and async tasks") { implicit sc =>
    val pub = new RangePublisher(0 until 10000, None)
    val f = Iterant[Task].fromReactivePublisher(pub, 256).mapEval(Task.evalAsync(_)).sumL.runAsync

    for (r <- f) yield {
      assertEquals(r, (10000L * 9999) / 2)
    }
  }

  class RangePublisher(from: Int, until: Int, step: Int, finish: Option[Throwable], onCancel: Promise[Unit])
    (implicit sc: Scheduler)
    extends Publisher[Int] {

    def this(range: Range, finish: Option[Throwable])(implicit sc: Scheduler) =
      this(range.start, range.end, range.step, finish, null)

    def this(range: Range, finish: Option[Throwable], onCancel: Promise[Unit])(implicit sc: Scheduler) =
      this(range.start, range.end, range.step, finish, onCancel)

    def subscribe(s: Subscriber[_ >: Int]): Unit = {
      s.onSubscribe(new Subscription { self =>
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
                var requested = self.requested.get
                var toSend = requested

                while (toSend > 0 && isInRange(index, until, step) && !cancelled.get) {
                  s.onNext(index)
                  index += step
                  toSend -= 1

                  if (toSend == 0) {
                    requested = self.requested.subtractAndGet(requested)
                    toSend = requested
                  }
                }

                if (!isInRange(index, until, step))
                  finish match {
                    case None =>
                      s.onComplete()
                    case Some(e) =>
                      s.onError(e)
                  }
              }
            })
        }

        def cancel(): Unit = {
          cancelled.set(true)
          if (onCancel != null) onCancel.success(())
        }
      })
    }
  }
}
