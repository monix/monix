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

package monix.reactive.internal.rstreams

import minitest.TestSuite
import monix.execution.Ack.{Continue, Stop}
import monix.execution.atomic.{Atomic, AtomicBoolean, AtomicInt}
import monix.execution.schedulers.TestScheduler
import monix.execution.{Ack, Cancelable, Scheduler}
import monix.reactive.{Observable, Observer}
import org.reactivestreams.{Publisher, Subscriber, Subscription}

import scala.concurrent.{Future, Promise}

object PublisherIsObservableSuite extends TestSuite[TestScheduler] {
  def setup() = TestScheduler()
  def tearDown(s: TestScheduler) = {
    s.state.lastReportedError match {
      case null =>
        assert(s.state.tasks.isEmpty, "TestScheduler should have no pending tasks")
      case error =>
        throw error
    }
  }

  test("should work with stop-and-wait back-pressure") { implicit s =>
    val isPublisherActive = Atomic(true)
    val isObservableActive = Atomic(true)
    val ack = Atomic(Promise[Ack]())

    val requested = Atomic(0)
    val received = Atomic(0)

    val publisher = createPublisher(isPublisherActive, requested, 1)
    subscribeToObservable(publisher, 1, ack, isObservableActive, received)

    s.tick()
    assertEquals(received.get, 1)

    for (i <- 2 to 100) {
      ack.getAndSet(Promise()).success(Continue)
      s.tick()
      assertEquals(received.get, i)
    }

    ack.get.success(Stop); s.tick()
    assertEquals(received.get, 100)
    assert(isObservableActive.get, "isObservableActive")
    assert(!isPublisherActive.get, "!isPublisherActive")
  }

  test("should work with back-pressure") { implicit s =>
    val isPublisherActive = Atomic(true)
    val isObservableActive = Atomic(true)
    val ack = Atomic(Promise[Ack]())

    val requested = Atomic(0)
    val received = Atomic(0)

    val publisher = createPublisher(isPublisherActive, requested, 2)
    subscribeToObservable(publisher, 2, ack, isObservableActive, received)

    s.tick()
    assertEquals(requested.get, 2)
    assertEquals(received.get, 1)

    for (i <- 2 to 100) {
      ack.getAndSet(Promise()).success(Continue)
      s.tick()
      assertEquals(received.get, i)
    }

    ack.get.success(Stop); s.tick()
    assertEquals(received.get, 100)
    assert(isObservableActive.get, "isObservableActive")
    assert(!isPublisherActive.get, "!isPublisherActive")
  }

  test("canceling observable should cancel publisher") { implicit s =>
    val isPublisherActive = Atomic(true)
    val isObservableActive = Atomic(true)
    val ack = Atomic(Promise[Ack]())

    val requested = Atomic(0)
    val received = Atomic(0)

    val publisher = createPublisher(isPublisherActive, requested, 1)
    val conn = subscribeToObservable(publisher, 1, ack, isObservableActive, received)

    s.tick()
    assertEquals(received.get, 1)

    conn.cancel(); s.tick()
    assertEquals(received.get, 1)
    assert(!isPublisherActive.get, "!isPublisherActive")
  }

  private def subscribeToObservable(p: Publisher[Long], requestSize: Int, ack: Atomic[Promise[Ack]], active: AtomicBoolean, received: AtomicInt)
    (implicit s: Scheduler): Cancelable = {

    Observable.fromReactivePublisher(p, requestSize).unsafeSubscribeFn(
      new Observer[Long] {
        def onNext(elem: Long): Future[Ack] = {
          received.increment()
          ack.get.future
        }

        def onError(ex: Throwable): Unit =
          throw ex
        def onComplete(): Unit =
          active := false
      })
  }

  private def createPublisher(isPublisherActive: AtomicBoolean, requested: AtomicInt, requestSize: Int)
    (implicit s: Scheduler): Publisher[Long] = {

    new Publisher[Long] {
      override def subscribe(subscriber: Subscriber[_ >: Long]): Unit =
        subscriber.onSubscribe(new Subscription {
          override def cancel(): Unit =
            isPublisherActive := false

          override def request(n: Long): Unit = {
            assertEquals(n, requestSize)
            requested.increment(requestSize)
            if (isPublisherActive.get) {
              s.executeTrampolined { () =>
                for (_ <- 0 until n.toInt) subscriber.onNext(1)
              }
            }
          }
        })
    }
  }
}
