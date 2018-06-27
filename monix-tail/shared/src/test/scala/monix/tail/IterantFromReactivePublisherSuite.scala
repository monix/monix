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

import cats.effect.IO
import cats.laws._
import cats.laws.discipline._
import monix.eval.Task
import monix.execution.Scheduler
import monix.execution.atomic.Atomic
import monix.execution.exceptions.DummyException
import org.reactivestreams.{Publisher, Subscriber, Subscription}
import org.scalacheck.{Arbitrary, Gen}

import scala.util.Failure
import monix.execution.rstreams.ReactivePullStrategy
import monix.execution.rstreams.ReactivePullStrategy.StopAndWait


object IterantFromReactivePublisherSuite extends BaseTestSuite {

  implicit val arbRange: Arbitrary[Range] = Arbitrary {
    for {
      i <- Gen.choose(-100, 100)
      j <- Gen.choose(-100, 100)
      Array(min, max) = Array(i, j).sorted
      step <- Gen.oneOf(1, 2, 3)
    } yield min to max by step
  }

  test("fromReactivePublisher emits values in correct order") { implicit s =>
    check1 { range: Range =>
      val publisher = new RangePublisher(range, None)
      Iterant[IO].fromReactivePublisher(publisher, StopAndWait) <-> Iterant[IO].fromSeq(range)
    }
  }

//  test("fromReactivePublisher cancels subscription on earlyStop") { implicit s =>
//    implicit val pullStrategy: ReactivePullStrategy = ReactivePullStrategy.Batched(8)
//    val publisher = new RangePublisher(1 to 64, None)
//    Iterant[Task].fromReactivePublisher(publisher)
//      .take(5)
//      .completeL
//      .runAsync
//
//    s.tick()
//
//    assert(publisher.emitted < 64)
//    assert(publisher.cancelled)
//  }
//
//  test("fromReactivePublisher propagates errors") { implicit s =>
//    val dummy = DummyException("dummy")
//    val publisher = new RangePublisher(1 to 64, Some(dummy))
//    val f = Iterant[Task].fromReactivePublisher(publisher)
//      .completeL
//      .runAsync
//
//    s.tick()
//
//    assertEquals(f.value, Some(Failure(dummy)))
//  }
//
//  test("fromReactivePublisher(it.toReactivePublisher) is identity") { implicit s =>
//    check1 { it: Iterant[IO, Int] =>
//      Iterant[IO].fromReactivePublisher(it.toReactivePublisher) <-> it
//    }
//  }
//
  class RangePublisher(from: Int, until: Int, step: Int, finish: Option[Throwable])
    (implicit sc: Scheduler) extends Publisher[Int] {

    def this(range: Range, finish: Option[Throwable]) =
      this(range.start, range.end, range.step, finish)

    def subscribe(s: Subscriber[_ >: Int]): Unit = {
      s.onSubscribe(new Subscription { self =>
        private[this] val cancelled = Atomic(false)
        private[this] val requested = Atomic(0L)
        private[this] var index = from

        def request(n: Long): Unit = {
          if (requested.getAndAdd(n) == 0)
            sc.execute(new Runnable {
              def run(): Unit = {
                var requested = self.requested.get
                var toSend = requested

                while (toSend > 0 && index < from && !cancelled.get) {
                  s.onNext(index)
                  index += step
                  toSend -= 1

                  if (toSend == 0) {
                    requested = self.requested.subtractAndGet(requested)
                    toSend = requested
                  }
                }

                if (index >= from)
                  finish match {
                    case None =>
                      s.onComplete()
                    case Some(e) =>
                      s.onError(e)
                  }
              }
            })
        }

        def cancel(): Unit =
          cancelled.set(true)
      })
    }
  }
}
