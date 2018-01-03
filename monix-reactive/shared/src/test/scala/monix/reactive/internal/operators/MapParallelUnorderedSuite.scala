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
import monix.execution.Ack.Continue
import monix.execution.internal.Platform
import monix.execution.exceptions.DummyException
import monix.reactive.{Observable, Observer, OverflowStrategy}

import scala.concurrent.Promise
import scala.concurrent.duration._
import scala.util.{Failure, Random}

object MapParallelUnorderedSuite extends BaseOperatorSuite {
  def createObservable(sourceCount: Int) = Some {
    val o = Observable.range(0, sourceCount).mapParallelUnordered(parallelism = 4)(x => Task(x))
    Sample(o, count(sourceCount), sum(sourceCount), waitFirst, waitNext)
  }

  def sum(sourceCount: Int) =
    sourceCount * (sourceCount - 1) / 2
  def count(sourceCount: Int) =
    sourceCount

  def waitFirst = Duration.Zero
  def waitNext = Duration.Zero

  def observableInError(sourceCount: Int, ex: Throwable) = None
  def brokenUserCodeObservable(sourceCount: Int, ex: Throwable) = None

  override def cancelableObservables(): Seq[Sample] = {
    Seq.empty
    val sample1 =  Observable.range(1, 100)
      .mapParallelUnordered(parallelism = 4)(x => Task.now(x).delayExecution(1.second))
    val sample2 = Observable.range(0, 100).delayOnNext(1.second)
      .mapParallelUnordered(parallelism = 4)(x => Task.now(x).delayExecution(1.second))

    Seq(
      Sample(sample1, 0, 0, 0.seconds, 0.seconds),
      Sample(sample1, 4, 10, 1.seconds, 0.seconds),
      Sample(sample2, 0, 0, 0.seconds, 0.seconds),
      Sample(sample2, 0, 0, 1.seconds, 0.seconds)
    )
  }

  test("should work for synchronous observers") { implicit s =>
    val randomCount= Random.nextInt(300) + 100

    for (sourceCount <- List(0,1,2,3,4,5,randomCount)) {
      var received = 0
      var total = 0L

      val obs = Observable.range(0, sourceCount).mapParallelUnordered(parallelism = 4)(x => Task.now(x))
      obs.unsafeSubscribeFn(new Observer[Long] {
        private[this] var sum = 0L

        def onNext(elem: Long) = {
          received += 1
          sum += elem
          Continue
        }

        def onError(ex: Throwable): Unit =
          throw new IllegalStateException()
        def onComplete(): Unit =
          total = sum
      })

      s.tick()
      assertEquals(received, count(sourceCount))
      assertEquals(total, sum(sourceCount))
    }
  }

  test("should work for asynchronous observers") { implicit s =>
    val randomCount= Random.nextInt(300) + 100

    for (sourceCount <- List(0,1,2,3,4,5,randomCount)) {
      var received = 0
      var total = 0L

      val obs = Observable.range(0, sourceCount).mapParallelUnordered(parallelism = 4)(x => Task(x))
      obs.unsafeSubscribeFn(new Observer[Long] {
        private[this] var sum = 0L

        def onNext(elem: Long) = {
          received += 1
          sum += elem
          Continue
        }

        def onError(ex: Throwable): Unit =
          throw new IllegalStateException()
        def onComplete(): Unit =
          total = sum
      })

      s.tick()
      assertEquals(received, count(sourceCount))
      assertEquals(total, sum(sourceCount))
    }
  }

  test("mapParallelUnordered equivalence with map") { implicit s =>
    check2 { (list: List[Int], isAsync: Boolean) =>
      val received = Observable.fromIterable(list)
        .mapParallelUnordered(parallelism=4)(x => if (isAsync) Task(x + 10) else Task.eval(x + 10))
        .toListL
        .map(_.sorted)

      val expected = Observable.fromIterable(list).map(_ + 10).toListL.map(_.sorted)
      received <-> expected
    }
  }

  test("mapParallelUnordered(parallelism=1) equivalence with mapTask") { implicit s =>
    check2 { (list: List[Int], isAsync: Boolean) =>
      val received = Observable.fromIterable(list)
        .mapParallelUnordered(parallelism=1)(x => if (isAsync) Task(x + 10) else Task.eval(x + 10))
        .toListL

      val expected = Observable.fromIterable(list)
        .mapTask(x => if (isAsync) Task(x + 10) else Task.eval(x + 10))
        .toListL

      received <-> expected
    }
  }

  test("should interrupt the streaming on error, test #1") { implicit s =>
    val dummy = DummyException("dummy")
    var isComplete = false
    var wasThrown: Throwable = null
    var received = 0L

    val task1 = Task(1L)
    val task2 = Task.raiseError[Long](dummy)
    val task3 = Task.never[Long]
    val tasks = List.fill(8)(task1) ::: List(task2) ::: List.fill(10)(task3)

    Observable.fromIterable(tasks).mapParallelUnordered(parallelism=4)(x => x)
      .unsafeSubscribeFn(new Observer[Long] {
        def onNext(elem: Long) = {
          assert(!isComplete, "!isComplete")
          received += elem
          Continue
        }

        def onError(ex: Throwable) = {
          assert(!isComplete, "!isComplete")
          isComplete = true
          wasThrown = ex
        }

        def onComplete() = {
          isComplete = true
          throw new IllegalStateException("onComplete")
        }
      })

    s.tick()
    assertEquals(wasThrown, dummy)
  }

  test("should interrupt the streaming on error, test #2") { implicit s =>
    val dummy = DummyException("dummy")
    var isComplete = false
    var wasThrown: Throwable = null
    var received = 0L

    val task1 = Task(1L)
    val tasks = List.fill(8)(task1)

    Observable.fromIterable(tasks).endWithError(dummy).mapParallelUnordered(parallelism=4)(x => x)
      .unsafeSubscribeFn(new Observer[Long] {
        def onNext(elem: Long) = {
          assert(!isComplete, "!isComplete")
          received += elem
          Continue
        }

        def onError(ex: Throwable) = {
          assert(!isComplete, "!isComplete")
          isComplete = true
          wasThrown = ex
        }

        def onComplete() = {
          isComplete = true
          throw new IllegalStateException("onComplete")
        }
      })

    s.tick()
    assertEquals(wasThrown, dummy)
  }

  test("should protect against user error") { implicit s =>
    val dummy = DummyException("dummy")
    var isComplete = false
    var wasThrown: Throwable = null
    var received = 0L

    Observable.range(0, 100)
      .mapParallelUnordered(parallelism=4)(x => if (x >= 50) throw dummy else Task(x))
      .unsafeSubscribeFn(new Observer[Long] {
        def onNext(elem: Long) = {
          assert(!isComplete, "!isComplete")
          received += elem
          Continue
        }

        def onError(ex: Throwable) = {
          assert(!isComplete, "!isComplete")
          isComplete = true
          wasThrown = ex
        }

        def onComplete() = {
          isComplete = true
          throw new IllegalStateException("onComplete")
        }
      })

    s.tick()
    assertEquals(wasThrown, dummy)
  }

  test("should back-pressure on semaphore") { implicit s =>
    var initiated = 0
    var received = 0
    var isComplete = false
    val p = Promise[Int]()

    val tasks = List.fill(8)(Task.fromFuture(p.future))
    Observable(tasks:_*).doOnNext(_ => initiated += 1)
      .mapParallelUnordered(parallelism=4)(x => x)
      .unsafeSubscribeFn(new Observer[Int] {
        def onNext(elem: Int) = {
          received += 1
          Continue
        }

        def onError(ex: Throwable): Unit =
          throw ex
        def onComplete(): Unit =
          isComplete = true
      })

    s.tick()
    assertEquals(initiated, 5)
    assertEquals(received, 0)
    assert(!isComplete, "!isComplete")

    p.success(1); s.tick()
    assertEquals(initiated, 8)
    assertEquals(received, 8)
    assert(isComplete, "isComplete")
  }

  test("should back-pressure on buffer") { implicit s =>
    var initiated = 0
    var received = 0
    var isComplete = false
    val p = Promise[Continue.type]()
    val totalCount = Platform.recommendedBatchSize * 4

    Observable.range(0, totalCount)
      .doOnNext(_ => initiated += 1)
      .mapParallelUnordered(parallelism=4)(x => Task(x))
      .unsafeSubscribeFn(new Observer[Long] {
        def onNext(elem: Long) = {
          received += 1
          p.future
        }

        def onError(ex: Throwable): Unit =
          throw ex
        def onComplete(): Unit =
          isComplete = true
      })

    s.tick()
    assert(initiated >= Platform.recommendedBatchSize, "initiated >= Platform.recommendedBatchSize")
    assert(initiated < totalCount, "initiated < totalCount")
    assertEquals(received, 1)
    assert(!isComplete, "!isComplete")

    p.success(Continue); s.tick()
    assertEquals(initiated, totalCount)
    assertEquals(received, totalCount)
    assert(isComplete, "isComplete")
  }

  test("should respect custom overflow strategy") { implicit s =>
    var initiated = 0
    var received = 0
    var isComplete = false
    val p = Promise[Continue.type]()
    val totalCount = Platform.recommendedBatchSize * 4

    Observable.range(0, totalCount)
      .doOnNext(_ => initiated += 1)
      .mapParallelUnordered(parallelism=4)(x => Task(x))(OverflowStrategy.DropNew(Platform.recommendedBatchSize))
      .unsafeSubscribeFn(new Observer[Long] {
        def onNext(elem: Long) = {
          received += 1
          p.future
        }

        def onError(ex: Throwable): Unit =
          throw ex
        def onComplete(): Unit =
          isComplete = true
      })

    s.tick()
    assertEquals(initiated, totalCount)
    assertEquals(received, 1)
    assert(!isComplete, "!isComplete")

    p.success(Continue); s.tick()
    assertEquals(initiated, totalCount)
    assertEquals(received, Platform.recommendedBatchSize + 2) // The rest were dropped from the buffer
    assert(isComplete, "isComplete")
  }

  test("should be cancelable after the main stream has ended") { implicit s =>
    val f = Observable.now(1)
      .mapParallelUnordered(parallelism = 4)(x => Task(x+1).delayExecution(1.second))
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
    val source = Observable.now(1L).mapParallelUnordered(parallelism = 4)(_ => throw dummy)

    val f = source.runAsyncGetLast
    s.tick()

    assertEquals(f.value, Some(Failure(dummy)))
    assertEquals(s.state.lastReportedError, null)
  }

  test("exceptions can be triggered synchronously through raiseError") { implicit s =>
    val dummy = DummyException("dummy")
    val source = Observable.now(1).mapParallelUnordered(parallelism = 4)(_ => Task.raiseError(dummy))

    val f = source.runAsyncGetLast
    s.tick()

    assertEquals(f.value, Some(Failure(dummy)))
    assertEquals(s.state.lastReportedError, null)
  }
}
