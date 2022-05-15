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

package monix.catnap

import java.util.concurrent.CompletableFuture
import cats.effect.{ Async, Concurrent, ContextShift, IO }
import minitest.TestSuite
import monix.catnap.syntax._
import monix.execution.exceptions.DummyException
import monix.execution.schedulers.TestScheduler
import scala.concurrent.Promise
import scala.util.{ Failure, Success }

object FutureLiftJava8Suite extends TestSuite[TestScheduler] {
  def setup() = TestScheduler()
  def tearDown(env: TestScheduler): Unit =
    assert(env.state.tasks.isEmpty, "There should be no tasks left!")

  test("convert from async CompletableFuture; on success; with Async[IO]") { implicit s =>
    val future = new CompletableFuture[Int]()
    val f = IO(future).futureLift.unsafeToFuture()

    s.tick()
    assertEquals(f.value, None)

    future.complete(100)
    s.tick()
    assertEquals(f.value, Some(Success(100)))
  }

  test("convert from async CompletableFuture; on success; with Concurrent[IO]") { implicit s =>
    implicit val cs: ContextShift[IO] = SchedulerEffect.contextShift[IO](s)(IO.ioEffect)

    val future = new CompletableFuture[Int]()
    val f = IO(future).futureLift.unsafeToFuture()

    s.tick()
    assertEquals(f.value, None)

    future.complete(100)
    s.tick()
    assertEquals(f.value, Some(Success(100)))
  }

  test("convert from async CompletableFuture; on failure; with Async[IO]") { implicit s =>
    val future = new CompletableFuture[Int]()
    val f = convertAsync(IO(future)).unsafeToFuture()

    s.tick()
    assertEquals(f.value, None)

    val dummy = DummyException("dummy")
    future.completeExceptionally(dummy)

    s.tick()
    assertEquals(f.value, Some(Failure(dummy)))
  }

  test("convert from async CompletableFuture; on failure; with Concurrent[IO]") { implicit s =>
    implicit val cs: ContextShift[IO] = SchedulerEffect.contextShift[IO](s)(IO.ioEffect)

    val future = new CompletableFuture[Int]()
    val f = convertConcurrent(IO(future)).unsafeToFuture()

    s.tick()
    assertEquals(f.value, None)

    val dummy = DummyException("dummy")
    future.completeExceptionally(dummy)

    s.tick()
    assertEquals(f.value, Some(Failure(dummy)))
  }

  test("CompletableFuture is cancelable via IO") { implicit s =>
    implicit val cs: ContextShift[IO] = SchedulerEffect.contextShift[IO](s)(IO.ioEffect)

    val future = new CompletableFuture[Int]()

    val p = Promise[Int]()
    val cancel = convertConcurrent(IO(future)).unsafeRunCancelable(r =>
      p.complete(r match { case Right(a) => Success(a); case Left(e) => Failure(e) })
    )

    s.tick()
    assertEquals(p.future.value, None)

    cancel.unsafeRunAsyncAndForget()
    s.tick()
    assertEquals(p.future.value, None)

    // Should be already completed
    assert(!future.complete(1))
    s.tick()
    assertEquals(p.future.value, None)
  }

  def convertAsync[F[_], A](fa: F[CompletableFuture[A]])(implicit F: Async[F]): F[A] =
    fa.futureLift

  def convertConcurrent[F[_], A](fa: F[CompletableFuture[A]])(implicit F: Concurrent[F]): F[A] =
    FutureLift.from(fa)
}
