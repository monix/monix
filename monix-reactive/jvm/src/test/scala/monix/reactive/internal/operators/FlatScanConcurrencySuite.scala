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

package monix.reactive.internal.operators

import monix.eval.Task
import monix.execution.Cancelable
import monix.execution.cancelables.BooleanCancelable
import monix.reactive.{ BaseConcurrencySuite, Observable }

import scala.concurrent.duration._
import scala.concurrent.{ Await, Future, Promise }

object FlatScanConcurrencySuite extends BaseConcurrencySuite {
  val cancelTimeout = 3.minutes
  val cancelIterations = 1000

  test("flatScan should work for synchronous children") { implicit s =>
    val count = 10000L
    val expected = 3L * count * (count - 1) / 2

    for (_ <- 0 until 100) {
      val sum = Observable
        .range(0, count)
        .flatScan(0L)((_, x) => Observable(x, x, x))
        .sumL
        .runToFuture

      val result = Await.result(sum, 30.seconds)
      assertEquals(result, expected)
    }
  }

  test("flatScan should work for asynchronous children") { implicit s =>
    val count = 10000L
    val expected = 3L * count * (count - 1) / 2

    for (_ <- 0 until 100) {
      val sum = Observable
        .range(0, count)
        .flatScan(0L)((_, x) => Observable(x, x, x).executeAsync)
        .sumL
        .runToFuture

      val result = Await.result(sum, 30.seconds)
      assertEquals(result, expected)
    }
  }

  test(s"flatScan should be cancellable, test 1, count $cancelIterations (issue #468)") { implicit s =>
    def never(): (Future[Unit], Observable[Int]) = {
      val isCancelled = Promise[Unit]()
      val ref = Observable.unsafeCreate[Int] { _ =>
        Cancelable { () => isCancelled.success(()); () }
      }
      (isCancelled.future, ref)
    }

    for (i <- 0 until cancelIterations) {
      val (isCancelled, ref) = never()
      val c = Observable(1).flatScan(0)((_, _) => ref).subscribe()

      // Creating race condition
      if (i % 2 == 0) {
        s.execute(() => c.cancel())
      } else {
        c.cancel()
      }
      Await.result(isCancelled, cancelTimeout)
    }
  }

  test(s"flatScan should be cancellable, test 2, count $cancelIterations (issue #468)") { implicit s =>
    def one(p: Promise[Unit])(acc: Long, x: Long): Observable[Long] =
      Observable.unsafeCreate { sub =>
        val ref = BooleanCancelable { () => p.trySuccess(()); () }
        sub.scheduler.execute { () =>
          if (!ref.isCanceled) {
            Observable.now(x).unsafeSubscribeFn(sub)
            ()
          }
        }
        ref
      }

    for (_ <- 0 until cancelIterations) {
      val p = Promise[Unit]()
      val c = Observable
        .range(0, Long.MaxValue)
        .executeAsync
        .uncancelable
        .doOnError(e => Task { p.tryFailure(e); () })
        .doOnComplete(Task { p.tryFailure(new IllegalStateException("complete")); () })
        .doOnEarlyStop(Task { p.trySuccess(()); () })
        .flatScan(0L)(one(p))
        .subscribe()

      // Creating race condition
      s.execute(() => c.cancel())
      Await.result(p.future, cancelTimeout)
    }
  }

  test(s"flatScan should be cancellable, test 3, count $cancelIterations (issue #468)") { implicit s =>
    for (_ <- 0 until cancelIterations) {
      val p = Promise[Unit]()
      val c = Observable
        .range(0, Long.MaxValue)
        .executeAsync
        .uncancelable
        .doOnError(e => Task { p.tryFailure(e); () })
        .doOnComplete(Task { p.tryFailure(new IllegalStateException("complete")); () })
        .doOnEarlyStop(Task { p.trySuccess(()); () })
        .flatScan(0L)((_, x) => Observable.now(x).executeAsync)
        .subscribe()

      // Creating race condition
      s.execute(() => c.cancel())
      Await.result(p.future, cancelTimeout)
    }
  }
}
