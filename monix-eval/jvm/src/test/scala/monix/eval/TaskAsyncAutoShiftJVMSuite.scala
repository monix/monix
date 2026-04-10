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

import java.util.concurrent.CountDownLatch

import minitest.TestSuite
import monix.execution.exceptions.DummyException
import monix.execution.schedulers.SchedulerService
import monix.execution.{ Cancelable, CancelableFuture, ExecutionModel, Scheduler }

import scala.concurrent.duration._
import scala.concurrent.{ blocking, ExecutionContext, Future }

object TaskAsyncAutoShiftJVMSuite extends TestSuite[SchedulerService] {
  private val ThreadName = "test-thread"

  override def setup(): SchedulerService =
    Scheduler.singleThread(ThreadName, executionModel = ExecutionModel.SynchronousExecution)

  override def tearDown(env: SchedulerService): Unit = {
    env.shutdown()
    blocking {
      assert(env.awaitTermination(10.seconds), "env.awaitTermination")
    }
  }

  def createFuture[A](ec: ExecutionContext)(r: => A): (Future[A], CountDownLatch) = {
    val started = new CountDownLatch(1)
    val latch = new CountDownLatch(1)
    val f = Future {
      started.countDown()
      blocking(latch.await())
      r
    }(ec)

    blocking(started.await())
    (f, latch)
  }

  def createCancelableFuture[A](ec: ExecutionContext)(r: => A): (CancelableFuture[A], CountDownLatch) = {
    val started = new CountDownLatch(1)
    val latch = new CountDownLatch(1)

    val f = Future {
      started.countDown()
      blocking(latch.await())
      r
    }(ec)

    blocking(started.await())
    (CancelableFuture(f, Cancelable.empty), latch)
  }

  def repeatTest(times: Int)(f: => Future[Unit])(implicit ec: ExecutionContext): Future[Unit] = {
    if (times > 0) f.flatMap(_ => repeatTest(times - 1)(f))
    else Future.successful(())
  }

  testAsync("Task.fromFuture(async) should shift back to the main scheduler") { implicit s =>
    val s2 = Scheduler.global

    repeatTest(1000) {
      val (f, latch) = createFuture(s2)(())
      val r = Task
        .fromFuture(f)
        .flatMap(_ => Task(Thread.currentThread().getName))
        .runToFuture

      latch.countDown()
      for (name <- r) yield {
        assert(name.startsWith(ThreadName), s"'$name' should start with '$ThreadName'")
      }
    }
  }

  testAsync("Task.fromFuture(completed) should not shift to the main scheduler") { s =>
    implicit val ec: Scheduler = Scheduler.global

    repeatTest(1000) {
      val f = Future.successful(())
      val r = Task
        .fromFuture(f)
        .flatMap(_ => Task(Thread.currentThread().getName))
        .runToFuture(s)

      for (name <- r) yield {
        assert(!name.startsWith(ThreadName), s"'$name' should not start with '$ThreadName'")
      }
    }
  }

  testAsync("Task.fromFuture(async error) should shift back to the main scheduler") { implicit ec =>
    val s2 = Scheduler.global

    repeatTest(1000) {
      val dummy = DummyException("dummy")
      val (f, latch) = createFuture[Unit](s2)(throw dummy)

      val r = Task
        .fromFuture(f)
        .onErrorHandle(e => assertEquals(e, dummy))
        .flatMap(_ => Task(Thread.currentThread().getName))
        .runToFuture

      latch.countDown()
      for (name <- r) yield {
        assert(name.startsWith(ThreadName), s"'$name' should start with '$ThreadName'")
      }
    }
  }

  testAsync("Task.fromFuture(completed error) should not shift to the main scheduler") { s =>
    implicit val ec: Scheduler = Scheduler.global

    repeatTest(1000) {
      val dummy = DummyException("dummy")
      val f = Future.failed(dummy)
      val r = Task
        .fromFuture(f)
        .onErrorHandle(e => assertEquals(e, dummy))
        .flatMap(_ => Task(Thread.currentThread().getName))
        .runToFuture(s)

      for (name <- r) yield {
        assert(!name.startsWith(ThreadName), s"'$name' should not start with '$ThreadName'")
      }
    }
  }

  testAsync("Task.deferFuture(async) should shift back to the main scheduler") { implicit s =>
    val s2 = Scheduler.global

    repeatTest(1000) {
      val (f, latch) = createFuture(s2)(())
      val r = Task
        .deferFuture(f)
        .flatMap(_ => Task(Thread.currentThread().getName))
        .runToFuture

      latch.countDown()
      for (name <- r) yield {
        assert(name.startsWith(ThreadName), s"'$name' should start with '$ThreadName'")
      }
    }
  }

  testAsync("Task.deferFuture(completed) should not shift to the main scheduler") { s =>
    implicit val ec: Scheduler = Scheduler.global

    repeatTest(1000) {
      val f = Future.successful(())
      val r = Task
        .deferFuture(f)
        .flatMap(_ => Task(Thread.currentThread().getName))
        .runToFuture(s)

      for (name <- r) yield {
        assert(!name.startsWith(ThreadName), s"'$name' should not start with '$ThreadName'")
      }
    }
  }

  testAsync("Task.deferFuture(async error) should shift back to the main scheduler") { implicit ec =>
    val s2 = Scheduler.global

    repeatTest(1000) {
      val dummy = DummyException("dummy")
      val (f, latch) = createFuture[Unit](s2)(throw dummy)

      val r = Task
        .deferFuture(f)
        .onErrorHandle(e => assertEquals(e, dummy))
        .flatMap(_ => Task(Thread.currentThread().getName))
        .runToFuture

      latch.countDown()
      for (name <- r) yield {
        assert(name.startsWith(ThreadName), s"'$name' should start with '$ThreadName'")
      }
    }
  }

  testAsync("Task.deferFuture(completed error) should not shift to the main scheduler") { s =>
    implicit val ec: Scheduler = Scheduler.global

    repeatTest(1000) {
      val dummy = DummyException("dummy")
      val f = Future.failed(dummy)
      val r = Task
        .deferFuture(f)
        .onErrorHandle(e => assertEquals(e, dummy))
        .flatMap(_ => Task(Thread.currentThread().getName))
        .runToFuture(s)

      for (name <- r) yield {
        assert(!name.startsWith(ThreadName), s"'$name' should not start with '$ThreadName'")
      }
    }
  }

  testAsync("Task.deferFutureAction(async) should shift back to the main scheduler") { implicit s =>
    val s2 = Scheduler.global

    repeatTest(1000) {
      val (f, latch) = createFuture(s2)(())
      val r = Task
        .deferFutureAction(_ => f)
        .flatMap(_ => Task(Thread.currentThread().getName))
        .runToFuture

      latch.countDown()
      for (name <- r) yield {
        assert(name.startsWith(ThreadName), s"'$name' should start with '$ThreadName'")
      }
    }
  }

  testAsync("Task.deferFutureAction(completed) should not shift to the main scheduler") { s =>
    implicit val ec: Scheduler = Scheduler.global

    repeatTest(1000) {
      val f = Future.successful(())
      val r = Task
        .deferFutureAction(_ => f)
        .flatMap(_ => Task(Thread.currentThread().getName))
        .runToFuture(s)

      for (name <- r) yield {
        assert(!name.startsWith(ThreadName), s"'$name' should not start with '$ThreadName'")
      }
    }
  }

  testAsync("Task.deferFutureAction(async error) should shift back to the main scheduler") { implicit ec =>
    val s2 = Scheduler.global

    repeatTest(1000) {
      val dummy = DummyException("dummy")
      val (f, latch) = createFuture[Unit](s2)(throw dummy)

      val r = Task
        .deferFutureAction(_ => f)
        .onErrorHandle(e => assertEquals(e, dummy))
        .flatMap(_ => Task(Thread.currentThread().getName))
        .runToFuture

      latch.countDown()
      for (name <- r) yield {
        assert(name.startsWith(ThreadName), s"'$name' should start with '$ThreadName'")
      }
    }
  }

  testAsync("Task.deferFutureAction(completed error) should not shift to the main scheduler") { s =>
    implicit val ec: Scheduler = Scheduler.global

    repeatTest(1000) {
      val dummy = DummyException("dummy")
      val f = Future.failed(dummy)
      val r = Task
        .deferFutureAction(_ => f)
        .onErrorHandle(e => assertEquals(e, dummy))
        .flatMap(_ => Task(Thread.currentThread().getName))
        .runToFuture(s)

      for (name <- r) yield {
        assert(!name.startsWith(ThreadName), s"'$name' should not start with '$ThreadName'")
      }
    }
  }

  testAsync("Task.fromFuture(async cancelable) should shift back to the main scheduler") { implicit s =>
    val s2 = Scheduler.global

    repeatTest(1000) {
      val (f, latch) = createCancelableFuture(s2)(())
      val r = Task
        .fromFuture(f)
        .flatMap(_ => Task(Thread.currentThread().getName))
        .runToFuture

      latch.countDown()
      for (name <- r) yield {
        assert(name.startsWith(ThreadName), s"'$name' should start with '$ThreadName'")
      }
    }
  }

  testAsync("Task.fromFuture(completed cancelable) should not shift to the main scheduler") { s =>
    implicit val ec: Scheduler = Scheduler.global

    repeatTest(1000) {
      val f = CancelableFuture.successful(())
      val r = Task
        .fromFuture(f)
        .flatMap(_ => Task(Thread.currentThread().getName))
        .runToFuture(s)

      for (name <- r) yield {
        assert(!name.startsWith(ThreadName), s"'$name' should not start with '$ThreadName'")
      }
    }
  }

  testAsync("Task.fromFuture(async error cancelable) should shift back to the main scheduler") { implicit ec =>
    val s2 = Scheduler.global

    repeatTest(1000) {
      val dummy = DummyException("dummy")
      val (f, latch) = createCancelableFuture[Unit](s2)(throw dummy)

      val r = Task
        .fromFuture(f)
        .onErrorHandle(e => assertEquals(e, dummy))
        .flatMap(_ => Task(Thread.currentThread().getName))
        .runToFuture

      latch.countDown()
      for (name <- r) yield {
        assert(name.startsWith(ThreadName), s"'$name' should start with '$ThreadName'")
      }
    }
  }

  testAsync("Task.fromFuture(completed error cancelable) should not shift to the main scheduler") { s =>
    implicit val ec: Scheduler = Scheduler.global

    repeatTest(1000) {
      val dummy = DummyException("dummy")
      val f = CancelableFuture.failed(dummy)
      val r = Task
        .fromFuture(f)
        .onErrorHandle(e => assertEquals(e, dummy))
        .flatMap(_ => Task(Thread.currentThread().getName))
        .runToFuture(s)

      for (name <- r) yield {
        assert(!name.startsWith(ThreadName), s"'$name' should not start with '$ThreadName'")
      }
    }
  }

  testAsync("Task.deferFuture(async cancelable) should shift back to the main scheduler") { implicit s =>
    val s2 = Scheduler.global

    repeatTest(1000) {
      val (f, latch) = createCancelableFuture(s2)(())
      val r = Task
        .deferFuture(f)
        .flatMap(_ => Task(Thread.currentThread().getName))
        .runToFuture

      latch.countDown()
      for (name <- r) yield {
        assert(name.startsWith(ThreadName), s"'$name' should start with '$ThreadName'")
      }
    }
  }

  testAsync("Task.deferFuture(completed cancelable) should not shift to the main scheduler") { s =>
    implicit val ec: Scheduler = Scheduler.global

    repeatTest(1000) {
      val f = CancelableFuture.successful(())
      val r = Task
        .deferFuture(f)
        .flatMap(_ => Task(Thread.currentThread().getName))
        .runToFuture(s)

      for (name <- r) yield {
        assert(!name.startsWith(ThreadName), s"'$name' should not start with '$ThreadName'")
      }
    }
  }

  testAsync("Task.deferFuture(async error cancelable) should shift back to the main scheduler") { implicit ec =>
    val s2 = Scheduler.global

    repeatTest(1000) {
      val dummy = DummyException("dummy")
      val (f, latch) = createCancelableFuture[Unit](s2)(throw dummy)

      val r = Task
        .deferFuture(f)
        .onErrorHandle(e => assertEquals(e, dummy))
        .flatMap(_ => Task(Thread.currentThread().getName))
        .runToFuture

      latch.countDown()
      for (name <- r) yield {
        assert(name.startsWith(ThreadName), s"'$name' should start with '$ThreadName'")
      }
    }
  }

  testAsync("Task.deferFuture(completed error cancelable) should not shift to the main scheduler") { s =>
    implicit val ec: Scheduler = Scheduler.global

    repeatTest(1000) {
      val dummy = DummyException("dummy")
      val f = CancelableFuture.failed(dummy)
      val r = Task
        .deferFuture(f)
        .onErrorHandle(e => assertEquals(e, dummy))
        .flatMap(_ => Task(Thread.currentThread().getName))
        .runToFuture(s)

      for (name <- r) yield {
        assert(!name.startsWith(ThreadName), s"'$name' should not start with '$ThreadName'")
      }
    }
  }

  testAsync("Task.deferFutureAction(async cancelable) should shift back to the main scheduler") { implicit s =>
    val s2 = Scheduler.global

    repeatTest(1000) {
      val (f, latch) = createCancelableFuture(s2)(())
      val r = Task
        .deferFutureAction(_ => f)
        .flatMap(_ => Task(Thread.currentThread().getName))
        .runToFuture

      latch.countDown()
      for (name <- r) yield {
        assert(name.startsWith(ThreadName), s"'$name' should start with '$ThreadName'")
      }
    }
  }

  testAsync("Task.deferFutureAction(completed cancelable) should not shift to the main scheduler") { s =>
    implicit val ec: Scheduler = Scheduler.global

    repeatTest(1000) {
      val f = CancelableFuture.successful(())
      val r = Task
        .deferFutureAction(_ => f)
        .flatMap(_ => Task(Thread.currentThread().getName))
        .runToFuture(s)

      for (name <- r) yield {
        assert(!name.startsWith(ThreadName), s"'$name' should not start with '$ThreadName'")
      }
    }
  }

  testAsync("Task.deferFutureAction(async error cancelable) should shift back to the main scheduler") { implicit ec =>
    val s2 = Scheduler.global

    repeatTest(1000) {
      val dummy = DummyException("dummy")
      val (f, latch) = createCancelableFuture[Unit](s2)(throw dummy)

      val r = Task
        .deferFutureAction(_ => f)
        .onErrorHandle(e => assertEquals(e, dummy))
        .flatMap(_ => Task(Thread.currentThread().getName))
        .runToFuture

      latch.countDown()
      for (name <- r) yield {
        assert(name.startsWith(ThreadName), s"'$name' should start with '$ThreadName'")
      }
    }
  }

  testAsync("Task.deferFutureAction(completed error cancelable) should not shift to the main scheduler") { s =>
    implicit val ec: Scheduler = Scheduler.global

    repeatTest(1000) {
      val dummy = DummyException("dummy")
      val f = CancelableFuture.failed(dummy)
      val r = Task
        .deferFutureAction(_ => f)
        .onErrorHandle(e => assertEquals(e, dummy))
        .flatMap(_ => Task(Thread.currentThread().getName))
        .runToFuture(s)

      for (name <- r) yield {
        assert(!name.startsWith(ThreadName), s"'$name' should not start with '$ThreadName'")
      }
    }
  }

  testAsync("Task.async(register) should shift back if register forks") { s =>
    implicit val s2: Scheduler = Scheduler.global

    repeatTest(1000) {
      val r = Task
        .async[Unit] { cb =>
          s2.execute(() => cb.onSuccess(()))
        }
        .flatMap(_ => Task(Thread.currentThread().getName))
        .executeAsync
        .runToFuture(s)

      for (name <- r) yield {
        assert(name.startsWith(ThreadName), s"'$name' should start with '$ThreadName'")
      }
    }
  }

  testAsync("Task.async(register) should not shift back if register does not fork") { s =>
    implicit val ec: Scheduler = Scheduler.global

    repeatTest(1000) {
      val r = Task
        .async[Unit](_.onSuccess(()))
        .flatMap(_ => Task(Thread.currentThread().getName))
        .runToFuture(s)

      for (name <- r) yield {
        assert(!name.startsWith(ThreadName), s"'$name' should not start with '$ThreadName'")
      }
    }
  }

  testAsync("Task.async(register error) should shift back if register forks") { s =>
    implicit val s2: Scheduler = Scheduler.global

    repeatTest(1000) {
      val dummy = DummyException("dummy")
      val r = Task
        .async[Unit] { cb =>
          s2.execute(() => cb.onError(dummy))
        }
        .onErrorHandle(e => assertEquals(e, dummy))
        .flatMap(_ => Task(Thread.currentThread().getName))
        .executeAsync
        .runToFuture(s)

      for (name <- r) yield {
        assert(name.startsWith(ThreadName), s"'$name' should start with '$ThreadName'")
      }
    }
  }

  testAsync("Task.async(register error) should not shift back if register does not fork") { s =>
    implicit val ec: Scheduler = Scheduler.global

    repeatTest(1000) {
      val dummy = DummyException("dummy")
      val r = Task
        .async[Unit](_.onError(dummy))
        .onErrorHandle(e => assertEquals(e, dummy))
        .flatMap(_ => Task(Thread.currentThread().getName))
        .runToFuture(s)

      for (name <- r) yield {
        assert(!name.startsWith(ThreadName), s"'$name' should not start with '$ThreadName'")
      }
    }
  }

  testAsync("Task.async0(register) should shift back if register forks") { s =>
    implicit val s2: Scheduler = Scheduler.global

    repeatTest(1000) {
      val r = Task
        .async0[Unit] { (_, cb) =>
          s2.execute(() => cb.onSuccess(()))
        }
        .flatMap(_ => Task(Thread.currentThread().getName))
        .executeAsync
        .runToFuture(s)

      for (name <- r) yield {
        assert(name.startsWith(ThreadName), s"'$name' should start with '$ThreadName'")
      }
    }
  }

  testAsync("Task.async0(register) should not shift back if register does not fork") { s =>
    implicit val ec: Scheduler = Scheduler.global

    repeatTest(1000) {
      val r = Task
        .async0[Unit]((_, cb) => cb.onSuccess(()))
        .flatMap(_ => Task(Thread.currentThread().getName))
        .runToFuture(s)

      for (name <- r) yield {
        assert(!name.startsWith(ThreadName), s"'$name' should not start with '$ThreadName'")
      }
    }
  }

  testAsync("Task.async0(register error) should shift back if register forks") { s =>
    implicit val s2: Scheduler = Scheduler.global

    repeatTest(1000) {
      val dummy = DummyException("dummy")
      val r = Task
        .async0[Unit] { (_, cb) =>
          s2.execute(() => cb.onError(dummy))
        }
        .onErrorHandle(e => assertEquals(e, dummy))
        .flatMap(_ => Task(Thread.currentThread().getName))
        .executeAsync
        .runToFuture(s)

      for (name <- r) yield {
        assert(name.startsWith(ThreadName), s"'$name' should start with '$ThreadName'")
      }
    }
  }

  testAsync("Task.async0(register error) should not shift back if register does not fork") { s =>
    implicit val ec: Scheduler = Scheduler.global

    repeatTest(1000) {
      val dummy = DummyException("dummy")
      val r = Task
        .async0[Unit]((_, cb) => cb.onError(dummy))
        .onErrorHandle(e => assertEquals(e, dummy))
        .flatMap(_ => Task(Thread.currentThread().getName))
        .runToFuture(s)

      for (name <- r) yield {
        assert(!name.startsWith(ThreadName), s"'$name' should not start with '$ThreadName'")
      }
    }
  }

  testAsync("Task.cancelable(register) should shift back if register forks") { s =>
    implicit val s2: Scheduler = Scheduler.global

    repeatTest(1000) {
      val r = Task
        .cancelable[Unit] { cb =>
          s2.execute(() => cb.onSuccess(()))
          Task(())
        }
        .flatMap(_ => Task(Thread.currentThread().getName))
        .executeAsync
        .runToFuture(s)

      for (name <- r) yield {
        assert(name.startsWith(ThreadName), s"'$name' should start with '$ThreadName'")
      }
    }
  }

  testAsync("Task.cancelable(register) should not shift back if register does not fork") { s =>
    implicit val ec: Scheduler = Scheduler.global

    repeatTest(1000) {
      val r = Task
        .cancelable[Unit] { cb =>
          cb.onSuccess(())
          Task(())
        }
        .flatMap(_ => Task(Thread.currentThread().getName))
        .runToFuture(s)

      for (name <- r) yield {
        assert(!name.startsWith(ThreadName), s"'$name' should not start with '$ThreadName'")
      }
    }
  }

  testAsync("Task.cancelable(register error) should shift back if register forks") { s =>
    implicit val s2: Scheduler = Scheduler.global

    repeatTest(1000) {
      val dummy = DummyException("dummy")
      val r = Task
        .cancelable[Unit] { cb =>
          s2.execute(() => cb.onError(dummy))
          Task(())
        }
        .onErrorHandle(e => assertEquals(e, dummy))
        .flatMap(_ => Task(Thread.currentThread().getName))
        .executeAsync
        .runToFuture(s)

      for (name <- r) yield {
        assert(name.startsWith(ThreadName), s"'$name' should start with '$ThreadName'")
      }
    }
  }

  testAsync("Task.cancelable(register error) should not shift back if register does not fork") { s =>
    implicit val ec: Scheduler = Scheduler.global

    repeatTest(1000) {
      val dummy = DummyException("dummy")
      val r = Task
        .cancelable[Unit] { cb =>
          cb.onError(dummy)
          Task(())
        }
        .onErrorHandle(e => assertEquals(e, dummy))
        .flatMap(_ => Task(Thread.currentThread().getName))
        .runToFuture(s)

      for (name <- r) yield {
        assert(!name.startsWith(ThreadName), s"'$name' should not start with '$ThreadName'")
      }
    }
  }

  testAsync("Task.cancelable0(register) should shift back if register forks") { s =>
    implicit val s2: Scheduler = Scheduler.global

    repeatTest(1000) {
      val r = Task
        .cancelable0[Unit] { (_, cb) =>
          s2.execute(() => cb.onSuccess(()))
          Task(())
        }
        .flatMap(_ => Task(Thread.currentThread().getName))
        .executeAsync
        .runToFuture(s)

      for (name <- r) yield {
        assert(name.startsWith(ThreadName), s"'$name' should start with '$ThreadName'")
      }
    }
  }

  testAsync("Task.cancelable0(register) should not shift back if register does not fork") { s =>
    implicit val ec: Scheduler = Scheduler.global

    repeatTest(1000) {
      val r = Task
        .cancelable0[Unit] { (_, cb) =>
          cb.onSuccess(())
          Task(())
        }
        .flatMap(_ => Task(Thread.currentThread().getName))
        .runToFuture(s)

      for (name <- r) yield {
        assert(!name.startsWith(ThreadName), s"'$name' should not start with '$ThreadName'")
      }
    }
  }

  testAsync("Task.cancelable0(register error) should shift back if register forks") { s =>
    implicit val s2: Scheduler = Scheduler.global

    repeatTest(1000) {
      val dummy = DummyException("dummy")
      val r = Task
        .cancelable0[Unit] { (_, cb) =>
          s2.execute(() => cb.onError(dummy))
          Task(())
        }
        .onErrorHandle(e => assertEquals(e, dummy))
        .flatMap(_ => Task(Thread.currentThread().getName))
        .executeAsync
        .runToFuture(s)

      for (name <- r) yield {
        assert(name.startsWith(ThreadName), s"'$name' should start with '$ThreadName'")
      }
    }
  }

  testAsync("Task.cancelable0(register error) should not shift back if register does not fork") { s =>
    implicit val ec: Scheduler = Scheduler.global

    repeatTest(1000) {
      val dummy = DummyException("dummy")
      val r = Task
        .cancelable0[Unit] { (_, cb) =>
          cb.onError(dummy)
          Task(())
        }
        .onErrorHandle(e => assertEquals(e, dummy))
        .flatMap(_ => Task(Thread.currentThread().getName))
        .runToFuture(s)

      for (name <- r) yield {
        assert(!name.startsWith(ThreadName), s"'$name' should not start with '$ThreadName'")
      }
    }
  }

  testAsync("Task.create(register) should shift back if register forks") { s =>
    implicit val s2: Scheduler = Scheduler.global

    repeatTest(1000) {
      val r = Task
        .create[Unit] { (_, cb) =>
          s2.execute(() => cb.onSuccess(()))
          Task(())
        }
        .flatMap(_ => Task(Thread.currentThread().getName))
        .executeAsync
        .runToFuture(s)

      for (name <- r) yield {
        assert(name.startsWith(ThreadName), s"'$name' should start with '$ThreadName'")
      }
    }
  }

  testAsync("Task.create(register) should not shift back if register does not fork") { s =>
    implicit val ec: Scheduler = Scheduler.global

    repeatTest(1000) {
      val r = Task
        .create[Unit] { (_, cb) =>
          cb.onSuccess(())
          Task(())
        }
        .flatMap(_ => Task(Thread.currentThread().getName))
        .runToFuture(s)

      for (name <- r) yield {
        assert(!name.startsWith(ThreadName), s"'$name' should not start with '$ThreadName'")
      }
    }
  }

  testAsync("Task.create(register error) should shift back if register forks") { s =>
    implicit val s2: Scheduler = Scheduler.global

    repeatTest(1000) {
      val dummy = DummyException("dummy")
      val r = Task
        .create[Unit] { (_, cb) =>
          s2.execute(() => cb.onError(dummy))
          Task(())
        }
        .onErrorHandle(e => assertEquals(e, dummy))
        .flatMap(_ => Task(Thread.currentThread().getName))
        .executeAsync
        .runToFuture(s)

      for (name <- r) yield {
        assert(name.startsWith(ThreadName), s"'$name' should start with '$ThreadName'")
      }
    }
  }

  testAsync("Task.create(register error) should not shift back if register does not fork") { s =>
    implicit val ec: Scheduler = Scheduler.global

    repeatTest(1000) {
      val dummy = DummyException("dummy")
      val r = Task
        .create[Unit] { (_, cb) =>
          cb.onError(dummy)
          Task(())
        }
        .onErrorHandle(e => assertEquals(e, dummy))
        .flatMap(_ => Task(Thread.currentThread().getName))
        .runToFuture(s)

      for (name <- r) yield {
        assert(!name.startsWith(ThreadName), s"'$name' should not start with '$ThreadName'")
      }
    }
  }

  /// ...
  testAsync("Task.asyncF(register) should shift back if register forks") { s =>
    implicit val s2: Scheduler = Scheduler.global

    repeatTest(1000) {
      val r = Task
        .asyncF[Unit] { cb =>
          Task(s2.execute(() => cb.onSuccess(())))
        }
        .flatMap(_ => Task(Thread.currentThread().getName))
        .executeAsync
        .runToFuture(s)

      for (name <- r) yield {
        assert(name.startsWith(ThreadName), s"'$name' should start with '$ThreadName'")
      }
    }
  }

  testAsync("Task.asyncF(register) should not shift back if register does not fork") { s =>
    implicit val ec: Scheduler = Scheduler.global

    repeatTest(1000) {
      val r = Task
        .asyncF[Unit](cb => Task(cb.onSuccess(())))
        .flatMap(_ => Task(Thread.currentThread().getName))
        .runToFuture(s)

      for (name <- r) yield {
        assert(!name.startsWith(ThreadName), s"'$name' should not start with '$ThreadName'")
      }
    }
  }

  testAsync("Task.asyncF(register error) should shift back if register forks") { s =>
    implicit val s2: Scheduler = Scheduler.global

    repeatTest(1000) {
      val dummy = DummyException("dummy")
      val r = Task
        .asyncF[Unit] { cb =>
          Task(s2.execute(() => cb.onError(dummy)))
        }
        .onErrorHandle(e => assertEquals(e, dummy))
        .flatMap(_ => Task(Thread.currentThread().getName))
        .executeAsync
        .runToFuture(s)

      for (name <- r) yield {
        assert(name.startsWith(ThreadName), s"'$name' should start with '$ThreadName'")
      }
    }
  }

  testAsync("Task.asyncF(register error) should not shift back if register does not fork") { s =>
    implicit val ec: Scheduler = Scheduler.global

    repeatTest(1000) {
      val dummy = DummyException("dummy")
      val r = Task
        .asyncF[Unit](cb => Task(cb.onError(dummy)))
        .onErrorHandle(e => assertEquals(e, dummy))
        .flatMap(_ => Task(Thread.currentThread().getName))
        .runToFuture(s)

      for (name <- r) yield {
        assert(!name.startsWith(ThreadName), s"'$name' should not start with '$ThreadName'")
      }
    }
  }
}
