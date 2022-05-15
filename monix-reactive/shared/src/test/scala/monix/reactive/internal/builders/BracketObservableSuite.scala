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

package monix.reactive.internal.builders

import cats.implicits._
import cats.effect.ExitCase
import cats.effect.concurrent.Deferred
import monix.eval.Task
import monix.execution.Ack.Continue
import monix.reactive.observers.Subscriber
import monix.reactive.{BaseTestSuite, Observable}

import scala.concurrent.duration._
import scala.util.Success

object BracketObservableSuite extends BaseTestSuite {
  test("simple bracket") { implicit s =>
    val rs = new Semaphore()

    val f = Observable
      .fromTask(rs.acquire)
      .bracket(Observable.pure)(_.release)
      .mapEval(_ => Task.now(1).delayExecution(1.second))
      .runAsyncGetFirst

    s.tick()
    assertEquals(rs.acquired, 1)
    assertEquals(rs.released, 0)
    assertEquals(f.value, None)

    s.tick(1.second)
    assertEquals(rs.released, 1)
    assertEquals(f.value, Some(Success(Some(1))))
  }

  test("inner brackets") { implicit s =>
    val rs = new Semaphore()

    val acquire = Observable.fromTask(rs.acquire)
    val resource = acquire.bracket { _ =>
      acquire.bracket(Observable.pure)(_.release)
    } { h =>
      h.release
    }

    val f = resource
      .mapEval(_ => Task.now(1).delayExecution(1.second))
      .runAsyncGetFirst

    s.tick()
    assertEquals(rs.acquired, 2)
    assertEquals(rs.released, 0)
    assertEquals(f.value, None)

    s.tick(1.second)
    assertEquals(rs.released, 2)
    assertEquals(f.value, Some(Success(Some(1))))
  }

  test("bracket should be cancelable") { implicit s =>
    val rs = new Semaphore()
    var wasCanceled = false

    val obs = Observable.fromTask(rs.acquire).bracketCase(Observable.pure) {
      case (r, ExitCase.Canceled) =>
        Task { wasCanceled = true }.flatMap(_ => r.release)
      case (r, _) =>
        r.release
    }

    val cancelable = obs
      .flatMap(_ => Observable.never)
      .unsafeSubscribeFn(new Subscriber[Handle] {
        implicit val scheduler = s
        def onNext(elem: Handle) =
          Continue
        def onComplete() =
          throw new IllegalStateException("onComplete")
        def onError(ex: Throwable) =
          throw new IllegalStateException("onError")
      })

    s.tick()
    cancelable.cancel()
    s.tick()

    assertEquals(rs.acquired, 1)
    assertEquals(rs.released, 1)
    assert(wasCanceled)
    assert(s.state.tasks.isEmpty, "tasks.isEmpty")
  }

  test("bracket should not be cancelable in its acquire") { implicit s =>
    for (_ <- 0 until 1000) {
      val task = for {
        start <- Deferred.uncancelable[Task, Unit]
        latch <- Deferred[Task, Unit]
        canceled <- Deferred.uncancelable[Task, Unit]
        acquire = start.complete(()) *> latch.get
        obs = Observable.fromTask(acquire).bracketCase(Observable.pure) {
          case (_, ExitCase.Canceled) =>
            canceled.complete(())
          case _ =>
            Task.unit
        }
        fiber <- obs.flatMap(_ => Observable.never[Unit]).completedL.start
        _ <- start.get
        _ <- fiber.cancel.start
        _ <- latch.complete(()).start
        _ <- canceled.get
      } yield ()

      val f = task.runToFuture; s.tick()
      assertEquals(f.value, Some(Success(())))
      assert(s.state.tasks.isEmpty, "tasks.isEmpty")
    }
  }

  test("bracket use is not evaluated on cancel") { implicit sc =>
    import scala.concurrent.duration._
    var use = false
    var release = false

    val task = Observable
      .evalDelayed(2.second, ())
      .bracket(_ => Observable.eval { use = true })(_ => Task { release = true })

    val f = task.completedL.runToFuture
    sc.tick()

    f.cancel()
    sc.tick(2.second)

    assertEquals(f.value, None)
    assertEquals(use, false)
    assertEquals(release, true)
  }

  class Semaphore(var acquired: Int = 0, var released: Int = 0) {
    def acquire: Task[Handle] =
      Task { acquired += 1 }.map(_ => Handle(this))
  }

  case class Handle(r: Semaphore) {
    def release = Task { r.released += 1 }
  }
}
