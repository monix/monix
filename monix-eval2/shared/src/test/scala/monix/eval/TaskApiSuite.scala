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

package monix.eval

import cats.effect.kernel.Async
import minitest.SimpleTestSuite
import monix.execution.{ Cancelable, CancelableFuture, ExecutionModel }
import monix.execution.schedulers.TestScheduler

import scala.concurrent.{ Future, Promise }
import scala.concurrent.duration._
import scala.util.{ Failure, Success, Try }

object TaskApiSuite extends SimpleTestSuite {
  test("builders preserve strict and delayed evaluation") {
    implicit val scheduler: TestScheduler = TestScheduler()
    var evaluations = 0
    var emptyErrorsCreated = 0

    val strict = Task.now { evaluations += 1 }
    val strictResult = strict.unsafeRunToFuture()
    assertEquals(evaluations, 1)

    val delayed = Task.eval { evaluations += 1; evaluations }
    val deferredFuture = Task.deferFuture(Future.successful { evaluations += 1; evaluations })
    assertEquals(evaluations, 1)

    val first = delayed.unsafeRunToFuture()
    scheduler.tick()
    assertEquals(first.value, Some(Success(2)))

    val second = delayed.unsafeRunToFuture()
    scheduler.tick()
    assertEquals(second.value, Some(Success(3)))

    val future = deferredFuture.unsafeRunToFuture()
    scheduler.tick()
    assertEquals(future.value, Some(Success(4)))
    assertEquals(strictResult.value, Some(Success(())))

    val fromTry = Task.fromTry(Try(42)).unsafeRunToFuture()
    val fromEither = Task.fromEither[Throwable, Int](Right(43)).unsafeRunToFuture()
    val fromOption = Task.fromOption(Some(44)) {
      emptyErrorsCreated += 1
      new RuntimeException("empty")
    }.unsafeRunToFuture()
    val unit = Task.unit.unsafeRunToFuture()
    scheduler.tick()
    assertEquals(fromTry.value, Some(Success(42)))
    assertEquals(fromEither.value, Some(Success(43)))
    assertEquals(fromOption.value, Some(Success(44)))
    assertEquals(unit.value, Some(Success(())))
    assertEquals(emptyErrorsCreated, 0)
  }

  test("attempt, materialize, dematerialize, and redeem preserve error channels") {
    implicit val scheduler: TestScheduler = TestScheduler()
    val sourceError = new RuntimeException("source")
    val mappingError = new RuntimeException("mapping")

    val attempted = Task.raiseError[Int](sourceError).attempt.unsafeRunToFuture()
    val materialized = Task.raiseError[Int](sourceError).materialize.unsafeRunToFuture()
    val dematerialized = Task.now[Try[Int]](Failure(sourceError)).dematerialize.unsafeRunToFuture()
    val redeemed = Task.raiseError[Int](sourceError).redeem(_ => 7, identity).unsafeRunToFuture()
    val redeemedWith = Task.raiseError[Int](sourceError).redeemWith(_ => Task.pure(8), Task.pure).unsafeRunToFuture()
    val mappingFailure = Task.pure(1).redeem(_ => 0, _ => throw mappingError).unsafeRunToFuture()

    scheduler.tick()

    assertEquals(attempted.value, Some(Success(Left(sourceError))))
    assert(materialized.value.exists {
      case Success(Failure(error)) => error eq sourceError
      case _ => false
    })
    assertEquals(dematerialized.value, Some(Failure(sourceError)))
    assertEquals(redeemed.value, Some(Success(7)))
    assertEquals(redeemedWith.value, Some(Success(8)))
    assertEquals(mappingFailure.value, Some(Failure(mappingError)))
  }

  test("partial recovery preserves unmatched errors and tapError exposes handler failure") {
    implicit val scheduler: TestScheduler = TestScheduler()
    val sourceError = new RuntimeException("source")
    val handlerError = new RuntimeException("handler")

    val unmatched = Task.raiseError[Int](sourceError)
      .onErrorRecover { case _: IllegalArgumentException => 1 }
      .unsafeRunToFuture()
    val tapped = Task.raiseError[Int](sourceError)
      .tapError(_ => Task.raiseError[Unit](handlerError))
      .unsafeRunToFuture()

    scheduler.tick()

    assertEquals(unmatched.value, Some(Failure(sourceError)))
    assertEquals(tapped.value, Some(Failure(handlerError)))
  }

  test("error restart counts retries and stays stack safe") {
    implicit val scheduler: TestScheduler = TestScheduler()
    var attempts = 0
    val recovered = Task.eval {
      attempts += 1
      if (attempts <= 2) throw new RuntimeException("retry")
      attempts
    }.onErrorRestart(2L).unsafeRunToFuture()

    scheduler.tick()
    assertEquals(recovered.value, Some(Success(3)))
    assertEquals(attempts, 3)

    attempts = 0
    val noRetries = Task.eval {
      attempts += 1
      throw new RuntimeException("stop")
    }.onErrorRestart(-1L).attempt.unsafeRunToFuture()
    scheduler.tick()
    assertEquals(attempts, 1)
    assert(noRetries.value.exists(_.isSuccess))

    attempts = 0
    val manyRetries = Task.eval {
      attempts += 1
      if (attempts <= 10000) throw new RuntimeException("retry")
      attempts
    }.onErrorRestart(10000L).unsafeRunToFuture()
    scheduler.tick()
    assertEquals(manyRetries.value, Some(Success(10001)))
  }

  test("restartUntil repeats until its predicate succeeds") {
    implicit val scheduler: TestScheduler = TestScheduler()
    var attempts = 0
    val result = Task.eval { attempts += 1; attempts }
      .restartUntil(_ >= 3)
      .unsafeRunToFuture()

    scheduler.tick()

    assertEquals(result.value, Some(Success(3)))
    assertEquals(attempts, 3)
  }

  test("delayExecution delays evaluation and delayResult delays only success") {
    implicit val scheduler: TestScheduler = TestScheduler(ExecutionModel.AlwaysAsyncExecution)
    var evaluations = 0
    val executionDelayed = Task.eval { evaluations += 1; evaluations }
      .delayExecution(1.second)
      .unsafeRunToFuture()

    scheduler.tick()
    assertEquals(evaluations, 0)
    assertEquals(executionDelayed.value, None)

    scheduler.tick(1.second)
    assertEquals(evaluations, 1)
    assertEquals(executionDelayed.value, Some(Success(1)))

    val resultDelayed = Task.eval { evaluations += 1; evaluations }
      .delayResult(1.second)
      .unsafeRunToFuture()
    scheduler.tick()
    assertEquals(evaluations, 2)
    assertEquals(resultDelayed.value, None)

    scheduler.tick(1.second)
    assertEquals(resultDelayed.value, Some(Success(2)))

    val error = new RuntimeException("immediate")
    val failed = Task.raiseError[Int](error).delayResult(1.second).unsafeRunToFuture()
    scheduler.tick()
    assertEquals(failed.value, Some(Failure(error)))
  }

  test("timeoutTo cancels the source and waits for its finalizer") {
    implicit val scheduler: TestScheduler = TestScheduler(ExecutionModel.AlwaysAsyncExecution)
    val F = Async[Task]
    var finalized = false
    var fallbackStarted = false
    val timed = Task.never[Int]
      .onCancel(F.sleep(1.second).flatMap(_ => Task.delay { finalized = true }))
      .timeoutTo(1.second, Task.delay { fallbackStarted = true; 42 })
      .unsafeRunToFuture()

    scheduler.tick()
    assert(!finalized)
    assert(!fallbackStarted)

    scheduler.tick(1.second)

    assert(!finalized)
    assert(!fallbackStarted)
    assertEquals(timed.value, None)

    scheduler.tick(1.second)

    assert(finalized)
    assert(fallbackStarted)
    assertEquals(timed.value, Some(Success(42)))
  }

  test("guarantee and bracket release on success, error, and cancellation") {
    implicit val scheduler: TestScheduler = TestScheduler(ExecutionModel.AlwaysAsyncExecution)
    var releases = 0
    val success = Task.pure(1).guarantee(Task.delay { releases += 1 })
    val error = Task.raiseError[Int](new RuntimeException("use"))
      .guarantee(Task.delay { releases += 1 })
    val bracketed = Task.pure("resource").bracket(_ => Task.never[Int])(_ => Task.delay { releases += 1 })
      .unsafeRunToFuture()

    val successResult = success.unsafeRunToFuture()
    val errorResult = error.unsafeRunToFuture()
    scheduler.tick()
    assertEquals(successResult.value, Some(Success(1)))
    assert(errorResult.value.exists(_.isFailure))
    assertEquals(releases, 2)

    bracketed.cancel()
    scheduler.tick()
    assertEquals(releases, 3)
  }

  test("map2 is sequential while parMap2 and bounded traverse run concurrently in order") {
    implicit val scheduler: TestScheduler = TestScheduler(ExecutionModel.AlwaysAsyncExecution)
    val F = Async[Task]
    val sequential = Task.map2(F.sleep(1.second).map(_ => 1), F.sleep(1.second).map(_ => 2))(_ + _)
      .unsafeRunToFuture()

    scheduler.tick(1.second)
    assertEquals(sequential.value, None)
    scheduler.tick(1.second)
    assertEquals(sequential.value, Some(Success(3)))

    val parallel = Task.parMap2(F.sleep(1.second).map(_ => 1), F.sleep(1.second).map(_ => 2))(_ + _)
      .unsafeRunToFuture()
    scheduler.tick(1.second)
    assertEquals(parallel.value, Some(Success(3)))

    var running = 0
    var maxRunning = 0
    val traversed = Task.parTraverseN(2)(List(1, 2, 3, 4)) { value =>
      Task.delay {
        running += 1
        maxRunning = maxRunning.max(running)
      }.flatMap(_ => F.sleep(1.second)).flatMap { _ =>
        Task.delay { running -= 1; value }
      }
    }.unsafeRunToFuture()

    scheduler.tick(2.seconds)
    assertEquals(traversed.value, Some(Success(List(1, 2, 3, 4))))
    assertEquals(maxRunning, 2)
  }

  test("evalOnce caches success and nonfatal failure without evaluating at construction") {
    implicit val scheduler: TestScheduler = TestScheduler()
    var successEvaluations = 0
    var errorEvaluations = 0
    val cachedFailure = new RuntimeException("cached")
    val cachedSuccess = Task.evalOnce { successEvaluations += 1; successEvaluations }
    val cachedError = Task.evalOnce[Int] {
      errorEvaluations += 1
      throw cachedFailure
    }

    assertEquals(successEvaluations, 0)
    assertEquals(errorEvaluations, 0)

    val firstSuccess = cachedSuccess.unsafeRunToFuture()
    val secondSuccess = cachedSuccess.unsafeRunToFuture()
    val firstError = cachedError.attempt.unsafeRunToFuture()
    val secondError = cachedError.attempt.unsafeRunToFuture()
    scheduler.tick()

    assertEquals(firstSuccess.value, Some(Success(1)))
    assertEquals(secondSuccess.value, Some(Success(1)))
    assertEquals(successEvaluations, 1)
    assertEquals(firstError.value, Some(Success(Left(cachedFailure))))
    assertEquals(secondError.value, Some(Success(Left(cachedFailure))))
    assertEquals(errorEvaluations, 1)
  }

  test("fromFuture cancellation runs outer finalizers and propagates CancelableFuture cancel") {
    implicit val scheduler: TestScheduler = TestScheduler(ExecutionModel.AlwaysAsyncExecution)
    val plainPromise = Promise[Int]()
    var outerFinalizerRan = false
    val waiting = Task.fromFuture(plainPromise.future)
      .onCancel(Task.delay { outerFinalizerRan = true })
      .unsafeRunToFuture()

    scheduler.tick()
    assertEquals(waiting.value, None)
    waiting.cancel()
    scheduler.tick()
    assert(outerFinalizerRan)
    assertEquals(waiting.value, None)

    val cancelablePromise = Promise[Int]()
    var sourceCanceled = false
    var cancelableFinalizerRan = false
    val source = CancelableFuture(cancelablePromise.future, Cancelable(() => { sourceCanceled = true }))
    val cancelableWaiting = Task.fromFuture(source)
      .onCancel(Task.delay { cancelableFinalizerRan = true })
      .unsafeRunToFuture()

    scheduler.tick()
    cancelableWaiting.cancel()
    scheduler.tick()

    assert(sourceCanceled)
    assert(cancelableFinalizerRan)
    assertEquals(cancelableWaiting.value, None)
  }

  test("deferFutureAction receives the current scheduler once per evaluation") {
    implicit val scheduler: TestScheduler = TestScheduler()
    var invocations = 0
    val deferred = Task.deferFutureAction { current =>
      invocations += 1
      Future.successful(current)
    }

    assertEquals(invocations, 0)
    val first = deferred.unsafeRunToFuture()
    scheduler.tick()
    assertEquals(first.value.map(_.toOption.exists(_ eq scheduler)), Some(true))

    val second = deferred.unsafeRunToFuture()
    scheduler.tick()
    assertEquals(second.value.map(_.toOption.exists(_ eq scheduler)), Some(true))
    assertEquals(invocations, 2)
  }

  test("sequence and traverse preserve Vector shape, defer callbacks, and stop on error") {
    implicit val scheduler: TestScheduler = TestScheduler()
    var effects = 0
    var traversed = List.empty[Int]
    val source = Vector(Task.delay { effects += 1; 1 }, Task.delay { effects += 1; 2 })
    val sequenced = Task.sequence(source)
    val values = Vector(1, 2, 3)
    val traversedTask = Task.traverse(values) { value =>
      traversed :+= value
      Task.delay(value + 10)
    }

    assertEquals(effects, 0)
    assertEquals(traversed, Nil)
    val firstSequence = sequenced.unsafeRunToFuture()
    val firstTraverse = traversedTask.unsafeRunToFuture()
    scheduler.tick()
    assertEquals(firstSequence.value, Some(Success(Vector(1, 2))))
    assertEquals(firstTraverse.value, Some(Success(Vector(11, 12, 13))))
    assertEquals(traversed, List(1, 2, 3))

    val secondSequence = sequenced.unsafeRunToFuture()
    val secondTraverse = traversedTask.unsafeRunToFuture()
    scheduler.tick()
    assertEquals(secondSequence.value, Some(Success(Vector(1, 2))))
    assertEquals(secondTraverse.value, Some(Success(Vector(11, 12, 13))))
    assertEquals(effects, 4)
    assertEquals(traversed, List(1, 2, 3, 1, 2, 3))

    var visited = List.empty[Int]
    val error = new RuntimeException("stop")
    val stopped = Task.traverse(Vector(1, 2, 3)) { value =>
      visited :+= value
      if (value == 2) Task.raiseError[Int](error) else Task.pure(value)
    }.attempt.unsafeRunToFuture()
    scheduler.tick()
    assertEquals(stopped.value, Some(Success(Left(error))))
    assertEquals(visited, List(1, 2))
  }

  test("large sequence is stack safe and parallel traversals preserve Vector order") {
    implicit val scheduler: TestScheduler = TestScheduler(ExecutionModel.AlwaysAsyncExecution)
    val F = Async[Task]
    val large = Task.sequence(Vector.fill(10000)(Task.pure(1))).map(_.sum).unsafeRunToFuture()
    scheduler.tick()
    assertEquals(large.value, Some(Success(10000)))

    val traversed = Task.parTraverse(Vector(1, 2, 3)) { value =>
      F.sleep((4 - value).seconds).map(_ => value)
    }.unsafeRunToFuture()
    scheduler.tick(4.seconds)
    assertEquals(traversed.value, Some(Success(Vector(1, 2, 3))))

    val sequenced = Task.parSequence(Vector(
      F.sleep(3.seconds).map(_ => 1),
      F.sleep(1.second).map(_ => 2),
      F.sleep(2.seconds).map(_ => 3)
    )).unsafeRunToFuture()
    scheduler.tick(3.seconds)
    assertEquals(sequenced.value, Some(Success(Vector(1, 2, 3))))
  }

  test("parallel traversal cancels siblings and waits for their finalizers on error") {
    implicit val scheduler: TestScheduler = TestScheduler(ExecutionModel.AlwaysAsyncExecution)
    val F = Async[Task]
    val error = new RuntimeException("parallel failure")
    var siblingFinalized = false
    val result = Task.parTraverse(Vector(1, 2)) {
      case 1 => F.sleep(1.second).flatMap(_ => Task.raiseError[Int](error))
      case _ => Task.never[Int].onCancel(Task.delay { siblingFinalized = true })
    }.attempt.unsafeRunToFuture()

    scheduler.tick()
    assertEquals(result.value, None)
    assert(!siblingFinalized)
    scheduler.tick(1.second)

    assertEquals(result.value, Some(Success(Left(error))))
    assert(siblingFinalized)
  }

  test("guaranteeCase and bracketCase receive CE3 outcomes") {
    implicit val scheduler: TestScheduler = TestScheduler(ExecutionModel.AlwaysAsyncExecution)
    var guaranteeSucceeded = false
    var bracketSucceeded = false
    var bracketCanceled = false
    val success = Task.pure(42).guaranteeCase { outcome =>
      Task.delay { guaranteeSucceeded = outcome.isSuccess }
    }.unsafeRunToFuture()
    val bracket = Task.pure("resource").bracketCase(_ => Task.pure(7)) { (_, outcome) =>
      Task.delay { bracketSucceeded = outcome.isSuccess }
    }.unsafeRunToFuture()
    val canceled = Task.pure("resource").bracketCase(_ => Task.never[Int]) { (_, outcome) =>
      Task.delay { bracketCanceled = outcome.isCanceled }
    }.unsafeRunToFuture()

    scheduler.tick()
    assertEquals(success.value, Some(Success(42)))
    assertEquals(bracket.value, Some(Success(7)))
    assert(guaranteeSucceeded)
    assert(bracketSucceeded)
    assertEquals(canceled.value, None)
    canceled.cancel()
    scheduler.tick()
    assert(bracketCanceled)

    val sourceError = new RuntimeException("source")
    val finalizerError = new RuntimeException("finalizer")
    val failed = Task.raiseError[Int](sourceError)
      .guaranteeCase(_ => Task.raiseError[Unit](finalizerError))
      .attempt
      .unsafeRunToFuture()
    scheduler.tick()
    assertEquals(failed.value, Some(Success(Left(sourceError))))
  }

  test("race cancels its loser and waits for loser finalization") {
    implicit val scheduler: TestScheduler = TestScheduler(ExecutionModel.AlwaysAsyncExecution)
    val F = Async[Task]
    var loserFinalized = false
    val result = Task.race(
      F.sleep(1.second).as(42),
      Task.never[Int].onCancel(F.sleep(1.second).flatMap(_ => Task.delay { loserFinalized = true }))
    ).unsafeRunToFuture()

    scheduler.tick()
    assertEquals(result.value, None)
    assert(!loserFinalized)
    scheduler.tick(1.second)
    assertEquals(result.value, None)
    assert(!loserFinalized)
    scheduler.tick(1.second)

    assertEquals(result.value, Some(Success(Left(42))))
    assert(loserFinalized)
  }

  test("executeOn restores source scheduler and timed uses monotonic time") {
    implicit val source: TestScheduler = TestScheduler(ExecutionModel.AlwaysAsyncExecution)
    val target = TestScheduler(ExecutionModel.AlwaysAsyncExecution)
    val F = Async[Task]
    val contextCheck = F.executionContext
      .map(ec => ec eq target)
      .executeOn(target)
      .flatMap(onTarget => F.executionContext.map(ec => (onTarget, ec eq source)))
      .unsafeRunToFuture()

    source.tick()
    target.tick()
    source.tick()
    assertEquals(contextCheck.value, Some(Success((true, true))))

    val timed = F.sleep(2.seconds).map(_ => 42).timed.unsafeRunToFuture()
    source.tick(2.seconds)
    assertEquals(timed.value, Some(Success((2.seconds, 42))))
  }

  test("loopForever stops on error and restartLoop evolves state between retries") {
    implicit val scheduler: TestScheduler = TestScheduler()
    val error = new RuntimeException("stop loop")
    val stopped = Task.raiseError[Int](error).loopForever.attempt.unsafeRunToFuture()

    var attempts = 0
    var retryStates = List.empty[Int]
    val retried = Task.eval {
      attempts += 1
      if (attempts < 3) throw error
      attempts
    }.onErrorRestartLoop(0) { (failure, state, restart) =>
      retryStates :+= state
      if (failure eq error) restart(state + 1) else Task.raiseError[Int](failure)
    }.unsafeRunToFuture()

    scheduler.tick()
    assertEquals(stopped.value, Some(Success(Left(error))))
    assertEquals(retried.value, Some(Success(3)))
    assertEquals(attempts, 3)
    assertEquals(retryStates, List(0, 1))
  }
}
