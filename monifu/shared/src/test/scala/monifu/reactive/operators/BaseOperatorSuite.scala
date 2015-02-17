/*
 * Copyright (c) 2015 Alexandru Nedelcu
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package monifu.reactive.operators

import minitest.TestSuite
import monifu.concurrent.extensions._
import monifu.concurrent.schedulers.TestScheduler
import monifu.reactive.Ack.Continue
import monifu.reactive.{DummyException, Observable, Observer}
import scala.concurrent.duration._
import scala.concurrent.{Future, Promise}
import scala.util.Random


trait BaseOperatorSuite extends TestSuite[TestScheduler] {
  def setup() = TestScheduler()
  def tearDown(s: TestScheduler) = {
    assert(s.state.get.tasks.isEmpty,
      "TestScheduler should have no pending tasks")
  }

  /**
   * Returns an observable that emits from its data-source
   * the specified `sourceCount` number of items.
   * 
   * NOTE: the final observable may emit more or less than 
   *       the specified amount, however if `sourceCount`
   *       is one, then the built observable should also
   *       emit a single item and then stop.
   */
  def observable(sourceCount: Int): Option[Observable[Long]]

  /**
   * Returns the total sum of the elements emitted.
   */
  def sum(sourceCount: Int): Long

  /**
   * Returns the count of the elements emitted.
   */
  def count(sourceCount: Int): Int

  /**
   * Optionally build an observable that simulates an error in user
   * code (if such a thing is possible for the tested operator.
   *
   * It first emits count-1 elements, followed by an error triggered
   * within the user-provided portion of the operator.
   */
  def brokenUserCodeObservable(sourceCount: Int, ex: Throwable): Option[Observable[Long]]

  /**
   * Optionally builds an observable that first emits the `count`
   * items and then ends in error.
   */
  def observableInError(sourceCount: Int, ex: Throwable): Option[Observable[Long]]

  /**
   * Specifies how long to wait for an element to be emitted
   * (except for the first one).
   */
  def waitForNext: FiniteDuration

  /**
   * Specifies how long to wait for the first element to be emitted.
   */
  def waitForFirst: FiniteDuration

  /**
   * Helper for quickly creating an observable ending with onError.
   */
  def createObservableEndingInError(source: Observable[Long], ex: Throwable) =
    Observable.create[Long] { subscriber =>
      implicit val s = subscriber.scheduler

      source.unsafeSubscribe(new Observer[Long] {
        def onNext(elem: Long) =
          subscriber.observer.onNext(elem)

        def onError(ex: Throwable) =
          subscriber.observer.onError(ex)

        def onComplete() =
          subscriber.observer.onError(ex)
      })
    }

  test("should emit exactly the requested elements") { implicit s =>
    val sourceCount = Random.nextInt(300) + 100
    val count = this.count(sourceCount)

    var received = 0
    var wasCompleted = false

    observable(sourceCount) match {
      case None => ignore()
      case Some(obs) =>
        obs.unsafeSubscribe(new Observer[Long] {
          def onNext(elem: Long) = {
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

  test("should back-pressure for onComplete, for 1 element") { implicit s =>
    val p = Promise[Continue]()
    var wasCompleted = false

    observable(1) match {
      case None => ignore()
      case Some(obs) =>
        obs.unsafeSubscribe(new Observer[Long] {
          def onNext(elem: Long) = p.future
          def onError(ex: Throwable): Unit = throw new IllegalStateException()
          def onComplete(): Unit = wasCompleted = true
        })

        s.tick(waitForFirst)
        assert(!wasCompleted)

        p.success(Continue); s.tick(waitForNext)
        assert(wasCompleted)
    }
  }

  test("should work for synchronous observers") { implicit s =>
    val sourceCount = Random.nextInt(300) + 100

    var received = 0
    var total = 0L

    observable(sourceCount) match {
      case None => ignore()
      case Some(obs) =>
        obs.unsafeSubscribe(new Observer[Long] {
          private[this] var sum = 0L

          def onNext(elem: Long) = {
            received += 1
            sum += elem
            Continue
          }

          def onError(ex: Throwable): Unit = throw new IllegalStateException()
          def onComplete(): Unit = total = sum
        })

        s.tick(waitForFirst + waitForNext * count(sourceCount))
        assertEquals(received, count(sourceCount))
        assertEquals(total, sum(sourceCount))
    }
  }

  test("should work for asynchronous observers") { implicit s =>
    val sourceCount = Random.nextInt(300) + 100
    val count = this.count(sourceCount)

    var received = 0
    var total = 0L

    observable(sourceCount) match {
      case None => ignore()
      case Some(obs) =>
        obs.unsafeSubscribe(new Observer[Long] {
          private[this] var sum = 0L

          def onNext(elem: Long) =
            Future.delayedResult(100.millis) {
              received += 1
              sum += elem
              Continue
            }

          def onError(ex: Throwable): Unit = throw new IllegalStateException()
          def onComplete(): Unit = total = sum
        })

        s.tick(waitForFirst + waitForNext * (count - 1) + 100.millis * count)
        assertEquals(received, count)
        assertEquals(total, sum(sourceCount))
    }
  }

  test("should back-pressure all the way") { implicit s =>
    val sourceCount = Random.nextInt(300) + 100

    var p = Promise[Continue]()
    var wasCompleted = false
    var received = 0

    observable(sourceCount) match {
      case None => ignore()
      case Some(obs) =>
        obs.unsafeSubscribe(new Observer[Long] {
          def onNext(elem: Long) = {
            received += 1
            p.future
          }

          def onError(ex: Throwable): Unit = throw new IllegalStateException()
          def onComplete(): Unit = wasCompleted = true
        })

        val count = this.count(sourceCount)

        for (index <- 1 to count) {
          if (index == 1)
            s.tick(waitForFirst)
          else
            s.tick(waitForNext)

          assertEquals(received, index)

          val old = p; p = Promise()

          if (index == count) {
            assert(!wasCompleted)
            old.success(Continue)
            s.tick(waitForNext)
            assert(wasCompleted)
            assertEquals(received, count)
          }
          else {
            old.success(Continue)
          }
        }
    }
  }

  test("should protect user-level code") { implicit s =>
    val sourceCount = Random.nextInt(300) + 100

    brokenUserCodeObservable(sourceCount, DummyException("dummy")) match {
      case None => ignore()
      case Some(obs) =>
        var thrownError: Throwable = null
        var received = 0

        obs.unsafeSubscribe(new Observer[Long] {
          def onNext(elem: Long) = {
            received += 1
            Continue
          }

          def onError(ex: Throwable): Unit = thrownError = ex
          def onComplete(): Unit = throw new IllegalStateException()
        })

        val count = this.count(sourceCount - 1)
        s.tick(waitForFirst + waitForNext * (count - 1))

        assertEquals(received, count)
        assertEquals(thrownError, DummyException("dummy"))
    }
  }

  test("should back-pressure onError") { implicit s =>
    val sourceCount = Random.nextInt(300) + 100
    val count = this.count(sourceCount)

    observableInError(sourceCount, DummyException("dummy")) match {
      case None => ignore()
      case Some(obs) =>
        var thrownError: Throwable = null
        var received = 0

        obs.unsafeSubscribe(new Observer[Long] {
          def onNext(elem: Long) = {
            received += 1
            if (received == count)
              Future.delayedResult(1.hour)(Continue)
            else
              Continue
          }

          def onError(ex: Throwable): Unit = thrownError = ex
          def onComplete(): Unit = throw new IllegalStateException()
        })

        s.tick(waitForFirst + waitForNext * (count - 1))
        assertEquals(received, count)
        assertEquals(thrownError, null)

        s.tick(1.hour)
        assertEquals(thrownError, DummyException("dummy"))
    }
  }
}

