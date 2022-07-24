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

import monix.execution.Ack.{ Continue, Stop }
import monix.execution.exceptions.DummyException
import monix.execution.internal.Platform
import scala.concurrent.{ Future, Promise }
import scala.util.{ Failure, Success, Try }

class AckSuite extends BaseTestSuite {

  val stackSafeLoopN: Int = if (Platform.isJVM) 100000 else 5000

  test("Continue defaults") {
    assert(Continue.isCompleted, "Continue.isCompleted")
    assertEquals(Continue.value, Some(Success(Continue)))
  }

  test("Stop defaults") {
    assert(Stop.isCompleted, "Stop.isCompleted")
    assertEquals(Stop.value, Some(Success(Stop)))
  }

  fixture.test("syncOnContinue(Continue) should execute synchronously #1") { implicit s =>
    var triggered = false
    Continue.syncOnContinue { triggered = true }
    assert(triggered, "triggered")
  }

  fixture.test("syncOnContinue(Continue) should execute synchronously #2") { implicit s =>
    val continue: Future[Ack] = Continue
    var triggered = false
    val trigger: () => Unit = {
      val value = true
      () => triggered = value
    }

    continue.syncOnContinue(trigger())
    assert(triggered, "triggered")
  }

  fixture.test("syncOnContinue(Future.successful(Continue)) should execute trampolined") { implicit s =>
    def loop(source: Future[Ack], n: Int): Future[Ack] =
      source.syncOnContinue {
        if (n > 0) { loop(source, n - 1); () }
      }

    var triggered = false
    val continue: Future[Ack] = Future.successful(Continue)
    loop(continue, stackSafeLoopN).syncOnContinue { triggered = true }
    assert(triggered, "triggered")
  }

  fixture.test("syncOnContinue(Future(Continue)) should execute async") { implicit s =>
    var triggered = false
    val continue: Future[Ack] = Future(Continue)
    continue.syncOnContinue { triggered = true }

    assert(!triggered, "!triggered")
    s.tick()
    assert(triggered, "triggered")
  }

  fixture.test("syncOnContinue(Stop) should execute synchronously #1") { implicit s =>
    var triggered = false
    (Stop: Future[Ack]).syncOnContinue { triggered = true }
    assert(!triggered, "!triggered")
    assert(s.state.tasks.isEmpty, "there should be no async task registered")
  }

  fixture.test("syncOnContinue(Stop) should execute synchronously #2") { implicit s =>
    val cancel: Future[Ack] = Stop
    var triggered = false
    val trigger: () => Unit = {
      val value = true
      () => triggered = value
    }

    cancel.syncOnContinue(trigger())
    assert(!triggered, "!triggered")
    assert(s.state.tasks.isEmpty, "there should be no async task registered")
  }

  fixture.test("syncOnContinue(Future.successful(Stop)) should execute trampolined") { implicit s =>
    var triggered = false
    val stop = Future.successful(Stop: Ack)
    stop.syncOnContinue { triggered = true }
    assert(!triggered, "!triggered")
    assert(s.state.tasks.isEmpty, "tasks.isEmpty")
  }

  fixture.test("syncOnContinue(Future(Stop)) should execute async") { implicit s =>
    var triggered = false
    Future(Stop: Ack).syncOnContinue { triggered = true }
    assert(s.state.tasks.nonEmpty, "async tasks should be registered")
    s.tick()
    assert(!triggered, "!triggered")
  }

  fixture.test("syncOnContinue(Continue) should protect against user errors") { implicit s =>
    val ex = new RuntimeException("dummy")
    Continue.syncOnContinue { throw ex }
    assertEquals(s.state.lastReportedError, ex)
  }

  fixture.test("syncOnContinue(Future.successful(Continue)) should protect against user errors") { implicit s =>
    val ex = new RuntimeException("dummy")
    Future.successful(Continue).syncOnContinue { throw ex }
    assertEquals(s.state.lastReportedError, ex)
  }

  fixture.test("syncOnStopOrFailure(Stop) should execute synchronously") { implicit s =>
    var triggered = false
    Stop.syncOnStopOrFailure { ex =>
      if (ex.isEmpty) triggered = true
    }
    assert(triggered, "triggered")
    assert(s.state.tasks.isEmpty, "there should be no async tasks registered")
  }

  fixture.test("syncOnStopOrFailure(Future(Stop)) should execute asynchronously") { implicit s =>
    var triggered = false
    Future(Stop).syncOnStopOrFailure { ex =>
      if (ex.isEmpty) triggered = true
    }
    assert(!triggered, "!triggered")

    s.tick()
    assert(triggered, "triggered")
    assert(s.state.tasks.isEmpty, "there should be no async tasks registered")
  }

  fixture.test("syncOnStopOrFailure(Future.successful(Stop)) should execute trampolined") { implicit s =>
    def loop(source: Future[Ack], n: Int): Future[Ack] =
      source.syncOnStopOrFailure { _ =>
        if (n > 0) { loop(source, n - 1); () }
      }

    var triggered = false
    loop(Future.successful(Stop), stackSafeLoopN).syncOnStopOrFailure { ex =>
      triggered = ex.isEmpty
    }
    assert(triggered, "triggered")
  }

  fixture.test("syncOnStopOrFailure(Continue) should execute synchronously") { implicit s =>
    var triggered = false
    (Continue: Ack).syncOnStopOrFailure { ex =>
      if (ex.isEmpty) triggered = true
    }
    assert(!triggered, "!triggered")
    assert(s.state.tasks.isEmpty, "there should be no async tasks registered")
  }

  fixture.test("syncOnStopOrFailure(Future.successful(Continue)) should execute trampolined") { implicit s =>
    var triggered = false
    (Future.successful(Continue): Future[Ack]).syncOnStopOrFailure { ex =>
      triggered = ex.isEmpty
    }
    assert(!triggered, "!triggered")
    assert(s.state.tasks.isEmpty, "there should be async tasks registered")
  }

  fixture.test("syncOnStopOrFailure(Future(Continue)) should execute asynchronously") { implicit s =>
    var triggered = false
    (Future(Continue): Future[Ack]).syncOnStopOrFailure { ex =>
      if (ex.isEmpty) triggered = true
    }
    assert(s.state.tasks.nonEmpty, "there should be async tasks registered")

    s.tick()
    assert(!triggered, "!triggered")
    assert(s.state.tasks.isEmpty, "tasks.isEmpty")
  }

  fixture.test("syncOnStopOrFailure(Future.failed(ex)) should execute trampolined") { implicit s =>
    var triggered = false
    val ex = new RuntimeException("dummy")
    val ack: Future[Ack] = Future.failed(ex)
    ack.syncOnStopOrFailure { p =>
      triggered = p.fold(triggered)(_ == ex)
    }
    assertEquals(triggered, true)
  }

  fixture.test("syncOnStopOrFailure(Future(throw ex)) should execute") { implicit s =>
    var triggered = false
    val ex = new RuntimeException("dummy")
    val ack: Future[Ack] = Future { throw ex }
    ack.syncOnStopOrFailure { p =>
      triggered = p.fold(triggered)(_ == ex)
    }

    assertEquals(triggered, false)
    assert(s.state.tasks.nonEmpty, "there should be async tasks registered")
    s.tick()
    assertEquals(triggered, true)
  }

  fixture.test("syncOnStopOrFailure(Stop) should protect against user errors") { implicit s =>
    val ex = new RuntimeException("dummy")
    Stop.syncOnStopOrFailure { _ =>
      throw ex
    }
    assertEquals(s.state.lastReportedError, ex)
  }

  fixture.test("syncOnStopOrFailure(Future(Stop)) should protect against user errors") { implicit s =>
    val ex = new RuntimeException("dummy")
    Future(Stop).syncOnStopOrFailure { _ =>
      throw ex
    }
    s.tick()
    assertEquals(s.state.lastReportedError, ex)
  }

  fixture.test("syncOnStopOrFailure(Future.failed(ex)) should protect against user errors") { implicit s =>
    val ex = new RuntimeException("dummy")
    val source = Future.failed[Ack](new RuntimeException("first"))
    source.syncOnStopOrFailure { _ =>
      throw ex
    }
    assertEquals(s.state.lastReportedError, ex)
  }

  fixture.test("syncOnStopOrFailure(Future(throw ex)) should protect against user errors") { implicit s =>
    val ex = new RuntimeException("dummy")
    val source = Future[Ack](throw new RuntimeException("first"))
    source.syncOnStopOrFailure { _ =>
      throw ex
    }
    s.tick()
    assertEquals(s.state.lastReportedError, ex)
  }

  fixture.test("syncMap(Continue) should execute synchronously") { implicit s =>
    var triggered = false
    val source: Future[Ack] = Continue

    val result = source.syncMap {
      case Stop => Stop
      case Continue =>
        triggered = true
        Stop
    }

    assertEquals(result, Stop)
    assertEquals(triggered, true)
    assert(s.state.tasks.isEmpty, "there should be no async tasks registered")
  }

  fixture.test("syncMap(Stop) should execute synchronously") { implicit s =>
    var triggered = false
    val source: Future[Ack] = Stop

    val result = source.syncMap {
      case Continue => Continue
      case Stop =>
        triggered = true
        Continue
    }

    assertEquals(result, Continue)
    assertEquals(triggered, true)
    assert(s.state.tasks.isEmpty, "there should be no async tasks registered")
  }

  fixture.test("syncMap should protect against exceptions") { implicit s =>
    val dummy = new RuntimeException("dummy")
    val result = (Continue: Future[Ack]).syncMap { _ =>
      throw dummy
    }

    assertEquals(result, Stop)
    assertEquals(s.state.lastReportedError, dummy)
  }

  fixture.test("syncMap(Future.successful(Continue)) should execute trampolined") { implicit s =>
    var triggered = false
    val source: Future[Ack] = Future.successful(Continue)

    val result = source.syncMap {
      case Stop => Stop
      case Continue =>
        triggered = true
        Stop
    }

    assert(s.state.tasks.isEmpty)
    assertEquals(result.syncTryFlatten, Stop)
    assertEquals(triggered, true)
  }

  fixture.test("syncMap(Future.successful(Stop)) should execute trampolined") { implicit s =>
    var triggered = false
    val source: Future[Ack] = Future.successful(Stop)

    val result = source.syncMap {
      case Continue => Continue
      case Stop =>
        triggered = true
        Continue
    }

    assert(s.state.tasks.isEmpty)
    assertEquals(result.syncTryFlatten, Continue)
    assertEquals(triggered, true)
  }

  fixture.test("syncMap(Continue) with impure function") { implicit s =>
    var triggered = false
    val source: Future[Ack] = Continue
    val fn: Ack => Ack = {
      val value = true
      (ack: Ack) =>
        ack match {
          case Stop => Stop
          case Continue =>
            triggered = value
            Stop
        }
    }

    val result = source.syncMap(fn)
    assertEquals(triggered, true)
    assertEquals(result, Stop)
  }

  fixture.test("syncMap(Future(Continue)) with impure function") { implicit s =>
    var triggered = false
    val source: Future[Ack] = Future(Continue)
    val fn: Ack => Ack = {
      val value = true
      (ack: Ack) =>
        ack match {
          case Stop => Stop
          case Continue =>
            triggered = value
            Stop
        }
    }

    val result = source.syncMap(fn)

    s.tick()
    assertEquals(triggered, true)
    assertEquals(result.syncTryFlatten, Stop)
  }

  fixture.test("syncFlatMap(Continue) should execute synchronously") { implicit s =>
    var triggered = false
    val source: Future[Ack] = Continue

    val result = source.syncFlatMap {
      case Stop => Stop
      case Continue =>
        triggered = true
        Stop
    }

    assertEquals(result, Stop)
    assertEquals(triggered, true)
    assert(s.state.tasks.isEmpty, "there should be no async tasks registered")
  }

  fixture.test("syncFlatMap(Stop) should execute synchronously") { implicit s =>
    var triggered = false
    val source: Future[Ack] = Stop

    val result = source.syncFlatMap {
      case Continue => Continue
      case Stop =>
        triggered = true
        Continue
    }

    assertEquals(result, Continue)
    assertEquals(triggered, true)
    assert(s.state.tasks.isEmpty, "there should be no async tasks registered")
  }

  fixture.test("syncFlatMap should protect against exceptions") { implicit s =>
    val dummy = new RuntimeException("dummy")
    val result = (Continue: Future[Ack]).syncFlatMap { _ =>
      throw dummy
    }

    assertEquals(result, Stop)
    assertEquals(s.state.lastReportedError, dummy)
  }

  fixture.test("syncFlatMap(Future.successful(Continue)) should execute trampolined") { implicit s =>
    def loop(f: Future[Ack], n: Int): Future[Ack] =
      if (n > 0) f.syncFlatMap(_ => loop(f, n - 1))
      else f

    var triggered = false
    val source: Future[Ack] = Future.successful(Continue)

    val result = loop(source, stackSafeLoopN).syncFlatMap {
      case Stop => Stop
      case Continue =>
        triggered = true
        Stop
    }

    assert(s.state.tasks.isEmpty)
    assertEquals(result.syncTryFlatten, Stop)
    assertEquals(triggered, true)
  }

  fixture.test("syncFlatMap(Future.successful(Stop)) should execute trampolined") { implicit s =>
    def loop(f: Future[Ack], n: Int): Future[Ack] =
      if (n > 0) f.syncFlatMap(_ => loop(f, n - 1))
      else f

    var triggered = false
    val source: Future[Ack] = Future.successful(Stop)

    val result = loop(source, stackSafeLoopN).syncFlatMap {
      case Continue => Continue
      case Stop =>
        triggered = true
        Continue
    }

    assert(s.state.tasks.isEmpty)
    assertEquals(result.syncTryFlatten, Continue)
    assertEquals(triggered, true)
  }

  fixture.test("syncFlatMap(Continue) with impure function") { implicit s =>
    var triggered = false
    val source: Future[Ack] = Continue
    val fn: Ack => Ack = {
      val value = true
      (ack: Ack) =>
        ack match {
          case Stop => Stop
          case Continue =>
            triggered = value
            Stop
        }
    }

    val result = source.syncFlatMap(fn)
    assertEquals(triggered, true)
    assertEquals(result, Stop)
  }

  fixture.test("syncFlatMap(Future(Continue)) with impure function") { implicit s =>
    var triggered = false
    val source: Future[Ack] = Future(Continue)
    val fn: Ack => Ack = {
      val value = true
      (ack: Ack) =>
        ack match {
          case Stop => Stop
          case Continue =>
            triggered = value
            Stop
        }
    }

    val result = source.syncFlatMap(fn)

    s.tick()
    assertEquals(triggered, true)
    assertEquals(result.syncTryFlatten, Stop)
  }

  fixture.test("syncTryFlatten(Continue)") { implicit s =>
    val f = Continue.syncTryFlatten
    assertEquals(f, Continue)
  }

  fixture.test("syncTryFlatten(Stop)") { implicit s =>
    val f = Stop.syncTryFlatten
    assertEquals(f, Stop)
  }

  fixture.test("syncTryFlatten(Future.successful(Continue))") { implicit s =>
    val f = Future.successful(Continue).syncTryFlatten
    assertEquals(f, Continue)
  }

  fixture.test("syncTryFlatten(Future(Continue))") { implicit s =>
    val source = Future(Continue)
    val f = source.syncTryFlatten
    assertEquals(f, source)
    s.tick()
  }

  fixture.test("syncTryFlatten(Stop)") { implicit s =>
    val f = Stop.syncTryFlatten
    assertEquals(f, Stop)
  }

  fixture.test("syncTryFlatten(Future.successful(Stop))") { implicit s =>
    val f = Future.successful(Stop).syncTryFlatten
    assertEquals(f, Stop)
  }

  fixture.test("syncTryFlatten(Future(Stop))") { implicit s =>
    val source = Future(Stop)
    val f = source.syncTryFlatten
    assertEquals(f, source)
    s.tick()
  }

  fixture.test("isSynchronous(Future(Continue)) == false") { _ =>
    val f: Future[Ack] = Future.successful(Continue)
    assert(!f.isSynchronous)
  }

  fixture.test("isSynchronous(Continue) == true") { _ =>
    val f: Future[Ack] = Continue
    assert(f.isSynchronous)
  }

  fixture.test("isSynchronous(Future(Stop)) == false") { _ =>
    val f: Future[Ack] = Future.successful(Stop)
    assert(!f.isSynchronous)
  }

  fixture.test("isSynchronous(Stop) == true") { _ =>
    val f: Future[Ack] = Stop
    assert(f.isSynchronous)
  }

  fixture.test("isSynchronous(failure) == false") { _ =>
    val f: Future[Ack] = Future.failed(new RuntimeException)
    assert(!f.isSynchronous)
  }

  fixture.test("isSynchronous(impure Future(Continue)) == false") { _ =>
    def f: Future[Ack] = Future.successful(Continue)
    assert(!f.isSynchronous)
  }

  fixture.test("isSynchronous(impure Continue) == true") { _ =>
    def f: Future[Ack] = Continue
    assert(f.isSynchronous)
  }

  fixture.test("isSynchronous(impure Future(Stop)) == false") { _ =>
    def f: Future[Ack] = Future.successful(Stop)
    assert(!f.isSynchronous)
  }

  fixture.test("isSynchronous(impure Stop) == true") { _ =>
    def f: Future[Ack] = Stop
    assert(f.isSynchronous)
  }

  fixture.test("isSynchronous(impure failure) == false") { _ =>
    def f: Future[Ack] = Future.failed(new RuntimeException)
    assert(!f.isSynchronous)
  }

  fixture.test("continue.syncOnComplete(clean)") { implicit s =>
    val ack: Ack = Continue
    var triggered = false

    ack.syncOnComplete {
      case Success(Continue) => triggered = true
      case _ => ()
    }

    assert(triggered, "should have been sync")
  }

  fixture.test("stop.syncOnComplete(clean)") { implicit s =>
    val ack: Ack = Stop
    var triggered = false

    ack.syncOnComplete {
      case Success(Stop) => triggered = true
      case _ => ()
    }

    assert(triggered, "should have been sync")
  }

  fixture.test("continue.syncOnComplete(unclean)") { implicit s =>
    val ack: Ack = Continue
    var triggered = false

    val fn = (x: Try[Ack]) =>
      x match {
        case Success(Continue) => triggered = true
        case _ => ()
      }

    ack.syncOnComplete(fn)
    assert(triggered, "should have been sync")
  }

  fixture.test("stop.syncOnComplete(unclean)") { implicit s =>
    val ack: Ack = Stop
    var triggered = false

    val fn = (x: Try[Ack]) =>
      x match {
        case Success(Stop) => triggered = true
        case _ => ()
      }

    ack.syncOnComplete(fn)
    assert(triggered, "should have been sync")
  }

  fixture.test("future(continue).syncOnComplete(clean)") { implicit s =>
    val ack: Future[Ack] = Future(Continue)
    var triggered = false

    ack.syncOnComplete {
      case Success(Continue) => triggered = true
      case _ => ()
    }

    assert(!triggered, "!triggered")
    s.tick()
    assert(triggered, "should have been async")
  }

  fixture.test("future(stop).syncOnComplete(clean)") { implicit s =>
    val ack: Future[Ack] = Future(Stop)
    var triggered = false

    ack.syncOnComplete {
      case Success(Stop) => triggered = true
      case _ => ()
    }

    assert(!triggered, "!triggered")
    s.tick()
    assert(triggered, "should have been async")
  }

  fixture.test("future(continue).syncOnComplete(unclean)") { implicit s =>
    val ack: Future[Ack] = Future(Continue)
    var triggered = false

    val fn = (x: Try[Ack]) =>
      x match {
        case Success(Continue) => triggered = true
        case _ => ()
      }

    ack.syncOnComplete(fn)

    assert(!triggered, "!triggered")
    s.tick()
    assert(triggered, "should have been async")
  }

  fixture.test("future(stop).syncOnComplete(unclean)") { implicit s =>
    val ack: Future[Ack] = Future(Stop)
    var triggered = false

    val fn = (x: Try[Ack]) =>
      x match {
        case Success(Stop) => triggered = true
        case _ => ()
      }

    ack.syncOnComplete(fn)

    assert(!triggered, "!triggered")
    s.tick()
    assert(triggered, "should have been async")
  }

  fixture.test("syncOnComplete protects against immediate errors") { implicit s =>
    val dummy = new DummyException("dummy")
    Continue.syncOnComplete { _ =>
      throw dummy
    }
    assertEquals(s.state.lastReportedError, dummy)
  }

  fixture.test("syncOnComplete protects against async errors") { implicit s =>
    val dummy = new DummyException("dummy")
    Future(Continue).syncOnComplete { _ =>
      throw dummy
    }
    s.tick()
    assertEquals(s.state.lastReportedError, dummy)
  }

  fixture.test("Continue.syncOnContinueFollow") { _ =>
    val ack: Future[Ack] = Continue
    val p = Promise[Int]()

    ack.syncOnContinueFollow(p, 1)
    // should be immediate
    assertEquals(p.future.value, Some(Success(1)))
  }

  fixture.test("Stop.syncOnContinueFollow") { implicit s =>
    val ack: Future[Ack] = Stop
    val p = Promise[Int]()

    ack.syncOnContinueFollow(p, 1)
    s.tick()
    assertEquals(p.future.value, None)
  }

  fixture.test("Future(Continue).syncOnContinueFollow") { implicit s =>
    val ack: Future[Ack] = Future(Continue)
    val p = Promise[Int]()

    ack.syncOnContinueFollow(p, 1)
    assertEquals(p.future.value, None)

    // should be async
    s.tick()
    assertEquals(p.future.value, Some(Success(1)))
  }

  fixture.test("Future(Stop).syncOnContinueFollow") { implicit s =>
    val ack: Future[Ack] = Future(Stop)
    val p = Promise[Int]()

    ack.syncOnContinueFollow(p, 1)
    s.tick()
    assertEquals(p.future.value, None)
  }

  fixture.test("Continue.syncOnStopFollow") { implicit s =>
    val ack: Future[Ack] = Continue
    val p = Promise[Int]()

    ack.syncOnStopFollow(p, 1)
    s.tick()
    assertEquals(p.future.value, None)
  }

  fixture.test("Stop.syncOnStopFollow") { _ =>
    val ack: Future[Ack] = Stop
    val p = Promise[Int]()

    ack.syncOnStopFollow(p, 1)
    // should be immediate
    assertEquals(p.future.value, Some(Success(1)))
  }

  fixture.test("Future(Continue).syncOnStopFollow") { implicit s =>
    val ack: Future[Ack] = Future(Continue)
    val p = Promise[Int]()

    ack.syncOnStopFollow(p, 1)
    s.tick()
    assertEquals(p.future.value, None)
  }

  fixture.test("Future(Stop).syncOnStopFollow") { implicit s =>
    val ack: Future[Ack] = Future(Stop)
    val p = Promise[Int]()

    ack.syncOnStopFollow(p, 1)
    s.tick()
    assertEquals(p.future.value, Some(Success(1)))
  }

  fixture.test("syncTryFlatten works for synchronous failure") { implicit s =>
    val dummy = new RuntimeException("dummy")
    val f: Future[Ack] = Future.failed(dummy)

    val sync = f.syncTryFlatten
    assertEquals(sync, Stop)
    assertEquals(s.state.lastReportedError, dummy)
  }

  fixture.test("Continue.transform") { implicit s =>
    val f1 = Continue.transform { _ =>
      Success(1)
    }
    s.tick()
    assertEquals(f1.value, Some(Success(1)))

    val dummy = new RuntimeException("dummy")
    val f2 = Continue.transform { _ =>
      Failure(dummy)
    }
    s.tick()
    assertEquals(f2.value, Some(Failure(dummy)))

    val f3 = Continue.transform { _ =>
      throw dummy
    }
    s.tick()
    assertEquals(f3.value, Some(Failure(dummy)))
  }

  fixture.test("Continue.transformWith") { implicit s =>
    val f1 = Continue.transformWith { _ =>
      Future.successful(1)
    }
    s.tick()
    assertEquals(f1.value, Some(Success(1)))

    val dummy = new RuntimeException("dummy")
    val f2 = Continue.transformWith { _ =>
      Future.failed(dummy)
    }
    s.tick()
    assertEquals(f2.value, Some(Failure(dummy)))

    val f3 = Continue.transformWith { _ =>
      throw dummy
    }
    s.tick()
    assertEquals(f3.value, Some(Failure(dummy)))
  }

  fixture.test("Continue.transformWith is stack safe") { implicit s =>
    def loop(n: Int): Future[Ack] =
      if (n <= 0) Continue
      else
        Continue.transformWith {
          case Success(_) => loop(n - 1)
          case Failure(ex) => Future.failed(ex)
        }

    val f = loop(100000); s.tick()
    assertEquals(f.value, Some(Success(Continue)))
  }

  fixture.test("Stop.transform") { implicit s =>
    val f1 = Stop.transform { _ =>
      Success(1)
    }
    s.tick()
    assertEquals(f1.value, Some(Success(1)))

    val dummy = new RuntimeException("dummy")
    val f2 = Stop.transform { _ =>
      Failure(dummy)
    }
    s.tick()
    assertEquals(f2.value, Some(Failure(dummy)))

    val f3 = Stop.transform { _ =>
      throw dummy
    }
    s.tick()
    assertEquals(f3.value, Some(Failure(dummy)))
  }

  fixture.test("Stop.transformWith") { implicit s =>
    val f1 = Stop.transformWith { _ =>
      Future.successful(1)
    }
    s.tick()
    assertEquals(f1.value, Some(Success(1)))

    val dummy = new RuntimeException("dummy")
    val f2 = Stop.transformWith { _ =>
      Future.failed(dummy)
    }
    s.tick()
    assertEquals(f2.value, Some(Failure(dummy)))

    val f3 = Stop.transformWith { _ =>
      throw dummy
    }
    s.tick()
    assertEquals(f3.value, Some(Failure(dummy)))
  }

  fixture.test("Stop.transformWith is stack safe") { implicit s =>
    def loop(n: Int): Future[Ack] =
      if (n <= 0) Stop
      else
        Stop.transformWith {
          case Success(_) => loop(n - 1)
          case Failure(ex) => Future.failed(ex)
        }

    val f = loop(100000); s.tick()
    assertEquals(f.value, Some(Success(Stop)))
  }
}
