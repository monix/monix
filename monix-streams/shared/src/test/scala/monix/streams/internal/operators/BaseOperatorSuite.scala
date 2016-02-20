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

package monix.streams.internal.operators

import minitest.TestSuite
import monix.execution.Ack
import monix.execution.FutureUtils.ops._
import monix.execution.schedulers.TestScheduler
import monix.execution.Ack.{Cancel, Continue}
import monix.streams.{Observer, Observable}
import monix.streams.exceptions.DummyException

import scala.concurrent.duration._
import scala.concurrent.{Future, Promise}
import scala.util.Random


trait BaseOperatorSuite extends TestSuite[TestScheduler] {
  case class Sample(
    observable: Observable[Long],
    count: Int,
    sum: Long,
    waitFirst: FiniteDuration,
    waitNext: FiniteDuration)

  def setup(): TestScheduler = TestScheduler()
  def tearDown(s: TestScheduler): Unit = {
    assert(s.state.get.tasks.isEmpty,
      "TestScheduler should have no pending tasks")
  }

  /**
   * Returns an observable that emits from its data-source
   * the specified `sourceCount` number of items. The `sourceCount`
   * is not necessarily equal to the number of elements emitted by
   * the resulting observable, being just a way to randomly vary
   * the events being emitted.
   */
  protected def createObservable(sourceCount: Int): Option[Sample]

  /**
   * Optionally build an observable that simulates an error in user
   * code (if such a thing is possible for the tested operator.
   *
   * It first emits elements, followed by an error triggered
   * within the user-provided portion of the operator.
   */
  protected def brokenUserCodeObservable(sourceCount: Int, ex: Throwable): Option[Sample]

  /**
   * Optionally builds an observable that first emits the
   * items and then ends in error triggered by user code
   * (only for operators that execute user specified code).
   */
  protected def observableInError(sourceCount: Int, ex: Throwable): Option[Sample]

  /**
   * Helper for quickly creating an observable ending with onError.
   */
  def createObservableEndingInError(source: Observable[Long], ex: Throwable): Observable[Long] =
    Observable.unsafeCreate[Long] { subscriber =>
      implicit val s = subscriber.scheduler

      source.unsafeSubscribeFn(new Observer[Long] {
        def onNext(elem: Long) =
          subscriber.onNext(elem)

        def onError(ex: Throwable) =
          subscriber.onError(ex)

        def onComplete() =
          subscriber.onError(ex)
      })
    }

  test("should emit exactly the requested elements") { implicit s =>
    val sourceCount = Random.nextInt(300) + 100
    var received = 0
    var wasCompleted = false

    createObservable(sourceCount) match {
      case None => ignore()
      case Some(Sample(obs, count, sum, waitForFirst, waitForNext)) =>
        obs.unsafeSubscribeFn(new Observer[Long] {
          def onNext(elem: Long): Ack = {
            received += 1
            Continue
          }

          def onError(ex: Throwable): Unit = ()
          def onComplete(): Unit = wasCompleted = true
        })

        s.tick(waitForFirst + waitForNext * (count - 1))
        assertEquals(received, count)
        s.tick(waitForNext)
        assert(wasCompleted)
    }
  }

  test("should work for synchronous observers") { implicit s =>
    val sourceCount = Random.nextInt(300) + 100
    var received = 0
    var total = 0L

    createObservable(sourceCount) match {
      case None => ignore()
      case Some(Sample(obs, count, sum, waitForFirst, waitForNext)) =>
        obs.unsafeSubscribeFn(new Observer[Long] {
          private[this] var sum = 0L

          def onNext(elem: Long): Continue = {
            received += 1
            sum += elem
            Continue
          }

          def onError(ex: Throwable): Unit =
            throw new IllegalStateException()
          def onComplete(): Unit =
            total = sum
        })

        s.tick(waitForFirst + waitForNext * count)
        assertEquals(received, count)
        assertEquals(total, sum)
    }
  }

  test("should work for asynchronous observers") { implicit s =>
    val sourceCount = Random.nextInt(300) + 100
    var received = 0
    var total = 0L

    createObservable(sourceCount) match {
      case None => ignore()
      case Some(Sample(obs, count, sum, waitForFirst, waitForNext)) =>
        obs.unsafeSubscribeFn(new Observer[Long] {
          private[this] var sum = 0L
          private[this] var ack: Future[Ack] = Continue

          def onNext(elem: Long): Future[Ack] = {
            ack = Future.delayedResult(100.millis) {
              received += 1
              sum += elem
              Continue
            }

            ack
          }

          def onError(ex: Throwable): Unit =
            throw new IllegalStateException()
          def onComplete(): Unit =
            ack.syncOnContinue { total = sum }
        })

        s.tick(waitForFirst + waitForNext * count + 100.millis * count)
        assertEquals(received, count)
        assertEquals(total, sum)
    }
  }

  test("should back-pressure all the way") { implicit s =>
    val sourceCount = Random.nextInt(300) + 100
    var p = Promise[Continue]()
    var wasCompleted = false
    var received = 0

    createObservable(sourceCount) match {
      case None => ignore()
      case Some(Sample(obs, count, sum, waitForFirst, waitForNext)) =>
        obs.unsafeSubscribeFn(new Observer[Long] {
          def onNext(elem: Long): Future[Continue] = {
            received += 1
            p.future
          }

          def onError(ex: Throwable): Unit = throw new IllegalStateException()
          def onComplete(): Unit = wasCompleted = true
        })

        for (index <- 1 to count) {
          if (index == 1)
            s.tick(waitForFirst)
          else
            s.tick(waitForNext)

          val old = p; p = Promise()

          if (index == count) {
            old.success(Continue)
            s.tick(waitForNext)
            assert(wasCompleted)
            assertEquals(received, count)
          } else {
            if (index < count-1) assertEquals(received, index)
            old.success(Continue)
          }
        }
    }
  }

  test("should protect user-level code") { implicit s =>
    val sourceCount = Random.nextInt(300) + 100

    brokenUserCodeObservable(sourceCount, DummyException("dummy")) match {
      case None => ignore()
      case Some(Sample(obs, count, sum, waitForFirst, waitForNext)) =>
        var thrownError: Throwable = null
        var received = 0
        var receivedSum = 0L

        obs.unsafeSubscribeFn(new Observer[Long] {
          def onNext(elem: Long): Continue = {
            received += 1
            receivedSum += elem
            Continue
          }

          def onComplete(): Unit = ()
          def onError(ex: Throwable): Unit = {
            thrownError = ex
          }
        })

        s.tick(waitForFirst + waitForNext * (count - 1))
        assertEquals(received, count)
        assertEquals(receivedSum, sum)
        assertEquals(thrownError, DummyException("dummy"))
    }
  }

  test("onError should work") { implicit s =>
    val sourceCount = Random.nextInt(300) + 100

    observableInError(sourceCount, DummyException("dummy")) match {
      case Some(Sample(obs, count, sum, waitForFirst, waitForNext)) if count > 0 =>
        var thrownError: Throwable = null
        var received = 0

        obs.unsafeSubscribeFn(new Observer[Long] {
          def onNext(elem: Long): Future[Continue] = {
            received += 1
            Future(Continue)
          }

          def onError(ex: Throwable): Unit = thrownError = ex
          def onComplete(): Unit = throw new IllegalStateException()
        })

        s.tick(waitForFirst + waitForNext * count)
        assertEquals(received, count)
        assertEquals(thrownError, DummyException("dummy"))

      case Some(Sample(obs, _, _, waitForFirst, _)) =>
        // observable emits error right away, as count is zero
        var thrownError: Throwable = null
        var received = 0

        obs.unsafeSubscribeFn(new Observer[Long] {
          def onNext(elem: Long): Continue = {
            received += 1
            Continue
          }

          def onError(ex: Throwable): Unit = thrownError = ex
          def onComplete(): Unit = throw new IllegalStateException()
        })

        s.tick(waitForFirst)
        assertEquals(received, 0)
        assertEquals(thrownError, DummyException("dummy"))

      case None =>
        ignore()
    }
  }

  test("should stop on first onNext") { implicit s =>
    val sourceCount = Random.nextInt(300) + 100

    createObservable(sourceCount) match {
      case None => ignore()
      case Some(Sample(o, count, sum, waitForFirst, waitForNext)) =>
        var wasCompleted = false
        var received = 0

        o.unsafeSubscribeFn(new Observer[Long] {
          def onNext(elem: Long): Cancel = {
            received += 1
            Cancel
          }

          def onError(ex: Throwable): Unit = ()

          def onComplete(): Unit = {
            wasCompleted = true
          }
        })

        s.tick(waitForFirst)
        assert(!wasCompleted)
        assertEquals(received, 1)
        s.tick(waitForNext * 2)
        assertEquals(received, 1)
        assert(!wasCompleted)
    }
  }

  test("should not back-pressure onError") { implicit s =>
    val p = Promise[Continue]()
    var wasCompleted = false

    observableInError(1, DummyException("dummy")) match {
      case None => ignore()
      case ref @ Some(Sample(obs, count, sum, waitForFirst, waitForNext)) =>
        obs.unsafeSubscribeFn(new Observer[Long] {
          def onNext(elem: Long): Future[Continue] = p.future
          def onError(ex: Throwable): Unit = wasCompleted = true
          def onComplete(): Unit = throw new IllegalStateException()
        })

        s.tick(waitForFirst)
        assert(wasCompleted)
        p.success(Continue)
        s.tick(waitForNext)
    }
  }
}

