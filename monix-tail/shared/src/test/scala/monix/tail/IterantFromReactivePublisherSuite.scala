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

import cats.effect.IO
import cats.laws._
import cats.laws.discipline._
import monix.eval.Task
import monix.execution.Scheduler
import monix.execution.atomic.Atomic
import monix.execution.exceptions.DummyException
import org.reactivestreams.{ Publisher, Subscriber, Subscription }
import org.scalacheck.{ Arbitrary, Gen }

import scala.concurrent.Promise
import scala.util.{ Failure, Success }
import scala.concurrent.duration._

object IterantFromReactivePublisherSuite extends BaseTestSuite {

  implicit val arbRange: Arbitrary[Range] = Arbitrary {
    for {
      i <- Gen.choose(-100, 100)
      j <- Gen.choose(-100, 100)
      Array(min, max) = Array(i, j).sorted
      step <- Gen.oneOf(1, 2, 3)
    } yield min until max by step
  }

  test("fromReactivePublisher(bufferSize = 1) emits values in correct order") { implicit s =>
    check1 { (range: Range) =>
      val publisher = new RangePublisher(range, None)
      Iterant[IO].fromReactivePublisher(publisher, 1) <-> Iterant[IO].fromSeq(range)
    }
  }

  test("fromReactivePublisher(bufferSize = 1) can end in error") { implicit s =>
    check1 { (range: Range) =>
      val dummy = DummyException("dummy")
      val publisher = new RangePublisher(range, Some(dummy))
      Iterant[IO].fromReactivePublisher(publisher, 1).attempt <->
        (Iterant[IO].fromSeq(range).map(Right(_)) ++ Iterant[IO].pure[Either[Throwable, Int]](Left(dummy)))
    }
  }

  test("fromReactivePublisher(bufferSize = default) emits values in correct order") { implicit s =>
    check1 { (range: Range) =>
      val publisher = new RangePublisher(range, None)
      Iterant[IO].fromReactivePublisher(publisher) <-> Iterant[IO].fromSeq(range)
    }
  }

  test("fromReactivePublisher(bufferSize = default) can end in error") { implicit s =>
    check1 { (range: Range) =>
      val dummy = DummyException("dummy")
      val publisher = new RangePublisher(range, Some(dummy))
      Iterant[IO].fromReactivePublisher(publisher).attempt <->
        (Iterant[IO].fromSeq(range).map(Right(_)) ++ Iterant[IO].pure[Either[Throwable, Int]](Left(dummy)))
    }
  }

  test("fromReactivePublisher(bufferSize = default) with slow consumer") { implicit s =>
    check1 { (range: Range) =>
      val publisher = new RangePublisher(range, None)
      val lh = Iterant[Task].fromReactivePublisher(publisher).mapEval(x => Task(x).delayExecution(10.millis))
      lh <-> Iterant[Task].fromSeq(range)
    }
  }

  test("fromReactivePublisher cancels subscription on earlyStop") { implicit s =>
    val cancelled = Promise[Unit]()
    val publisher = new RangePublisher(1 to 64, None, cancelled)
    Iterant[Task]
      .fromReactivePublisher(publisher, 8)
      .take(5)
      .completedL
      .runToFuture

    s.tick()
    assertEquals(cancelled.future.value, Some(Success(())))
  }

  test("fromReactivePublisher propagates errors") { implicit s =>
    val dummy = DummyException("dummy")
    val publisher = new RangePublisher(1 to 64, Some(dummy))
    val f = Iterant[Task].fromReactivePublisher(publisher).completedL.runToFuture

    s.tick()
    assertEquals(f.value, Some(Failure(dummy)))
  }

  test("fromReactivePublisher(it.toReactivePublisher) is identity") { implicit s =>
    check1 { (it: Iterant[IO, Int]) =>
      Iterant[IO].fromReactivePublisher(it.toReactivePublisher) <-> it
    }
  }

  test("fromReactivePublisher handles immediate completion") { implicit s =>
    val publisher = new Publisher[Unit] {
      def subscribe(subscriber: Subscriber[_ >: Unit]): Unit = {
        subscriber.onComplete()
      }
    }
    val f = Iterant[Task].fromReactivePublisher(publisher).completedL.runToFuture

    s.tick()
    assertEquals(f.value, Some(Success(())))
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

                while (toSend > 0 && isInRange(index.toLong, until.toLong, step.toLong) && !cancelled.get()) {
                  s.onNext(index)
                  index += step
                  toSend -= 1

                  if (toSend == 0) {
                    requested = self.requested.subtractAndGet(requested)
                    toSend = requested
                  }
                }

                if (!isInRange(index.toLong, until.toLong, step.toLong))
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
          if (onCancel != null) {
            onCancel.success(())
            ()
          }
        }
      })
    }
  }
}
