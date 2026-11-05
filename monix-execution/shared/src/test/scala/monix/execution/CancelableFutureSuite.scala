/*
 * Copyright (c) 2014-2022 Monix Contributors.
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

package monix.execution

import java.util.concurrent.CancellationException
import minitest.TestSuite
import monix.execution.cancelables.{ BooleanCancelable, ChainedCancelable }
import monix.execution.exceptions.{ CallbackCalledMultipleTimesException, DummyException }
import monix.execution.schedulers.{ TestScheduler, TrampolineExecutionContext }

import scala.annotation.nowarn
import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future, Promise }
import scala.util.{ Failure, Success, Try }

object CancelableFutureSuite extends TestSuite[TestScheduler] {
  def setup() = TestScheduler()
  def tearDown(env: TestScheduler): Unit =
    assert(env.state.tasks.isEmpty, "should not have tasks left to execute")

  private def assertCancellation[A](future: CancelableFuture[A]): Unit =
    assert(future.value.exists { case Failure(_: CancellationException) => true; case _ => false })

  @nowarn("cat=deprecation")
  private def legacyAsync[A](register: (Try[A] => Unit) => Cancelable)(implicit
    ec: ExecutionContext
  ): CancelableFuture[A] =
    CancelableFuture.async(register)

  test("CancelableFuture.fromTry(success)") { _ =>
    val f = CancelableFuture.fromTry(Success(1))
    assertEquals(f.value, Some(Success(1)))
  }

  test("CancelableFuture.fromTry(failure)") { _ =>
    val ex = new RuntimeException("dummy")
    val f = CancelableFuture.fromTry(Failure(ex))
    assertEquals(f.value, Some(Failure(ex)))
  }

  test("future adapters preserve existing CancelableFuture identity") { _ =>
    val source = CancelableFuture.never[Int]
    assert(CancelableFuture.fromFuture(source) eq source)
    assert(CancelableFuture.fromForgettableFuture(source) eq source)
  }

  test("fromFuture waits for source success after cancellation") { _ =>
    val source = Promise[Int]()
    val future = CancelableFuture.fromFuture(source.future)

    future.cancel()
    assertEquals(future.value, None)
    source.success(42)
    assertCancellation(future)
  }

  test("fromFuture reports cancellation instead of source failure") { _ =>
    val source = Promise[Int]()
    val future = CancelableFuture.fromFuture(source.future)

    future.cancel()
    assertEquals(future.value, None)
    source.failure(DummyException("source failure"))
    assertCancellation(future)
  }

  test("fromFuture preserves a result completed before cancellation") { _ =>
    val future = CancelableFuture.fromFuture(Future.successful(42))

    assertEquals(future.value, Some(Success(42)))
    future.cancel()
    assertEquals(future.value, Some(Success(42)))

    val forgettable = CancelableFuture.fromForgettableFuture(Future.successful(99))
    assertEquals(forgettable.value, Some(Success(99)))
    forgettable.cancel()
    assertEquals(forgettable.value, Some(Success(99)))
  }

  test("future converters preserve completed outcomes inside the immediate trampoline") { _ =>
    val error = DummyException("completed failure")
    TrampolineExecutionContext.immediate.execute(new Runnable {
      def run(): Unit = {
        val successfulSource = Future.successful(42)
        val failedSource = Future.failed[Int](error)

        val fromFutureSuccess = CancelableFuture.fromFuture(successfulSource)
        fromFutureSuccess.cancel()
        assertEquals(fromFutureSuccess.value, Some(Success(42)))

        val fromFutureFailure = CancelableFuture.fromFuture(failedSource)
        fromFutureFailure.cancel()
        assertEquals(fromFutureFailure.value, Some(Failure(error)))

        val forgettableSuccess = CancelableFuture.fromForgettableFuture(successfulSource)
        forgettableSuccess.cancel()
        assertEquals(forgettableSuccess.value, Some(Success(42)))

        val forgettableFailure = CancelableFuture.fromForgettableFuture(failedSource)
        forgettableFailure.cancel()
        assertEquals(forgettableFailure.value, Some(Failure(error)))
      }
    })
  }

  test("fromForgettableFuture reports cancellation immediately and forgets source") { _ =>
    val source = Promise[Int]()
    val future = CancelableFuture.fromForgettableFuture(source.future)

    future.cancel()
    assertCancellation(future)
    assert(source.trySuccess(42), "source should remain independently completable")
    assertCancellation(future)
  }

  test("CancelableFuture.successful is already completed") { _ =>
    val f = CancelableFuture.successful(1)
    assertEquals(f.isCompleted, true)
    assertEquals(f.value, Some(Success(1)))
    f.cancel()
    val f2 = f.failed.value
    assert(f2.isDefined && f2.get.isFailure, "f.failed should be completed as well")
  }

  test("cancellation works") { implicit s =>
    val p = Promise[Unit]()
    val task = s.scheduleOnce(10.seconds) { p.success(()); () }
    val f = CancelableFuture(p.future, task)

    s.tick()
    assertEquals(f.value, None)

    assert(s.state.tasks.nonEmpty, "tasks.nonEmpty")
    f.cancel()
    assert(s.state.tasks.isEmpty, "tasks.isEmpty")
    assertEquals(f.value, None)
  }

  test("now.onComplete") { implicit s =>
    var result = Option.empty[Try[Int]]
    CancelableFuture.pure(1).onComplete(r => result = Some(r))

    assertEquals(result, None); s.tick()
    assertEquals(result, Some(Success(1)))
  }

  test("now.failed") { _ =>
    val dummy = new RuntimeException("dummy")
    val f = CancelableFuture.failed(dummy).failed
    assertEquals(f.value, Some(Success(dummy)))
  }

  test("async.failed") { implicit s =>
    val dummy = new RuntimeException("dummy")
    val f = CancelableFuture(Future(throw dummy), Cancelable.empty).failed
    assertEquals(f.value, None)
    s.tick()
    assertEquals(f.value, Some(Success(dummy)))
  }

  test("async is chain-able, test 1") { implicit s =>
    val b = BooleanCancelable()
    val fa = CancelableFuture.fromAsync[Int] { _ =>
      val ch = ChainedCancelable()
      s.execute(() => { ch := b; () })
      ch
    }

    s.tick()
    assertEquals(fa.value, None)
    assert(!b.isCanceled, "!b.isCanceled")

    fa.cancel()
    assert(b.isCanceled, "b.isCanceled")
  }

  test("async is chain-able, test 2") { implicit s =>
    val b = BooleanCancelable()
    val fa = CancelableFuture.fromAsync[Int] { _ =>
      val ch = ChainedCancelable()
      s.execute(() => { ch := b; () })
      ch
    }

    fa.cancel()
    assert(!b.isCanceled, "!b.isCanceled")

    s.tick()
    assert(b.isCanceled, "b.isCanceled")
  }

  test("async is chain-able, test 2") { implicit s =>
    val b = BooleanCancelable()
    val fa = CancelableFuture.fromAsync[Int] { _ =>
      ChainedCancelable(b)
    }

    fa.cancel(); s.tick()
    assert(b.isCanceled, "b.isCanceled")
  }

  test("now.map.failed") { implicit s =>
    val dummy = new RuntimeException("dummy")
    val f = CancelableFuture.failed[Int](dummy).map(_ + 1).failed
    assertEquals(f.value, None)
    s.tick()
    assertEquals(f.value, Some(Success(dummy)))
  }

  test("now.transform") { implicit s =>
    val f = CancelableFuture.successful(1).transform(_ + 1, ex => ex)
    assertEquals(f.value, None)
    s.tick()
    assertEquals(f.value, Some(Success(2)))
  }

  test("async.transform") { implicit s =>
    val f = CancelableFuture(Future(1), Cancelable.empty).transform(_ + 1, ex => ex)
    assertEquals(f.value, None)
    s.tick()
    assertEquals(f.value, Some(Success(2)))
  }

  test("now.map.transform") { implicit s =>
    val f = CancelableFuture.successful(1).map(_ + 1).transform(_ + 1, ex => ex)
    assertEquals(f.value, None)
    s.tick()
    assertEquals(f.value, Some(Success(3)))
  }

  test("now.map") { implicit s =>
    val f = CancelableFuture.successful(1).map(_ + 1)
    assertEquals(f.value, None)
    s.tick()
    assertEquals(f.value, Some(Success(2)))
  }

  test("async.map") { implicit s =>
    val f = CancelableFuture(Future(1), Cancelable.empty).map(_ + 1)
    assertEquals(f.value, None)
    s.tick()
    assertEquals(f.value, Some(Success(2)))
  }

  test("now.map.map") { implicit s =>
    val f = CancelableFuture.successful(1).map(_ + 1).map(_ + 1)
    assertEquals(f.value, None)
    s.tick()
    assertEquals(f.value, Some(Success(3)))
  }

  test("now.flatMap") { implicit s =>
    val f = CancelableFuture
      .successful(1)
      .flatMap(x => CancelableFuture.successful(x + 1))

    assertEquals(f.value, None)
    s.tick()
    assertEquals(f.value, Some(Success(2)))
  }

  test("async.flatMap") { implicit s =>
    val f = CancelableFuture(Future(1), Cancelable.empty)
      .flatMap(x => CancelableFuture.successful(x + 1))

    assertEquals(f.value, None)
    s.tick()
    assertEquals(f.value, Some(Success(2)))
  }

  test("now.map.flatMap") { implicit s =>
    val f = CancelableFuture
      .successful(1)
      .map(_ + 1)
      .flatMap(x => CancelableFuture.successful(x + 1))

    assertEquals(f.value, None)
    s.tick()
    assertEquals(f.value, Some(Success(3)))
  }

  test("now.filter") { implicit s =>
    val f = CancelableFuture.successful(1).filter(_ == 1)
    assertEquals(f.value, None)
    s.tick()
    assertEquals(f.value, Some(Success(1)))
  }

  test("async.filter") { implicit s =>
    val f = CancelableFuture(Future(1), Cancelable.empty).filter(_ == 1)
    assertEquals(f.value, None)
    s.tick()
    assertEquals(f.value, Some(Success(1)))
  }

  test("now.map.filter") { implicit s =>
    val f = CancelableFuture.successful(1).map(_ + 1).filter(_ == 2)
    assertEquals(f.value, None)
    s.tick()
    assertEquals(f.value, Some(Success(2)))
  }

  test("now.collect") { implicit s =>
    val f = CancelableFuture.successful(1).collect { case x => x + 1 }
    assertEquals(f.value, None)
    s.tick()
    assertEquals(f.value, Some(Success(2)))
  }

  test("async.collect") { implicit s =>
    val f = CancelableFuture(Future(1), Cancelable.empty).collect { case x => x + 1 }
    assertEquals(f.value, None)
    s.tick()
    assertEquals(f.value, Some(Success(2)))
  }

  test("now.map.collect") { implicit s =>
    val f = CancelableFuture.successful(1).map(_ + 1).collect { case x => x + 1 }
    assertEquals(f.value, None)
    s.tick()
    assertEquals(f.value, Some(Success(3)))
  }

  test("now.failed") { implicit s =>
    val dummy = new RuntimeException("dummy")
    val f = CancelableFuture.failed(dummy).failed
    s.tick(); assertEquals(f.value, Some(Success(dummy)))
  }

  test("async.failed") { implicit s =>
    val dummy = new RuntimeException("dummy")
    val f = CancelableFuture(Future(throw dummy), Cancelable.empty).failed

    assertEquals(f.value, None)
    s.tick()
    assertEquals(f.value, Some(Success(dummy)))
  }

  test("now.recover") { implicit s =>
    val ex = new RuntimeException("dummy")
    val f = CancelableFuture.failed(ex).recover { case _ => 1 }
    assertEquals(f.value, None)
    s.tick()
    assertEquals(f.value, Some(Success(1)))
  }

  test("async.recover") { implicit s =>
    val ex = new RuntimeException("dummy")
    val f = CancelableFuture(Future(throw ex), Cancelable.empty).recover { case _ => 1 }
    assertEquals(f.value, None)
    s.tick()
    assertEquals(f.value, Some(Success(1)))
  }

  test("now.map.recover") { implicit s =>
    val ex = new RuntimeException("dummy")
    val f = CancelableFuture.failed[Int](ex).map(_ + 1).recover { case _ => 1 }
    assertEquals(f.value, None)
    s.tick()
    assertEquals(f.value, Some(Success(1)))
  }

  test("now.recoverWith") { implicit s =>
    val ex = new RuntimeException("dummy")
    val f = CancelableFuture.failed(ex).recoverWith { case _ => CancelableFuture.successful(1) }

    assertEquals(f.value, None)
    s.tick()
    assertEquals(f.value, Some(Success(1)))
  }

  test("async.recoverWith") { implicit s =>
    val ex = new RuntimeException("dummy")
    val f = CancelableFuture(Future(throw ex), Cancelable.empty).recoverWith {
      case _ => CancelableFuture.successful(1)
    }

    assertEquals(f.value, None)
    s.tick()
    assertEquals(f.value, Some(Success(1)))
  }

  test("now.map.recoverWith") { implicit s =>
    val ex = new RuntimeException("dummy")
    val f = CancelableFuture.failed[Int](ex).map(_ + 1).recoverWith { case _ => CancelableFuture.successful(1) }

    assertEquals(f.value, None)
    s.tick()
    assertEquals(f.value, Some(Success(1)))
  }

  test("now.zip(now)") { implicit s =>
    val f = CancelableFuture
      .successful(1)
      .zip(CancelableFuture.successful(1))
      .map { case (x, y) => x + y }

    assertEquals(f.value, None)
    s.tick()
    assertEquals(f.value, Some(Success(2)))
  }

  test("async.zip(Async)") { implicit s =>
    val f = CancelableFuture(Future(1), Cancelable.empty)
      .zip(CancelableFuture(Future(1), Cancelable.empty))
      .map { case (x, y) => x + y }

    assertEquals(f.value, None)
    s.tick()
    assertEquals(f.value, Some(Success(2)))
  }

  test("now.map.zip(now.map)") { implicit s =>
    val f = CancelableFuture
      .successful(1)
      .map(_ + 1)
      .zip(CancelableFuture.successful(1).map(_ + 1))
      .map { case (x, y) => x + y }

    assertEquals(f.value, None)
    s.tick()
    assertEquals(f.value, Some(Success(4)))
  }

  test("zip cancellation requests both inputs and waits for both") { implicit s =>
    var leftCallback = Option.empty[CompletionCallback[Int]]
    var rightCallback = Option.empty[CompletionCallback[Int]]
    var leftCancelRequests = 0
    var rightCancelRequests = 0

    val left = CancelableFuture.fromAsync[Int] { cb =>
      leftCallback = Some(cb)
      Cancelable(() => leftCancelRequests += 1)
    }
    val right = CancelableFuture.fromAsync[Int] { cb =>
      rightCallback = Some(cb)
      Cancelable(() => rightCancelRequests += 1)
    }
    s.tick()

    val zipped = left.zip(right)
    zipped.cancel()
    assertEquals(leftCancelRequests, 1)
    assertEquals(rightCancelRequests, 1)
    assertEquals(zipped.value, None)

    leftCallback.get.onCancellation()
    s.tick()
    assertEquals(zipped.value, None)

    rightCallback.get.onCancellation()
    s.tick()
    assertCancellation(zipped)
  }

  test("zipWith cancellation requests both inputs and waits for both") { implicit s =>
    var leftCallback = Option.empty[CompletionCallback[Int]]
    var rightCallback = Option.empty[CompletionCallback[Int]]
    var leftCancelRequests = 0
    var rightCancelRequests = 0

    val left = CancelableFuture.fromAsync[Int] { cb =>
      leftCallback = Some(cb)
      Cancelable(() => leftCancelRequests += 1)
    }
    val right = CancelableFuture.fromAsync[Int] { cb =>
      rightCallback = Some(cb)
      Cancelable(() => rightCancelRequests += 1)
    }
    s.tick()

    val zipped = left.zipWith(right)(_ + _)
    zipped.cancel()
    assertEquals(leftCancelRequests, 1)
    assertEquals(rightCancelRequests, 1)

    leftCallback.get.onCancellation()
    s.tick()
    assertEquals(zipped.value, None)
    rightCallback.get.onCancellation()
    s.tick()
    assertCancellation(zipped)
  }

  test("zip waits for a sibling after an independent cancellation") { implicit s =>
    var leftCallback = Option.empty[CompletionCallback[Int]]
    var rightCallback = Option.empty[CompletionCallback[Int]]
    var rightCancelRequests = 0

    val left = CancelableFuture.fromAsync[Int] { cb =>
      leftCallback = Some(cb)
      Cancelable.empty
    }
    val right = CancelableFuture.fromAsync[Int] { cb =>
      rightCallback = Some(cb)
      Cancelable(() => rightCancelRequests += 1)
    }
    s.tick()

    val zipped = left.zip(right)
    leftCallback.get.onCancellation()
    s.tick()
    assertEquals(rightCancelRequests, 1)
    assertEquals(zipped.value, None)

    rightCallback.get.onCancellation()
    s.tick()
    assertCancellation(zipped)
  }

  test("zip preserves first failure and suppresses the sibling failure") { implicit s =>
    var leftCallback = Option.empty[CompletionCallback[Int]]
    var rightCallback = Option.empty[CompletionCallback[Int]]
    var leftCancelRequests = 0
    val primary = DummyException("primary")
    val sibling = DummyException("sibling")

    val left = CancelableFuture.fromAsync[Int] { cb =>
      leftCallback = Some(cb)
      Cancelable(() => leftCancelRequests += 1)
    }
    val right = CancelableFuture.fromAsync[Int] { cb =>
      rightCallback = Some(cb)
      Cancelable.empty
    }
    s.tick()

    val zipped = left.zip(right)
    rightCallback.get.onError(primary)
    s.tick()
    assertEquals(leftCancelRequests, 1)
    assertEquals(zipped.value, None)

    leftCallback.get.onError(sibling)
    s.tick()
    assertEquals(zipped.value, Some(Failure(primary)))
    assertEquals(primary.getSuppressed.toList, List(sibling))
  }

  test("zip does not self-suppress a repeated failure") { implicit s =>
    var leftCallback = Option.empty[CompletionCallback[Int]]
    var rightCallback = Option.empty[CompletionCallback[Int]]
    val shared = DummyException("shared")

    val left = CancelableFuture.fromAsync[Int] { cb =>
      leftCallback = Some(cb)
      Cancelable.empty
    }
    val right = CancelableFuture.fromAsync[Int] { cb =>
      rightCallback = Some(cb)
      Cancelable.empty
    }
    s.tick()

    val zipped = left.zip(right)
    leftCallback.get.onError(shared)
    s.tick()
    rightCallback.get.onError(shared)
    s.tick()

    assertEquals(zipped.value, Some(Failure(shared)))
    assertEquals(shared.getSuppressed.toList, Nil)
  }

  test("zip with an ordinary Future waits for its source after cancellation") { implicit s =>
    var leftCallback = Option.empty[CompletionCallback[Int]]
    var leftCancelRequests = 0
    val left = CancelableFuture.fromAsync[Int] { cb =>
      leftCallback = Some(cb)
      Cancelable(() => leftCancelRequests += 1)
    }
    val source = Promise[Int]()
    s.tick()

    val zipped = left.zip(source.future)
    zipped.cancel()
    assertEquals(leftCancelRequests, 1)
    assertEquals(zipped.value, None)

    leftCallback.get.onCancellation()
    s.tick()
    assertEquals(zipped.value, None)

    source.success(42)
    s.tick()
    assertCancellation(zipped)
  }

  test("now.fallbackTo") { _ =>
    val ex = new RuntimeException("dummy")
    val f = CancelableFuture
      .failed(ex)
      .fallbackTo(CancelableFuture.successful(1))

    assertEquals(f.value, Some(Success(1)))
  }

  test("async.fallbackTo") { implicit s =>
    val ex = new RuntimeException("dummy")
    val f = CancelableFuture(Future(throw ex), Cancelable.empty)
      .fallbackTo(CancelableFuture.successful(1))

    assertEquals(f.value, None)
    s.tick()
    assertEquals(f.value, Some(Success(1)))
  }

  test("now.map.fallbackTo") { implicit s =>
    val ex = new RuntimeException("dummy")
    val f = CancelableFuture
      .failed[Int](ex)
      .map(_ + 1)
      .fallbackTo(CancelableFuture.successful(1))

    assertEquals(f.value, None)
    s.tick()
    assertEquals(f.value, Some(Success(1)))
  }

  test("now.mapTo") { _ =>
    val f = CancelableFuture.successful(1).mapTo[Int]
    assertEquals(f.value, Some(Success(1)))
  }

  test("never.mapTo remains independently cancellable") { implicit s =>
    val source = CancelableFuture.never[Int]
    val f = source.mapTo[Int]
    assert(!(f eq source))
    assertEquals(f.value, None)

    f.cancel()
    s.tick()
    assertCancellation(f)
    assertCancellation(source)
  }

  test("async.mapTo") { implicit s =>
    val f = CancelableFuture(Future(1), Cancelable.empty).mapTo[Int]
    assertEquals(f.value, None)
    s.tick()
    assertEquals(f.value, Some(Success(1)))
  }

  test("now.map.mapTo") { implicit s =>
    val f = CancelableFuture.successful(1).map(_ + 1).mapTo[Int]
    assertEquals(f.value, None)
    s.tick()
    assertEquals(f.value, Some(Success(2)))
  }

  test("now.andThen") { implicit s =>
    val f = CancelableFuture.successful(1).andThen { case Success(x) => x + 1 }
    s.tick()
    assertEquals(f.value, Some(Success(1)))
  }

  test("async.andThen") { implicit s =>
    val f = CancelableFuture(Future(1), Cancelable.empty).andThen { case Success(x) => x + 1 }
    s.tick()
    assertEquals(f.value, Some(Success(1)))
  }

  test("now.map.andThen") { implicit s =>
    val f = CancelableFuture.successful(1).map(_ + 1).andThen { case Success(x) => x + 1 }
    s.tick()
    assertEquals(f.value, Some(Success(2)))
  }

  test("now.transform") { implicit s =>
    val f = CancelableFuture.successful(1).transform {
      case Success(value) => Success(value + 1)
      case error @ Failure(_) => error
    }

    s.tick()
    assertEquals(f.value, Some(Success(2)))
  }

  test("now.transformWith") { implicit s =>
    val f = CancelableFuture.successful(1).transformWith {
      case Success(value) => Future.successful(value + 1)
      case Failure(ex) => Future.failed(ex)
    }

    s.tick()
    assertEquals(f.value, Some(Success(2)))
  }

  test("error.transform") { implicit s =>
    val ex = DummyException("dummy")
    val f = CancelableFuture.failed[Int](ex).transform {
      case Failure(`ex`) => Success(10)
      case other @ Failure(_) => other
      case Success(value) => Success(value + 1)
    }

    s.tick()
    assertEquals(f.value, Some(Success(10)))
  }

  test("error.transformWith") { implicit s =>
    val ex = DummyException("dummy")
    val f = CancelableFuture.failed[Int](ex).transformWith {
      case Failure(`ex`) => Future.successful(10)
      case Failure(other) => Future.failed(other)
      case Success(value) => Future.successful(value + 1)
    }

    s.tick()
    assertEquals(f.value, Some(Success(10)))
  }

  test("async.transform") { implicit s =>
    val f = CancelableFuture(Future(1), Cancelable.empty).transform {
      case Success(value) => Success(value + 1)
      case error @ Failure(_) => error
    }

    s.tick()
    assertEquals(f.value, Some(Success(2)))
  }

  test("async.transformWith") { implicit s =>
    val f = CancelableFuture(Future(1), Cancelable.empty).transformWith {
      case Success(value) => Future.successful(value + 1)
      case Failure(ex) => Future.failed(ex)
    }

    s.tick()
    assertEquals(f.value, Some(Success(2)))
  }

  test("async.isCompleted") { implicit s =>
    val f = CancelableFuture(Future(1), Cancelable.empty)
    assert(!f.isCompleted, "!f.isCompleted")
    s.tick()
    assert(f.isCompleted, "f.isCompleted")
  }

  test("never") { implicit s =>
    val first = CancelableFuture.never[Int]
    val second = CancelableFuture.never[Int]
    var observed = Option.empty[Try[Int]]
    assert(!(first eq second))
    assertEquals(first.value, None)
    assertEquals(second.value, None)
    first.onComplete(result => observed = Some(result))
    s.tick()
    assertEquals(observed, None)

    first.cancel()
    assertCancellation(first)
    assertEquals(second.value, None)
    s.tick()
    assertEquals(observed, first.value)
    second.cancel()
    assertCancellation(second)
  }

  test("flatMap cancels first") { implicit s =>
    val c = BooleanCancelable()
    val f = CancelableFuture(CancelableFuture.never[Unit], c)
      .flatMap(_ => Future.successful(1))

    assert(!f.isCompleted, "f.isCompleted")
    s.tick()
    f.cancel()
    assert(c.isCanceled, "c.isCanceled")
  }

  test("flatMap cancels second") { implicit s =>
    val c = BooleanCancelable()
    val first = CancelableFuture.successful(())

    val f = first.flatMap { _ =>
      CancelableFuture(
        CancelableFuture.never[Unit],
        Cancelable { () =>
          c.cancel()
        }
      )
    }

    assert(first.isCompleted, "!first.isCompleted")
    s.tick()
    f.cancel()
    s.tick()
    assert(c.isCanceled, "c.isCanceled")
  }

  test("flatMap cancels third") { implicit s =>
    val c = BooleanCancelable()
    val first = CancelableFuture(Future.successful(1), Cancelable.empty)

    val f = first.flatMap { _ =>
      val second = CancelableFuture(Future.successful(2), Cancelable.empty)
      second.flatMap { _ =>
        CancelableFuture(CancelableFuture.never[Unit], c)
      }
    }

    assert(first.isCompleted, "!first.isCompleted")
    s.tick()
    f.cancel()
    s.tick()
    assert(c.isCanceled, "c.isCanceled")
  }

  test("flatMap carries pending cancellation across successful handoff") { implicit s =>
    var sourceCallback = Option.empty[CompletionCallback[Int]]
    var nextCallback = Option.empty[CompletionCallback[Int]]
    var sourceCancelRequests = 0
    var nextCancelRequests = 0

    val source = CancelableFuture.fromAsync[Int] { cb =>
      sourceCallback = Some(cb)
      Cancelable(() => sourceCancelRequests += 1)
    }
    val flatMapped = source.flatMap { _ =>
      CancelableFuture.fromAsync[Int] { cb =>
        nextCallback = Some(cb)
        Cancelable(() => nextCancelRequests += 1)
      }
    }
    s.tick()

    flatMapped.cancel()
    assertEquals(sourceCancelRequests, 1)
    assertEquals(nextCancelRequests, 0)
    assertEquals(flatMapped.value, None)

    sourceCallback.get.onSuccess(1)
    s.tick()
    assert(nextCallback.nonEmpty)
    assertEquals(nextCancelRequests, 1)
    assertEquals(flatMapped.value, None)

    nextCallback.get.onCancellation()
    s.tick()
    assertCancellation(flatMapped)
  }

  test("flatMap adapts a plain Future stage and waits after cancellation") { implicit s =>
    val next = Promise[Int]()
    val flatMapped = CancelableFuture.successful(1).flatMap(_ => next.future)
    s.tick()
    assertEquals(flatMapped.value, None)

    flatMapped.cancel()
    assertEquals(flatMapped.value, None)
    next.success(42)
    s.tick()
    assertCancellation(flatMapped)
  }

  test("recovery starts a fresh cancellation stage") { implicit s =>
    var sourceCallback = Option.empty[CompletionCallback[Int]]
    var replacementCallback = Option.empty[CompletionCallback[Int]]
    var replacementCancelRequests = 0

    val source = CancelableFuture.fromAsync[Int] { cb =>
      sourceCallback = Some(cb)
      Cancelable(() => cb.onCancellation())
    }
    val recovered = source.recoverWith {
      case _: CancellationException =>
        CancelableFuture.fromAsync[Int] { cb =>
          replacementCallback = Some(cb)
          Cancelable(() => replacementCancelRequests += 1)
        }
    }
    s.tick()

    recovered.cancel()
    s.tick()
    assert(sourceCallback.nonEmpty)
    assert(replacementCallback.nonEmpty)
    assertEquals(replacementCancelRequests, 0)
    assertEquals(recovered.value, None)

    recovered.cancel()
    assertEquals(replacementCancelRequests, 1)
    replacementCallback.get.onCancellation()
    s.tick()
    assertCancellation(recovered)
  }

  test("cancelling a deep pending map and flatMap chain does not overflow the stack") { implicit s =>
    var future: CancelableFuture[Int] = CancelableFuture.never[Int]
    var index = 0
    while (index < 5000) {
      future = future.map((value: Int) => value).flatMap(value => CancelableFuture.successful(value))
      index += 1
    }

    future.cancel()
    s.tick()
    assertCancellation(future)
  }

  test("long flatMap chain cancels only the active stage and waits for it") { implicit s =>
    var completedStageCancelRequests = 0
    var activeCallback = Option.empty[CompletionCallback[Int]]
    var activeCancelRequests = 0

    val chain = (0 until 512).foldLeft(CancelableFuture.successful(0)) { (current, index) =>
      current.flatMap { _ =>
        CancelableFuture.fromAsync[Int] { cb =>
          cb.onSuccess(index + 1)
          Cancelable(() => completedStageCancelRequests += 1)
        }
      }
    }
    val result = chain.flatMap { _ =>
      CancelableFuture.fromAsync[Int] { cb =>
        activeCallback = Some(cb)
        Cancelable(() => activeCancelRequests += 1)
      }
    }
    s.tick()
    assert(activeCallback.nonEmpty)

    result.cancel()
    assertEquals(completedStageCancelRequests, 0)
    assertEquals(activeCancelRequests, 1)
    assertEquals(result.value, None)

    activeCallback.get.onCancellation()
    s.tick()
    assertCancellation(result)
  }

  test("async works for success") { implicit s =>
    val fa = legacyAsync[Int] { cb =>
      s.execute(() => cb(Success(1)))
      Cancelable.empty
    }

    s.tick()
    assertEquals(fa.value, Some(Success(1)))
  }

  test("async works for failure") { implicit s =>
    val dummy = DummyException("dummy")
    val fa = legacyAsync[Int] { cb =>
      s.execute(() => cb(Failure(dummy)))
      Cancelable.empty
    }

    s.tick()
    assertEquals(fa.value, Some(Failure(dummy)))
  }

  test("async is cancelable") { implicit s =>
    val fa = legacyAsync[Int] { cb =>
      s.scheduleOnce(1.second)(cb(Success(1)))
    }

    s.tick()
    assertEquals(fa.value, None)
    assert(s.state.tasks.nonEmpty, "tasks.nonEmpty")

    fa.cancel()
    s.tick()
    assertEquals(fa.value, None)
    assert(s.state.tasks.isEmpty, "tasks.isEmpty")

    s.tick(1.second)
    assertEquals(fa.value, None)
  }

  test("fromAsync reports cancellation after late token assignment") { _ =>
    var callback = Option.empty[CompletionCallback[Int]]
    var cancelRequests = 0
    val tasks = scala.collection.mutable.Queue.empty[Runnable]
    val queued = new ExecutionContext {
      def execute(runnable: Runnable): Unit = tasks.enqueue(runnable)
      def reportFailure(error: Throwable): Unit = throw error
    }
    val future = CancelableFuture.fromAsync[Int] { cb =>
      callback = Some(cb)
      Cancelable(() => cancelRequests += 1)
    }(queued)

    future.cancel()
    assertEquals(cancelRequests, 0)
    assertEquals(future.value, None)

    tasks.dequeue().run()
    assertEquals(cancelRequests, 1)
    assert(callback.nonEmpty)
    assertEquals(future.value, None)
    callback.get.onCancellation()
    assertCancellation(future)
  }

  test("fromAsync allows success to win after a cancellation request") { implicit s =>
    var callback = Option.empty[CompletionCallback[Int]]
    val future = CancelableFuture.fromAsync[Int] { cb =>
      callback = Some(cb)
      Cancelable.empty
    }

    s.tick()
    future.cancel()
    assertEquals(future.value, None)
    callback.get.onSuccess(42)
    assertEquals(future.value, Some(Success(42)))
  }

  test("fromAsync diagnoses duplicate terminal signals") { implicit s =>
    val future = CancelableFuture.fromAsync[Int] { cb =>
      cb.onSuccess(42)
      cb.onCancellation()
      Cancelable.empty
    }

    s.tick()
    assertEquals(future.value, Some(Success(42)))
    assert(s.state.lastReportedError.isInstanceOf[CallbackCalledMultipleTimesException])
  }

  test("async reports failures in user code") { implicit s =>
    val dummy = DummyException("dummy")
    val fa = legacyAsync[Int] { _ =>
      throw dummy
    }
    assertEquals(fa.value, Some(Failure(dummy)))
  }

  test("async throws error if protocol is violated") { implicit s =>
    val fa = legacyAsync[Int] { cb =>
      cb(Success(1))
      cb(Success(2))
      Cancelable.empty
    }

    assertEquals(fa.value, Some(Success(1)))
    assert(s.state.lastReportedError != null)
    assert(s.state.lastReportedError.isInstanceOf[IllegalStateException])
  }

  test("transform is safe") { implicit s =>
    val fa1 = CancelableFuture(CancelableFuture.successful(1), Cancelable.empty)
    val fa2 = fa1.transform(_.map(_ + 1))
    val fa3 = fa2.transform(_.map(_ + 1))

    s.tick()
    assertEquals(fa1.value, Some(Success(1)))
    assertEquals(fa2.value, Some(Success(2)))
    assertEquals(fa3.value, Some(Success(3)))
  }

  test("transformWith is safe") { implicit s =>
    val fa1 = CancelableFuture(CancelableFuture.successful(1), Cancelable.empty)
    val fa2 = fa1.transformWith(x => CancelableFuture.fromTry(x.map(_ + 1)))
    val fa3 = fa2.transformWith(x => CancelableFuture.fromTry(x.map(_ + 1)))

    s.tick()
    assertEquals(fa1.value, Some(Success(1)))
    assertEquals(fa2.value, Some(Success(2)))
    assertEquals(fa3.value, Some(Success(3)))
  }

  test("transform protects against user error") { implicit s =>
    val dummy = DummyException("dummy")
    val fa1 = CancelableFuture(CancelableFuture.successful(1), Cancelable.empty)
    val fa2 = fa1.transform(_ => throw dummy)

    s.tick()
    assertEquals(fa2.value, Some(Failure(dummy)))
  }

  test("transformWith protects against user error") { implicit s =>
    val dummy = DummyException("dummy")
    val fa1 = CancelableFuture(CancelableFuture.successful(1), Cancelable.empty)
    val fa2 = fa1.transformWith(_ => throw dummy)

    s.tick()
    assertEquals(fa2.value, Some(Failure(dummy)))
  }

  test("pure.transform protects against user error") { implicit s =>
    val dummy = DummyException("dummy")
    val fa1 = CancelableFuture.successful(1)
    val fa2 = fa1.transform(_ => throw dummy)

    s.tick()
    assertEquals(fa2.value, Some(Failure(dummy)))
  }

  test("pure.transformWith protects against user error") { implicit s =>
    val dummy = DummyException("dummy")
    val fa1 = CancelableFuture.pure(1)
    val fa2 = fa1.transformWith(_ => throw dummy)

    s.tick()
    assertEquals(fa2.value, Some(Failure(dummy)))
  }

  test("raiseError.transform protects against user error") { implicit s =>
    val dummy1 = DummyException("dummy1")
    val dummy2 = DummyException("dummy2")

    val fa1 = CancelableFuture.raiseError(dummy1)
    val fa2 = fa1.transform(_ => throw dummy2)

    s.tick()
    assertEquals(fa2.value, Some(Failure(dummy2)))
  }

  test("pure.transformWith protects against user error") { implicit s =>
    val dummy1 = DummyException("dummy1")
    val dummy2 = DummyException("dummy2")

    val fa1 = CancelableFuture.raiseError(dummy1)
    val fa2 = fa1.transformWith(_ => throw dummy2)

    s.tick()
    assertEquals(fa2.value, Some(Failure(dummy2)))
  }
}
