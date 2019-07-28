/*
 * Copyright (c) 2014-2019 by The Monix Project Developers.
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

import cats.implicits._
import minitest.{SimpleTestSuite, TestSuite}
import monix.execution.Scheduler
import monix.execution.exceptions.DummyException
import monix.execution.schedulers.{SchedulerService, TestScheduler}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

object TaskToFutureJVMSuite extends TestSuite[SchedulerService] {
  private val ThreadName = "test-thread"

  override def setup(): SchedulerService = {
    Scheduler.singleThread(ThreadName)
  }

  override def tearDown(env: SchedulerService): Unit =
    env.shutdown()

  private val TestEC = new ExecutionContext {
    def execute(r: Runnable): Unit = {
      val th = new Thread(r)
      th.setName(ThreadName)
      th.start()
    }

    def reportFailure(cause: Throwable): Unit =
      throw cause
  }

  def createFuture[A](ec: ExecutionContext)(r: => A): (Future[A], CountDownLatch) = {
    val started = new CountDownLatch(1)
    val latch = new CountDownLatch(1)
    val f = Future {
      started.countDown()
      latch.await()
      r
    }(ec)

    started.await()
    (f, latch)
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
        assert(name.startsWith(ThreadName), s"'$name' starts with '$ThreadName'")
      }
    }
  }

  testAsync("Task.fromFuture(completed) should not shift to the main scheduler") { s =>
    import Scheduler.Implicits.global

    repeatTest(1000) {
      val f = Future.successful(())
      val r = Task
        .fromFuture(f)
        .flatMap(_ => Task(Thread.currentThread().getName))
        .runToFuture(s)

      for (name <- r) yield {
        assert(!name.startsWith(ThreadName), s"'$name' does not start with '$ThreadName'")
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
        assert(name.startsWith(ThreadName), s"'$name' starts with '$ThreadName'")
      }
    }
  }

  testAsync("Task.fromFuture(completed error) should not shift to the main scheduler") { s =>
    import Scheduler.Implicits.global

    repeatTest(1000) {
      val dummy = DummyException("dummy")
      val f = Future.failed(dummy)
      val r = Task
        .fromFuture(f)
        .onErrorHandle(e => assertEquals(e, dummy))
        .flatMap(_ => Task(Thread.currentThread().getName))
        .runToFuture(s)

      for (name <- r) yield {
        assert(!name.startsWith(ThreadName), s"'$name' does not start with '$ThreadName'")
      }
    }
  }

  
  //  test("Task.deferFuture should shift back to the main scheduler") {
//    implicit val s = Scheduler(TestEC)
//    val s2 = Scheduler.global
//
//    val task = Task.deferFuture(Future(1)(s2)).flatMap(_ => Task(Thread.currentThread().getName))
//
//    assertEquals(task.runSyncUnsafe(), ThreadName)
//  }
//
//  test("Task.deferFuture(error) should shift back to the main scheduler") {
//    implicit val s = Scheduler(TestEC)
//    val s2 = Scheduler.global
//
//    val dummy = DummyException("dummy")
//    val task = Task.deferFuture(Future(throw dummy)(s2)).attempt.flatMap(_ => Task(Thread.currentThread().getName))
//
//    assertEquals(task.runSyncUnsafe(), ThreadName)
//  }
//
//  test("Task.deferFutureAction should shift back to the main scheduler") {
//    implicit val s = Scheduler(TestEC)
//    val s2 = Scheduler.global
//
//    val task = Task.deferFutureAction(implicit s => Future(1)(s2)).flatMap(_ => Task(Thread.currentThread().getName))
//
//    assertEquals(task.runSyncUnsafe(), ThreadName)
//  }
//
//  test("Task.deferFutureAction(error) should shift back to the main scheduler") {
//    implicit val s = Scheduler(TestEC)
//    val s2 = Scheduler.global
//
//    val dummy = DummyException("dummy")
//    val task = Task
//      .deferFutureAction(implicit s => Future(throw dummy)(s2))
//      .attempt
//      .flatMap(_ => Task(Thread.currentThread().getName))
//
//    assertEquals(task.runSyncUnsafe(), ThreadName)
//  }
//
//  test("Task.deferFuture(cancelable) should shift back to the main scheduler") {
//    implicit val s = Scheduler(TestEC)
//    val s2 = Scheduler.global
//
//    val f = Task.eval(1).runToFuture
//    val task = Task.deferFuture(f.map(x => x)(s2)).flatMap(_ => Task(Thread.currentThread().getName))
//
//    assertEquals(task.runSyncUnsafe(), ThreadName)
//  }
//
//  test("Task.deferFutureAction(cancelable) should shift back to the main scheduler") {
//    implicit val s = Scheduler(TestEC)
//    val s2 = Scheduler.global
//
//    val f = Task.eval(1).delayExecution(1.second).runToFuture(s2)
//    val task = Task.deferFutureAction(implicit s => f).flatMap(_ => Task(Thread.currentThread().getName))
//
//    assertEquals(task.runSyncUnsafe(), ThreadName)
//  }
//
//  test("Task.fromFuture(completed) should shift back to the main scheduler") {
//    implicit val s = Scheduler(TestEC)
//    val s2 = TestScheduler()
//
//    val f = Future(1)(s2)
//    val task = Task.fromFuture(f).flatMap(_ => Task(Thread.currentThread().getName))
//
//    s2.tick()
//    assertEquals(task.runSyncUnsafe(), ThreadName)
//  }
//
//  test("Task.deferFuture(completed) should shift back to the main scheduler") {
//    implicit val s = Scheduler(TestEC)
//    val s2 = TestScheduler()
//
//    val f = Future(1)(s2)
//    val task = Task.deferFuture(f).flatMap(_ => Task(Thread.currentThread().getName))
//
//    s2.tick()
//    assertEquals(task.runSyncUnsafe(), ThreadName)
//  }
//
//  test("Task.deferFutureAction(completed) should shift back to the main scheduler") {
//    implicit val s = Scheduler(TestEC)
//    val s2 = TestScheduler()
//
//    val f = Future(1)(s2)
//    val task = Task.deferFutureAction(_ => f).flatMap(_ => Task(Thread.currentThread().getName))
//
//    s2.tick()
//    assertEquals(task.runSyncUnsafe(), ThreadName)
//  }
}
