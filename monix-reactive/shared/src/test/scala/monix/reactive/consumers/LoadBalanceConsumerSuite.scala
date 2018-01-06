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

package monix.reactive.consumers

import cats.laws._
import cats.laws.discipline._

import monix.eval.Callback
import monix.execution.Ack.{Continue, Stop}
import monix.execution.atomic.{Atomic, AtomicInt, AtomicLong}
import monix.execution.cancelables.{AssignableCancelable, BooleanCancelable, CompositeCancelable}
import monix.execution.{Ack, Cancelable, Scheduler}
import monix.execution.exceptions.DummyException
import monix.reactive.internal.consumers.LoadBalanceConsumer
import monix.reactive.observers.Subscriber
import monix.reactive.{BaseTestSuite, Consumer, Observable, Observer}

import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success}

object LoadBalanceConsumerSuite extends BaseTestSuite {
  test("trigger error when parallelism < 1") { implicit s =>
    intercept[IllegalArgumentException] {
      Consumer.loadBalance(0, Consumer.head[Int])
    }
  }

  test("trigger error when array of consumers is empty") { implicit s =>
    intercept[IllegalArgumentException] {
      new LoadBalanceConsumer(1, Array.empty[Consumer[Int,Int]])
    }
  }

  test("aggregate all events") { implicit s =>
    check2 { (source: Observable[Int], rndInt: Int) =>
      // Parallelism value will be between 1 and 16
      val parallelism = {
        val x = math.abs(rndInt)
        val pos = if (x < 0) Int.MaxValue else x
        (pos % 15) + 1
      }

      val consumer = Consumer.loadBalance(parallelism,
        Consumer.foldLeft[Long,Int](0L)(_+_))

      val task1 = source.foldLeftF(0L)(_+_).firstL
      val task2 = source.consumeWith(consumer).map(_.sum)
      task1 <-> task2
    }
  }

  test("aggregate all events with subscribers that stop") { implicit s =>
    check2 { (source: Observable[Int], rndInt: Int) =>
      // Parallelism value will be between 1 and 16
      val parallelism = {
        val x = math.abs(rndInt)
        val pos = if (x < 0) Int.MaxValue else x
        (pos % 15) + 1
      }

      val fold = Consumer.foldLeft[Long,Int](0L)(_+_)
      val justOne = Consumer.headOption[Int].map(_.getOrElse(0).toLong)
      val allConsumers = for (i <- 0 until parallelism) yield
        if (i % 2 == 0) fold else justOne

      val consumer = Consumer.loadBalance(allConsumers:_*)
      val task1 = source.foldLeftF(0L)(_+_).firstL
      val task2 = source.consumeWith(consumer).map(_.sum)
      task1 <-> task2
    }
  }

  test("keep subscribers busy until the end") { implicit s =>
    val iterations = 10000
    val expectedSum = iterations.toLong * (iterations-1) / 2
    val ackPromise = Promise[Ack]()
    val sum = Atomic(0L)
    val wasCompleted = Atomic(0)

    val async = createAsync(sum, wasCompleted)
    val sync = createSync(sum, wasCompleted)
    val busy = createBusy(sum, wasCompleted, ackPromise)

    val finishPromise = Promise[Int]()
    val loadBalancer = Consumer.loadBalance(sync, async, busy, sync, async, busy).map(_.length)
    val (subscriber, _) = loadBalancer.createSubscriber(Callback.fromPromise(finishPromise), s)

    val continue = Observer.feed(subscriber, BooleanCancelable(), (0 until 10000).iterator)
    s.tick()

    assertEquals(continue.syncTryFlatten, Continue)
    assertEquals(sum.get, expectedSum - 2 - 5)

    // Triggering on complete
    subscriber.onComplete(); s.tick()
    assertEquals(wasCompleted.get, 4)
    assertEquals(finishPromise.future.value, None)

    // Continue
    ackPromise.success(Continue); s.tick()
    assertEquals(sum.get, expectedSum)
    assertEquals(wasCompleted.get, 6)
    assertEquals(finishPromise.future.value, Some(Success(6)))
  }

  test("a subscriber triggering an error in onNext will cancel everything") { implicit s =>
    val iterations = 10000
    val ackPromise1 = Promise[Ack]()
    val ackPromise2 = Promise[Ack]()
    val expectedSum = iterations.toLong * (iterations-1) / 2
    val sum = Atomic(0L)
    val wasCompleted = Atomic(0)

    val async = createAsync(sum, wasCompleted)
    val sync = createSync(sum, wasCompleted)
    val busy1 = createBusy(sum, wasCompleted, ackPromise1)
    val busy2 = createBusy(sum, wasCompleted, ackPromise2)

    val finishPromise = Promise[Int]()
    val loadBalancer = Consumer.loadBalance(sync, async, busy1, sync, async, busy2).map(_.length)

    val conn = BooleanCancelable()
    val (subscriber, c) = loadBalancer.createSubscriber(Callback.fromPromise(finishPromise), s)
    c := conn

    val continue = Observer.feed(subscriber, conn, (0 until 10000).iterator)
    s.tick()

    assertEquals(continue.syncTryFlatten, Continue)
    assertEquals(sum.get, expectedSum - 2 - 5)

    // Triggering on complete
    subscriber.onComplete(); s.tick()
    assertEquals(wasCompleted.get, 4)
    assertEquals(finishPromise.future.value, None)

    // Continue
    val dummy = DummyException("dummy")
    ackPromise1.failure(dummy); s.tick()
    assertEquals(wasCompleted.get, 4)
    assertEquals(finishPromise.future.value, Some(Failure(dummy)))
    assert(conn.isCanceled, "conn.isCanceled")
    assertEquals(subscriber.onNext(10), Stop)

    ackPromise2.success(Continue)
    s.tick()

    // We shouldn't have triggered any errors!
    assertEquals(s.state.lastReportedError, null)
  }

  test("a subscriber triggering an error by callback will cancel everything") { implicit s =>
    val iterations = 10000
    val ackPromise1 = Promise[Ack]()
    val ackPromise2 = Promise[Ack]()
    val expectedSum = iterations.toLong * (iterations-1) / 2
    val sum = Atomic(0L)
    val wasCompleted = Atomic(0)

    val async = createAsync(sum, wasCompleted)
    val sync = createSync(sum, wasCompleted)
    val dummy = DummyException("dummy")
    val withError = createErrorSignaling(ackPromise1, dummy)
    val busy = createBusy(sum, wasCompleted, ackPromise2)

    val finishPromise = Promise[Int]()
    val loadBalancer = Consumer.loadBalance(sync, async, withError, sync, async, busy).map(_.length)

    val conn = BooleanCancelable()
    val (subscriber, c) = loadBalancer.createSubscriber(Callback.fromPromise(finishPromise), s)
    c := conn

    val continue = Observer.feed(subscriber, conn, (0 until 10000).iterator)
    s.tick()

    assertEquals(continue.syncTryFlatten, Continue)
    assertEquals(sum.get, expectedSum - 2 - 5)

    // Triggering on complete
    subscriber.onComplete(); s.tick()
    assertEquals(wasCompleted.get, 4)
    assertEquals(finishPromise.future.value, None)

    // Continue
    ackPromise1.success(Continue); s.tick()
    assertEquals(wasCompleted.get, 4)
    assertEquals(finishPromise.future.value, Some(Failure(dummy)))
    assert(conn.isCanceled, "conn.isCanceled")
    assertEquals(subscriber.onNext(10), Stop)

    ackPromise2.success(Continue)
    s.tick()

    // We shouldn't have triggered any errors!
    assertEquals(s.state.lastReportedError, null)
  }

  test("a subscriber can cancel at any time") { implicit s =>
    val sum = Atomic(0L)
    val wasCompleted = Atomic(0)

    val composite = CompositeCancelable()
    val cancelableConsumer = createCancelable(sum, wasCompleted, composite)
    val sync = createSync(sum, wasCompleted)
    val loadBalancer = Consumer.loadBalance(sync, cancelableConsumer, sync, cancelableConsumer).map(_.length)

    val finishPromise = Promise[Int]()
    val (subscriber, _) = loadBalancer.createSubscriber(Callback.fromPromise(finishPromise), s)

    for (_ <- 0 until 4) assertEquals(subscriber.onNext(1), Continue)
    s.tick()
    assertEquals(sum.get, 4 + 2)

    for (_ <- 0 until 4) assertEquals(subscriber.onNext(1), Continue)
    s.tick()
    assertEquals(sum.get, 8 + 2 + 2)

    composite.cancel(); s.tick()
    for (_ <- 0 until 4) { assertEquals(subscriber.onNext(1), Continue); s.tick() }
    assertEquals(sum.get, 12 + 4)

    subscriber.onComplete(); s.tick()
    assertEquals(wasCompleted.get, 2)
    assertEquals(finishPromise.future.value, Some(Success(4)))
  }

  def createCancelable(sum: AtomicLong, wasCompleted: AtomicInt, conn: CompositeCancelable): Consumer[Int, Unit] =
    new Consumer[Int, Unit] {
      def createSubscriber(cb: Callback[Unit], s: Scheduler): (Subscriber[Int], AssignableCancelable) = {
        val sendFinal = Cancelable { () => cb.onSuccess(()) }
        val c = new AssignableCancelable {
          def cancel(): Unit = conn.cancel()
          def `:=`(value: Cancelable): this.type = {
            conn += value
            conn += sendFinal
            this
          }
        }

        val sub = new Subscriber[Int] {
          implicit val scheduler = s
          def onNext(elem: Int) = {
            sum.increment(elem+1)
            Continue
          }

          def onError(ex: Throwable): Unit = ()
          def onComplete(): Unit =
            wasCompleted.increment()
        }

        (sub, c)
      }
    }

  def createSync(sum: AtomicLong, wasCompleted: AtomicInt): Consumer[Int, Unit] =
    Consumer.fromObserver { _ =>
      new Observer.Sync[Int] {
        def onNext(elem: Int) = {
          sum.increment(elem)
          Continue
        }

        def onError(ex: Throwable): Unit = ()
        def onComplete(): Unit =
          wasCompleted.increment()
      }
    }

  def createAsync(sum: AtomicLong, wasCompleted: AtomicInt): Consumer[Int, Unit] =
    Consumer.fromObserver { implicit scheduler =>
      new Observer[Int] {
        def onNext(elem: Int) = {
          sum.increment(elem)
          Future(Continue)
        }
        def onError(ex: Throwable): Unit = ()
        def onComplete(): Unit =
          wasCompleted.increment()
      }
    }

  def createBusy(sum: AtomicLong, wasCompleted: AtomicInt, ack: Promise[Ack]): Consumer[Int, Unit] =
    Consumer.fromObserver { implicit scheduler =>
      new Observer[Int] {
        def onNext(elem: Int) =
          ack.future.map { r =>
            sum.increment(elem)
            r
          }

        def onError(ex: Throwable): Unit = ()
        def onComplete(): Unit =
          wasCompleted.increment()
      }
    }

  def createErrorSignaling(ack: Promise[Ack], ex: Throwable): Consumer[Int, Unit] =
    new Consumer[Int, Unit] {
      def createSubscriber(cb: Callback[Unit], s: Scheduler): (Subscriber[Int], AssignableCancelable) = {
        val sub = new Subscriber[Int] {
          implicit val scheduler = s

          def onNext(elem: Int) =
            ack.future.map { _ =>
              cb.onError(ex)
              Stop
            }

          def onError(ex: Throwable): Unit = ()
          def onComplete(): Unit = ()
        }

        (sub, AssignableCancelable.dummy)
      }
    }
}
