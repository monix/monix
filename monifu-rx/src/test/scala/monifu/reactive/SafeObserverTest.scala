/*
 * Copyright (c) 2014 by its authors. Some rights reserved.
 * See the project homepage at
 *
 *     http://www.monifu.org/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  	http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package monifu.reactive

import java.util.concurrent.TimeoutException

import monifu.concurrent.extensions._
import monifu.concurrent.{Scheduler, UncaughtExceptionReporter}
import monifu.reactive.Ack.{Cancel, Continue}
import monifu.reactive.observers.SafeObserver
import org.scalatest.FunSpec

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future, Promise}

class SafeObserverTest extends FunSpec {
  describe("SafeObserver") {
    it("should treat immediate errors in onNext") {
      withContext { (scheduler, report) =>
        implicit val s = scheduler

        val p = Promise[Throwable]()
        val obs = SafeObserver(new Observer[Int] {
          def onNext(elem: Int) = throw new DummyException
          def onError(ex: Throwable) = p.success(ex)
          def onComplete() = ()
        })

        val result = obs.onNext(1)
        assert(result === Cancel)

        val ex = Await.result(p.future, 5.seconds)
        assert(ex.isInstanceOf[DummyException], s"$ex.isInstanceOf[DummyException]")

        // no reporting happens
        intercept[TimeoutException] {
          Await.result(report, 100.millis)
        }
      }
    }

    it("should treat sync errors in onNext") {
      withContext { (scheduler, report) =>
        implicit val s = scheduler

        val p = Promise[Throwable]()
        val obs = SafeObserver(new Observer[Int] {
          def onNext(elem: Int) = Future.failed(throw new DummyException)
          def onError(ex: Throwable) = p.success(ex)
          def onComplete() = ()
        })

        val result = obs.onNext(1)
        assert(result === Cancel)

        val ex = Await.result(p.future, 5.seconds)
        assert(ex.isInstanceOf[DummyException], s"$ex.isInstanceOf[DummyException]")

        // no reporting happens
        intercept[TimeoutException] {
          Await.result(report, 100.millis)
        }
      }
    }

    it("should treat async errors in onNext") {
      withContext { (scheduler, report) =>
        implicit val s = scheduler

        val p = Promise[Throwable]()
        val obs = SafeObserver(new Observer[Int] {
          def onNext(elem: Int) = Future.delayedResult(50.millis)(throw new DummyException)
          def onError(ex: Throwable) = p.success(ex)
          def onComplete() = ()
        })

        val result = Await.result(obs.onNext(1), 5.seconds)
        assert(result === Cancel)

        val ex = Await.result(p.future, 5.seconds)
        assert(ex.isInstanceOf[DummyException], s"$ex.isInstanceOf[DummyException]")

        // no reporting happens
        intercept[TimeoutException] {
          Await.result(report, 100.millis)
        }
      }
    }

    it("should catch and report errors happening in onError") {
      withContext { (scheduler, report) =>
        implicit val s = scheduler

        val obs = SafeObserver(new Observer[Int] {
          def onNext(elem: Int) = Future.delayedResult(50.millis)(throw new RuntimeException)

          def onError(ex: Throwable) = throw new DummyException

          def onComplete() = ()
        })

        val result = Await.result(obs.onNext(1), 5.seconds)
        assert(result === Cancel)

        val reported = Await.result(report, 5.seconds)
        assert(reported.isInstanceOf[DummyException], s"$reported.isInstanceOf[DummyException]")
      }
    }

    it("should catch and report errors happening in onComplete") {
      withContext { (scheduler, report) =>
        implicit val s = scheduler

        val obs = SafeObserver(new Observer[Int] {
          def onNext(elem: Int) = Continue
          def onError(ex: Throwable) = ()
          def onComplete() = throw new DummyException
        })

        obs.onComplete()

        val reported = Await.result(report, 5.seconds)
        assert(reported.isInstanceOf[DummyException], s"$reported.isInstanceOf[DummyException]")
      }
    }

    it("should protect synchronous observables") {
      withContext { (scheduler, report) =>
        implicit val s = scheduler
        val p = Promise[Throwable]()

        Observable.unit(1).subscribe(new Observer[Int] {
          def onNext(elem: Int) = throw new DummyException

          def onError(ex: Throwable) = p.success(ex)

          def onComplete() = throw new RuntimeException("onComplete called")
        })

        assert(Await.result(p.future, 5.seconds).isInstanceOf[DummyException],
          "DummyException must be signaled onError")
      }
    }

    it("should protect asynchronous observables when signaling async onNext") {
      withContext { (scheduler, report) =>
        implicit val s = scheduler

        Observable.unit(1).subscribe(new Observer[Int] {
          def onNext(elem: Int) = Future.delayedResult(100.millis)(throw new DummyException)
          def onError(ex: Throwable) = ()
          def onComplete() = ()
        })

        assert(Await.result(report, 5.seconds).isInstanceOf[DummyException],
          "DummyException must be reported")
      }
    }

    it("should protect asynchronous observables when signaling sync onNext") {
      withContext { (scheduler, report) =>
        implicit val s = scheduler

        val p = Promise[Throwable]()
        Observable.unit(1).subscribe(new Observer[Int] {
          def onNext(elem: Int) = throw new DummyException
          def onError(ex: Throwable) = p.success(ex)
          def onComplete() = ()
        })

        assert(Await.result(p.future, 5.seconds).isInstanceOf[DummyException],
          "DummyException must be signaled in onError")
      }
    }
  }

  class DummyException extends RuntimeException

  def withContext[T](f: (Scheduler, Future[Throwable]) => T): T = {
    val p = Promise[Throwable]()
    val s = Scheduler.apply(ExecutionContext.global, new UncaughtExceptionReporter {
      def reportFailure(ex: Throwable) =
        p.success(ex)
    })

    f(s, p.future)
  }
}
