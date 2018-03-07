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

package monix.reactive.internal.operators

import cats.laws._
import cats.laws.discipline._

import monix.eval.Task
import monix.execution.Ack
import monix.execution.Ack.Continue
import monix.execution.FutureUtils.extensions._
import monix.execution.Scheduler
import monix.reactive.{Observable, Observer}
import monix.execution.exceptions.DummyException

import scala.concurrent.duration._
import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Random}

object MapTaskSuite extends BaseOperatorSuite {
  def createObservable(sourceCount: Int) = Some {
    val o = Observable.range(0, sourceCount).mapTask(x => Task(x))
    Sample(o, count(sourceCount), sum(sourceCount), waitFirst, waitNext)
  }

  def count(sourceCount: Int) =
    sourceCount

  def waitFirst = Duration.Zero
  def waitNext = Duration.Zero

  def observableInError(sourceCount: Int, ex: Throwable) =
    if (sourceCount == 1) None else Some {
      val o = createObservableEndingInError(Observable.range(0, sourceCount), ex)
        .mapTask(i => Task.now(i))

      Sample(o, count(sourceCount), sum(sourceCount), waitFirst, waitNext)
    }

  def sum(sourceCount: Int) = {
    sourceCount * (sourceCount - 1) / 2
  }

  def brokenUserCodeObservable(sourceCount: Int, ex: Throwable) = Some {
    val o = Observable.range(0, sourceCount).mapTask { i =>
      if (i == sourceCount-1)
        throw ex
      else
        Task.now(i)
    }

    Sample(o, count(sourceCount-1), sum(sourceCount-1), waitFirst, waitNext)
  }

  def toList[A](o: Observable[A])(implicit s: Scheduler) = {
    o.foldLeftF(Vector.empty[A])(_ :+ _).runAsyncGetLast
      .map(_.getOrElse(Vector.empty))
  }

  override def cancelableObservables(): Seq[Sample] = {
    val sample1 =  Observable.range(1, 100)
      .mapTask(x => Task.now(x).delayExecution(1.second))
    val sample2 = Observable.range(0, 100).delayOnNext(1.second)
      .mapTask(x => Task.now(x).delayExecution(1.second))

    Seq(
      Sample(sample1, 0, 0, 0.seconds, 0.seconds),
      Sample(sample1, 1, 1, 1.seconds, 0.seconds),
      Sample(sample2, 0, 0, 0.seconds, 0.seconds)
    )
  }

  test("should work synchronously for synchronous observers") { implicit s =>
    val sourceCount = Random.nextInt(300) + 100
    var received = 0
    var total = 0L

    val obs = Observable.range(0, sourceCount).mapTask(x => Task.now(x))
    obs.unsafeSubscribeFn(new Observer[Long] {
      private[this] var sum = 0L

      def onNext(elem: Long): Ack = {
        received += 1
        sum += elem
        Continue
      }

      def onError(ex: Throwable): Unit = throw new IllegalStateException()
      def onComplete(): Unit = total = sum
    })

    assertEquals(received, count(sourceCount))
    assertEquals(total, sum(sourceCount))
  }

  test("should work for asynchronous observers") { implicit s =>
    val sourceCount = Random.nextInt(300) + 100
    var received = 0
    var total = 0L

    val obs = Observable.range(0, sourceCount).mapTask(x => Task(x))
    obs.unsafeSubscribeFn(new Observer[Long] {
      private[this] var sum = 0L

      def onNext(elem: Long): Ack = {
        received += 1
        sum += elem
        Continue
      }

      def onError(ex: Throwable): Unit = throw new IllegalStateException()
      def onComplete(): Unit = total = sum
    })

    assertEquals(total, 0)
    s.tick()

    assertEquals(received, count(sourceCount))
    assertEquals(total, sum(sourceCount))
  }

  test("map can be expressed in terms of mapTask") { implicit s =>
    check2 { (list: List[Int], isAsync: Boolean) =>
      val received = Observable.fromIterable(list)
        .mapTask(x => if (isAsync) Task(x + 10) else Task.eval(x + 10))
        .toListL

      val expected = Observable.fromIterable(list).map(_ + 10).toListL
      received <-> expected
    }
  }

  test("should wait the completion of the current, before subscribing to the next") { implicit s =>
    var received = 0L
    var continued = 0
    var wasCompleted = false

    val p1 = Promise[Long]()
    val task1 = Task.fromFuture(p1.future)
    val task2 = Task.eval(100L)

    Observable.fromIterable(Seq(task1, task2)).mapTask(x => x).unsafeSubscribeFn(new Observer[Long] {
      def onNext(elem: Long) = {
        received += elem
        Future.delayedResult(1.second) {
          continued += 1
          Continue
        }
      }

      def onError(ex: Throwable) = ()
      def onComplete() = wasCompleted = true
    })

    s.tick()
    assertEquals(received, 0)
    p1.success(10)
    s.tick()
    assertEquals(received, 10)

    s.tick(1.second)
    assertEquals(received, 10 + 100)
    assert(wasCompleted)

    assertEquals(continued, 1)
    s.tick(1.second)
    assertEquals(continued, 2)
  }

  test("should interrupt the streaming on error, test #1") { implicit s =>
    val dummy = DummyException("dummy")
    var wasThrown: Throwable = null
    var received = 0L

    val p1 = Promise[Long]()
    val task1 = Task.fromFuture(p1.future)
    val p2 = Promise[Long]()
    val task2 = Task.fromFuture(p2.future)
    val task3 = Task.eval(100L)

    Observable.fromIterable(Seq(task1, task2, task3)).mapTask(x => x)
      .unsafeSubscribeFn(new Observer[Long] {
        def onNext(elem: Long) = {
          received += elem
          Future.delayedResult(1.second)(Continue)
        }

        def onError(ex: Throwable) =
          wasThrown = ex
        def onComplete() =
          throw new IllegalStateException("onComplete")
      })

    s.tick()
    assertEquals(received, 0)
    p1.success(10)
    s.tick()
    assertEquals(received, 10)

    p2.failure(dummy)
    s.tick()
    assertEquals(wasThrown, null)

    s.tick(1.second)
    assertEquals(wasThrown, dummy)

    s.tick(1.second)
    assertEquals(received, 10)
  }

  test("should interrupt the streaming on error, test #2") { implicit s =>
    val dummy = DummyException("dummy")
    var wasThrown: Throwable = null
    var received = 0L

    Observable(1L,2L,3L).endWithError(dummy).mapTask(x => Task(x))
      .unsafeSubscribeFn(new Observer[Long] {
        def onNext(elem: Long) = {
          received += elem
          Future.delayedResult(1.second)(Continue)
        }

        def onError(ex: Throwable) =
          wasThrown = ex
        def onComplete() =
          throw new IllegalStateException("onComplete")
      })

    s.tick(1.second)
    assertEquals(received, 3)
    assertEquals(wasThrown, null)

    s.tick(1.seconds)
    assertEquals(received, 6)
    assertEquals(wasThrown, dummy)
    s.tick(1.second)
  }

  test("should interrupt the streaming on error, test #3") { implicit s =>
    val dummy = DummyException("dummy")
    var wasThrown: Throwable = null
    var received = 0L

    Observable(1L,2L,3L).endWithError(dummy).mapTask(x => Task.now(x))
      .unsafeSubscribeFn(new Observer[Long] {
        def onNext(elem: Long) = {
          received += elem
          Continue
        }

        def onError(ex: Throwable) =
          wasThrown = ex
        def onComplete() =
          throw new IllegalStateException("onComplete")
      })

    assertEquals(received, 6)
    assertEquals(wasThrown, dummy)
  }

  test("should not break the contract on user-level error #1") { implicit s =>
    val dummy1 = DummyException("dummy1")
    val dummy2 = DummyException("dummy2")

    val source = Observable.now(1L).endWithError(dummy1)
    val obs: Observable[Long] = source.mapTask { i => Task.raiseError(dummy2) }

    var thrownError: Throwable = null
    var received = 0
    var onCompleteReceived = false
    var onErrorReceived = 0

    obs.unsafeSubscribeFn(new Observer[Long] {
      def onNext(elem: Long): Ack = {
        received += 1
        Continue
      }

      def onComplete(): Unit =
        onCompleteReceived = true
      def onError(ex: Throwable): Unit = {
        onErrorReceived += 1
        thrownError = ex
      }
    })

    s.tick()
    assertEquals(received, 0)
    assertEquals(thrownError, dummy2)
    assert(!onCompleteReceived, "!onCompleteReceived")
    assertEquals(onErrorReceived, 1)
  }

  test("should not break the contract on user-level error #2") { implicit s =>
    val dummy1 = DummyException("dummy1")
    val dummy2 = DummyException("dummy2")

    val source = Observable.now(1L).endWithError(dummy1)
    val obs: Observable[Long] = source.mapTask { i => throw dummy2 }

    var thrownError: Throwable = null
    var received = 0
    var onCompleteReceived = false
    var onErrorReceived = 0

    obs.unsafeSubscribeFn(new Observer[Long] {
      def onNext(elem: Long): Ack = {
        received += 1
        Continue
      }

      def onComplete(): Unit =
        onCompleteReceived = true
      def onError(ex: Throwable): Unit = {
        onErrorReceived += 1
        thrownError = ex
      }
    })

    s.tick()
    assertEquals(received, 0)
    assertEquals(thrownError, dummy2)
    assert(!onCompleteReceived, "!onCompleteReceived")
    assertEquals(onErrorReceived, 1)
  }

  test("should not break the contract on user-level error #3") { implicit s =>
    val dummy1 = DummyException("dummy1")
    val dummy2 = DummyException("dummy2")

    val source = Observable.now(1L).endWithError(dummy1)
    val obs: Observable[Long] = source.mapTask { i => Task.raiseError(dummy2).executeAsync }

    var thrownError: Throwable = null
    var received = 0
    var onCompleteReceived = false
    var onErrorReceived = 0

    obs.unsafeSubscribeFn(new Observer[Long] {
      def onNext(elem: Long): Ack = {
        received += 1
        Continue
      }

      def onComplete(): Unit =
        onCompleteReceived = true
      def onError(ex: Throwable): Unit = {
        onErrorReceived += 1
        thrownError = ex
      }
    })

    s.tick()
    assertEquals(received, 0)
    assertEquals(thrownError, dummy2)
    assertEquals(s.state.lastReportedError, dummy1)
    assert(!onCompleteReceived, "!onCompleteReceived")
    assertEquals(onErrorReceived, 1)
  }

  test("should be cancelable, test #1") { implicit s =>
    var sumBeforeMap = 0L
    var sumAfterMap = 0L

    val f = Observable.intervalAtFixedRate(1.second, 4.seconds)
      .take(10)
      .doOnNext { x => sumBeforeMap += x + 1 }
      .mapTask(x => Task.eval(x + 1).delayExecution(2.seconds))
      .doOnNext { x => sumAfterMap += x + 1 }
      .completedL
      .runAsync

    s.tick()
    assertEquals(sumBeforeMap, 0)

    f.cancel()
    assert(s.state.tasks.isEmpty, "state.tasks.isEmpty")
    assertEquals(sumBeforeMap, 0)
    assertEquals(sumAfterMap, 0)

    s.tick(1.hour)
    assertEquals(f.value, None)
  }

  test("should be cancelable, test #2") { implicit s =>
    var sumBeforeMap = 0L
    var sumAfterMap = 0L

    val f = Observable.intervalAtFixedRate(1.second, 4.seconds)
      .take(10)
      .doOnNext { x => sumBeforeMap += x + 1 }
      .mapTask(x => Task.eval(x + 1).delayExecution(2.seconds))
      .doOnNext { x => sumAfterMap += x + 1 }
      .completedL
      .runAsync

    s.tick(1.second)
    assertEquals(sumBeforeMap, 1)

    f.cancel()
    assert(s.state.tasks.isEmpty, "state.tasks.isEmpty")
    assertEquals(sumBeforeMap, 1)
    assertEquals(sumAfterMap, 0)

    s.tick(1.hour)
    assertEquals(f.value, None)
  }

  test("should be cancelable, test #3") { implicit s =>
    var sumBeforeMap = 0L
    var sumAfterMap = 0L

    val f = Observable.intervalAtFixedRate(1.second, 4.seconds)
      .take(10)
      .doOnNext { x => sumBeforeMap += x + 1 }
      .mapTask(x => Task.eval(x + 1).delayExecution(2.seconds))
      .doOnNext { x => sumAfterMap += x + 1 }
      .completedL
      .runAsync

    s.tick(3.second)
    assertEquals(sumBeforeMap, 1)

    f.cancel()
    assert(s.state.tasks.isEmpty, "state.tasks.isEmpty")
    assertEquals(sumBeforeMap, 1)
    assertEquals(sumAfterMap, 2)

    s.tick(1.hour)
    assertEquals(f.value, None)
  }

  test("should be cancelable, test #4") { implicit s =>
    var sumBeforeMapTask = 0L
    var sumAfterMapTask = 0L
    var sumAfterMapFuture = 0L

    val f = Observable.intervalAtFixedRate(1.second, 4.seconds)
      .take(10)
      .doOnNext { x => sumBeforeMapTask += x + 1 }
      .mapTask(x => Task.eval(x + 1).delayExecution(2.seconds))
      .doOnNext { x => sumAfterMapTask += x + 1 }
      .mapFuture(x => Future.delayedResult(1.second)(x + 1))
      .doOnNext { x => sumAfterMapFuture += x + 1 }
      .completedL
      .runAsync

    s.tick(3.second)
    assertEquals(sumBeforeMapTask, 1)
    assertEquals(sumAfterMapTask, 2)
    assertEquals(sumAfterMapFuture, 0)

    f.cancel()
    assert(s.state.tasks.nonEmpty, "state.tasks.nonEmpty")

    s.tick(1.second)
    assert(s.state.tasks.isEmpty, "state.tasks.isEmpty")
    assertEquals(sumAfterMapFuture, 3)

    s.tick(1.hour)
    assertEquals(f.value, None)
  }

  test("should be cancelable after the main stream has ended") { implicit s =>
    val f = Observable.now(1)
      .mapTask(x => Task(x+1).delayExecution(1.second))
      .sumL
      .runAsync

    s.tick()
    assertEquals(f.value, None)
    assert(s.state.tasks.nonEmpty, "tasks.nonEmpty")

    f.cancel(); s.tick()
    assertEquals(f.value, None)
    assert(s.state.tasks.isEmpty, "tasks.isEmpty")
  }

  test("exceptions can be triggered synchronously by throw") { implicit s =>
    val dummy = DummyException("dummy")
    val source = Observable.now(1L).mapTask(_ => throw dummy)

    val f = source.runAsyncGetLast
    s.tick()

    assertEquals(f.value, Some(Failure(dummy)))
    assertEquals(s.state.lastReportedError, null)
  }

  test("exceptions can be triggered synchronously through raiseError") { implicit s =>
    val dummy = DummyException("dummy")
    val source = Observable.now(1L).mapTask(_ => Task.raiseError(dummy))

    val f = source.runAsyncGetLast
    s.tick()

    assertEquals(f.value, Some(Failure(dummy)))
    assertEquals(s.state.lastReportedError, null)
  }
}
