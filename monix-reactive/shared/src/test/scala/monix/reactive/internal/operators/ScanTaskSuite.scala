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

import java.util.concurrent.TimeUnit

import monix.eval.Task
import monix.execution.exceptions.DummyException
import monix.reactive.Observable

import scala.concurrent.duration._
import scala.util.Failure

object ScanTaskSuite extends BaseOperatorSuite {
  def createObservable(sourceCount: Int) = Some {
    val o = Observable.range(0, sourceCount).scanTask(Task.now(0L)) {
      (s, x) => if (x % 2 == 0) Task(s + x) else Task.eval(s + x)
    }

    Sample(o, count(sourceCount), sum(sourceCount), waitFirst, waitNext)
  }

  def count(sourceCount: Int) =
    sourceCount
  def sum(sourceCount: Int) =
    0.until(sourceCount).scan(0)(_ + _).sum

  def waitFirst = Duration.Zero
  def waitNext = Duration.Zero

  def observableInError(sourceCount: Int, ex: Throwable) =
    if (sourceCount == 1) None else Some {
      val o = createObservableEndingInError(Observable.range(0, sourceCount), ex)
        .scanTask(Task.now(0L)) {
          (s, x) => if (x % 2 == 0) Task(s + x) else Task.eval(s + x)
        }

      Sample(o, count(sourceCount), sum(sourceCount), waitFirst, waitNext)
    }

  def brokenUserCodeObservable(sourceCount: Int, ex: Throwable) = Some {
    val o = Observable.range(0, sourceCount)
      .scanTask(Task.now(0L)) { (s, i) =>
        if (i == sourceCount-1)
          throw ex
        else if (i % 2 == 0)
          Task(s + i)
        else
          Task.eval(s + i)
      }

    Sample(o, count(sourceCount-1), sum(sourceCount-1), waitFirst, waitNext)
  }

  override def cancelableObservables(): Seq[Sample] = {
    val sample1 =  Observable.range(1, 100)
      .scanTask(Task.now(0L))((s, i) => Task.eval(s + i).delayExecution(1.second))
    val sample2 = Observable.range(0, 100)
      .delayOnNext(1.second)
      .scanTask(Task.now(0L))((s, i) => Task.eval(s + i).delayExecution(2.second))

    Seq(
      Sample(sample1, 0, 0, 0.seconds, 0.seconds),
      Sample(sample1, 1, 1, 1.seconds, 0.seconds),
      Sample(sample2, 0, 0, 0.seconds, 0.seconds),
      Sample(sample2, 0, 0, 1.seconds, 0.seconds),
      Sample(sample2, 0, 0, 2.seconds, 0.seconds),
      Sample(sample2, 1, 1, 3.seconds, 0.seconds)
    )
  }

  test("should protect against errors in seed") { implicit s =>
    val dummy = DummyException("dummy")
    var effect = 0

    val obs = Observable.range(0, 100)
      .doOnTerminate(_ => { effect += 1 })
      .scanTask(Task.raiseError[Long](dummy))((s, a) => Task(s + a))
      .doOnError(_ => { effect += 1 })
      .lastL

    val f = obs.runAsync; s.tick()
    assertEquals(f.value, Some(Failure(dummy)))
    assertEquals(effect, 1)
  }

  test("should protect against exceptions thrown in op") { implicit s =>
    val dummy = DummyException("dummy")
    var effect = 0

    val obs = Observable.range(0, 100)
      .doOnTerminate(_ => { effect += 1 })
      .scanTask(Task.now(0))((_, _) => throw dummy)
      .doOnError(_ => { effect += 1 })
      .lastL

    val f = obs.runAsync; s.tick()
    assertEquals(f.value, Some(Failure(dummy)))
    assertEquals(effect, 2)
  }

  test("should protect against errors raised in op") { implicit s =>
    val dummy = DummyException("dummy")
    var effect = 0

    val obs = Observable.range(0, 100)
      .doOnTerminate(_ => { effect += 1 })
      .scanTask(Task.now(0))((_, _) => Task.raiseError(dummy))
      .doOnError(_ => { effect += 1 })
      .lastL

    val f = obs.runAsync; s.tick()
    assertEquals(f.value, Some(Failure(dummy)))
    assertEquals(effect, 2)
  }

  test("back-pressure with onError") { implicit s =>
    val dummy = DummyException("dummy")
    var sum = 0
    var effect = 0

    val f = Observable.now(10).endWithError(dummy)
      .doOnError { _ => effect += 1 }
      .scanTask(Task.now(11))((s, a) => Task(s + a).delayExecution(1.second))
      .doOnNext { x => sum += x }
      .doOnError { _ => effect += 1 }
      .lastL
      .runAsync

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

  test("onError from source + error in task") { implicit s =>
    val dummy1 = DummyException("dummy1")
    val dummy2 = DummyException("dummy2")
    var effect = 0

    val f = Observable.now(10).endWithError(dummy1)
      .doOnError { _ => effect += 1 }
      .scanTask(Task.now(0))((_, _) => Task.raiseError[Int](dummy2).delayExecution(1.second))
      .doOnError { _ => effect += 1 }
      .lastL
      .runAsync

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

  test("error in task after user cancelled") { implicit s =>
    def delay[A](ex: Throwable): Task[A] =
      Task.unsafeCreate[A] { (ctx, cb) =>
        ctx.scheduler.scheduleOnce(1, TimeUnit.SECONDS, new Runnable {
          def run() = cb.onError(ex)
        })
      }

    val dummy = DummyException("dummy")
    var effect = 0

    val f = Observable.now(10)
      .doOnNext { _ => effect += 1 }
      .scanTask(Task.now(0))((_, _) => delay[Int](dummy))
      .doOnError { _ => effect += 1 }
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
}
