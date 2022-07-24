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

import java.util.concurrent.TimeUnit

import cats.effect.IO
import cats.laws._
import cats.laws.discipline._
import monix.eval.Task
import monix.execution.exceptions.DummyException
import monix.reactive.Observable

import scala.concurrent.duration._
import scala.util.Failure

class ScanTaskSuite extends BaseOperatorSuite {
  def createObservable(sourceCount: Int) = Some {
    val o = Observable.range(0L, sourceCount.toLong).scanEval(Task.now(0L)) { (s, x) =>
      if (x % 2 == 0) Task.evalAsync(s + x) else Task.eval(s + x)
    }

    Sample(o, count(sourceCount), sum(sourceCount), waitFirst, waitNext)
  }

  def count(sourceCount: Int) =
    sourceCount
  def sum(sourceCount: Int) =
    0.until(sourceCount).scanLeft(0)(_ + _).sum

  def waitFirst = Duration.Zero
  def waitNext = Duration.Zero

  def observableInError(sourceCount: Int, ex: Throwable) =
    if (sourceCount == 1) None
    else
      Some {
        val o = createObservableEndingInError(Observable.range(0L, sourceCount.toLong), ex)
          .scanEval(Task.now(0L)) { (s, x) =>
            if (x % 2 == 0) Task.evalAsync(s + x) else Task.eval(s + x)
          }

        Sample(o, count(sourceCount), sum(sourceCount), waitFirst, waitNext)
      }

  def brokenUserCodeObservable(sourceCount: Int, ex: Throwable) = Some {
    val o = Observable
      .range(0L, sourceCount.toLong)
      .scanEval(Task.now(0L)) { (s, i) =>
        if (i == sourceCount - 1)
          throw ex
        else if (i % 2 == 0)
          Task.evalAsync(s + i)
        else
          Task.eval(s + i)
      }

    Sample(o, count(sourceCount - 1), sum(sourceCount - 1), waitFirst, waitNext)
  }

  override def cancelableObservables(): Seq[Sample] = {
    val sample1 = Observable
      .range(1, 100)
      .scanEval(Task.now(0L))((s, i) => Task.eval(s + i).delayExecution(1.second))
    val sample2 = Observable
      .range(0, 100)
      .delayOnNext(1.second)
      .scanEval(Task.now(0L))((s, i) => Task.eval(s + i).delayExecution(2.second))

    Seq(
      Sample(sample1, 0, 0, 0.seconds, 0.seconds),
      Sample(sample1, 1, 1, 1.seconds, 0.seconds),
      Sample(sample2, 0, 0, 0.seconds, 0.seconds),
      Sample(sample2, 0, 0, 1.seconds, 0.seconds),
      Sample(sample2, 0, 0, 2.seconds, 0.seconds),
      Sample(sample2, 1, 1, 3.seconds, 0.seconds)
    )
  }

  fixture.test("should protect against errors in seed") { implicit s =>
    val dummy = DummyException("dummy")
    var effect = 0

    val obs = Observable
      .range(0, 100)
      .guarantee(Task { effect += 1 })
      .scanEval(Task.raiseError[Long](dummy))((s, a) => Task.evalAsync(s + a))
      .doOnError(_ => Task { effect += 1 })
      .lastL

    val f = obs.runToFuture; s.tick()
    assertEquals(f.value, Some(Failure(dummy)))
    assertEquals(effect, 1)
  }

  fixture.test("should protect against exceptions thrown in op") { implicit s =>
    val dummy = DummyException("dummy")
    var effect = 0

    val obs = Observable
      .range(0, 100)
      .guaranteeF(IO { effect += 1 })
      .scanEval(Task.now(0))((_, _) => throw dummy)
      .doOnErrorF(_ => IO { effect += 1 })
      .lastL

    val f = obs.runToFuture; s.tick()
    assertEquals(f.value, Some(Failure(dummy)))
    assertEquals(effect, 2)
  }

  fixture.test("should protect against errors raised in op") { implicit s =>
    val dummy = DummyException("dummy")
    var effect = 0

    val obs = Observable
      .range(0, 100)
      .guaranteeF(IO { effect += 1 })
      .scanEval(Task.now(0))((_, _) => Task.raiseError(dummy))
      .doOnErrorF(_ => IO { effect += 1 })
      .lastL

    val f = obs.runToFuture; s.tick()
    assertEquals(f.value, Some(Failure(dummy)))
    assertEquals(effect, 2)
  }

  fixture.test("back-pressure with onError") { implicit s =>
    val dummy = DummyException("dummy")
    var sum = 0
    var effect = 0

    val f = Observable
      .now(10)
      .endWithError(dummy)
      .doOnErrorF(_ => IO { effect += 1 })
      .scanEval(Task.now(11))((s, a) => Task.evalAsync(s + a).delayExecution(1.second))
      .doOnNextF { x =>
        IO { sum += x }
      }
      .doOnErrorF { _ =>
        IO { effect += 1 }
      }
      .lastL
      .runToFuture

    assertEquals(effect, 1)
    assertEquals(sum, 0)
    assertEquals(f.value, None)

    s.tick()
    assertEquals(effect, 1)
    assertEquals(sum, 0)
    assertEquals(f.value, None)

    s.tick(1.second)
    assertEquals(effect, 2)
    assertEquals(sum, 21)
    assertEquals(f.value, Some(Failure(dummy)))
  }

  fixture.test("onError from source + error in task") { implicit s =>
    val dummy1 = DummyException("dummy1")
    val dummy2 = DummyException("dummy2")
    var effect = 0

    val f = Observable
      .now(10)
      .endWithError(dummy1)
      .doOnErrorF { _ =>
        IO { effect += 1 }
      }
      .scanEval(Task.now(0))((_, _) => Task.raiseError[Int](dummy2).delayExecution(1.second))
      .doOnErrorF { _ =>
        IO { effect += 1 }
      }
      .lastL
      .runToFuture

    assertEquals(effect, 1)
    assertEquals(f.value, None)

    s.tick()
    assertEquals(effect, 1)
    assertEquals(f.value, None)

    s.tick(1.second)
    assertEquals(effect, 2)
    assertEquals(f.value, Some(Failure(dummy2)))
    assertEquals(s.state.lastReportedError, dummy1)
  }

  fixture.test("error in task after user cancelled") { implicit s =>
    def delay[A](ex: Throwable): Task[A] =
      Task.async0 { (sc, cb) =>
        sc.scheduleOnce(1, TimeUnit.SECONDS, () => cb.onError(ex))
        ()
      }

    val dummy = DummyException("dummy")
    var effect = 0

    val f = Observable
      .now(10)
      .doOnNextF { _ =>
        IO { effect += 1 }
      }
      .scanEval(Task.now(0))((_, _) => delay[Int](dummy))
      .doOnErrorF { _ =>
        IO { effect += 1 }
      }
      .runAsyncGetLast

    s.tick()
    assertEquals(effect, 1)
    assertEquals(f.value, None)

    f.cancel()
    s.tick()

    assertEquals(effect, 1)
    assertEquals(f.value, None)
    assert(s.state.tasks.nonEmpty, "tasks.nonEmpty")
    assertEquals(s.state.lastReportedError, null)

    s.tick(1.second)
    assertEquals(effect, 1)
    assertEquals(f.value, None)
    assert(s.state.tasks.isEmpty, "tasks.isEmpty")
    assertEquals(s.state.lastReportedError, dummy)
  }

  fixture.test("scanTask0.headL <-> seed") { implicit s =>
    check2 { (obs: Observable[Int], seed: Task[Int]) =>
      obs.scanEval0(seed)((a, b) => Task.pure(a + b)).headL <-> seed
    }
  }

  fixture.test("scanTask0.drop(1) <-> scanTask") { implicit s =>
    check2 { (obs: Observable[Int], seed: Task[Int]) =>
      obs.scanEval0(seed)((a, b) => Task.pure(a + b)).drop(1) <-> obs.scanEval(seed)((a, b) => Task.pure(a + b))
    }
  }
}
