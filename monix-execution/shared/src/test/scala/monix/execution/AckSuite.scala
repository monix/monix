/*
 * Copyright (c) 2014-2016 by its authors. Some rights reserved.
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

import minitest.TestSuite
import monix.execution.Ack.{Cancel, Continue}
import monix.execution.schedulers.TestScheduler

import scala.concurrent.Future

object AckSuite extends TestSuite[TestScheduler] {
  def setup() = TestScheduler()

  def tearDown(env: TestScheduler): Unit = {
    assert(env.state.get.tasks.isEmpty, "should not have tasks left to execute")
  }

  test("syncOnContinue(Continue) should execute synchronously #1") { implicit s =>
    var triggered = false
    Continue.syncOnContinue { triggered = true }
    assert(triggered, "triggered")
  }

  test("syncOnContinue(Continue) should execute synchronously #2") { implicit s =>
    val continue: Future[Ack] = Continue
    var triggered = false
    val trigger: () => Unit = {
      val value = true
      () => triggered = value
    }

    continue.syncOnContinue(trigger())
    assert(triggered, "triggered")
  }

  test("syncOnContinue(Future.successful(Continue)) should execute async") { implicit s =>
    var triggered = false
    val continue: Future[Ack] = Future.successful(Continue)
    continue.syncOnContinue { triggered = true }

    assert(!triggered, "!triggered")
    s.tick()
    assert(triggered, "triggered")
  }

  test("syncOnContinue(Future(Continue)) should execute async") { implicit s =>
    var triggered = false
    val continue: Future[Ack] = Future(Continue)
    continue.syncOnContinue { triggered = true }

    assert(!triggered, "!triggered")
    s.tick()
    assert(triggered, "triggered")
  }

  test("syncOnContinue(Cancel) should execute synchronously #1") { implicit s =>
    var triggered = false
    (Cancel : Future[Ack]).syncOnContinue { triggered = true }
    assert(!triggered, "!triggered")
    assert(s.state.get.tasks.isEmpty, "there should be no async task registered")
  }

  test("syncOnContinue(Cancel) should execute synchronously #2") { implicit s =>
    val cancel: Future[Ack] = Cancel
    var triggered = false
    val trigger: () => Unit = {
      val value = true
      () => triggered = value
    }

    cancel.syncOnContinue(trigger())
    assert(!triggered, "!triggered")
    assert(s.state.get.tasks.isEmpty, "there should be no async task registered")
  }

  test("syncOnContinue(Future.successful(Cancel)) should execute async") { implicit s =>
    var triggered = false
    Future.successful(Cancel : Ack).syncOnContinue { triggered = true }
    assert(s.state.get.tasks.nonEmpty, "async tasks should be registered")
    s.tickOne()
    assert(!triggered, "!triggered")
  }

  test("syncOnContinue(Future(Cancel)) should execute async") { implicit s =>
    var triggered = false
    Future(Cancel : Ack).syncOnContinue { triggered = true }
    assert(s.state.get.tasks.nonEmpty, "async tasks should be registered")
    s.tick()
    assert(!triggered, "!triggered")
  }

  test("syncOnContinue(Continue) should protect against user errors") { implicit s =>
    val ex = new RuntimeException("dummy")
    Continue.syncOnContinue { throw ex }
    assertEquals(s.state.get.lastReportedError, ex)
  }

  test("syncOnContinue(Continue) should not protect against fatal errors") { implicit s =>
    val ex = new InterruptedException()
    var caught: Throwable = null

    try Continue.syncOnContinue { throw ex } catch {
      case err: InterruptedException => caught = err
    }

    assertEquals(s.state.get.lastReportedError, null)
    assertEquals(caught, ex)
  }

  test("syncOnCancel(Cancel) should execute synchronously") { implicit s =>
    var triggered = false
    Cancel.syncOnCancelOrFailure { triggered = true }
    assert(triggered, "triggered")
    assert(s.state.get.tasks.isEmpty, "there should be no async tasks registered")
  }

  test("syncOnCancel(Continue) should execute synchronously") { implicit s =>
    var triggered = false
    (Continue : Ack).syncOnCancelOrFailure { triggered = true }
    assert(!triggered, "!triggered")
    assert(s.state.get.tasks.isEmpty, "there should be no async tasks registered")
  }

  test("syncOnCancel(Future.successful(Cancel)) should execute asynchronously") { implicit s =>
    var triggered = false
    Future.successful(Cancel).syncOnCancelOrFailure { triggered = true }
    assert(s.state.get.tasks.nonEmpty, "there should be async tasks registered")
    s.tick()
    assert(triggered, "triggered")
  }

  test("syncOnCancel(Future.successful(Continue)) should execute asynchronously") { implicit s =>
    var triggered = false
    (Future.successful(Continue) : Future[Ack]).syncOnCancelOrFailure { triggered = true }
    assert(s.state.get.tasks.nonEmpty, "there should be async tasks registered")
    s.tick()
    assert(!triggered, "!triggered")
  }

  test("syncOnCancel(Failure(ex)) should execute") { implicit s =>
    var triggered = false
    val ex = new RuntimeException("dummy")
    val ack: Future[Ack] = Future.failed(ex)
    ack.syncOnCancelOrFailure { triggered = true }

    assertEquals(triggered, false)
    assert(s.state.get.tasks.nonEmpty, "there should be async tasks registered")
    s.tickOne()
    assertEquals(triggered, true)
  }

  test("syncOnCancel(Future(throw ex)) should execute") { implicit s =>
    var triggered = false
    val ex = new RuntimeException("dummy")
    val ack: Future[Ack] = Future { throw ex }
    ack.syncOnCancelOrFailure { triggered = true }

    assertEquals(triggered, false)
    assert(s.state.get.tasks.nonEmpty, "there should be async tasks registered")
    s.tick()
    assertEquals(triggered, true)
  }

  test("syncOnCancel(Cancel) should protect against user errors") { implicit s =>
    val ex = new RuntimeException("dummy")
    Cancel.syncOnCancelOrFailure { throw ex }
    assertEquals(s.state.get.lastReportedError, ex)
  }

  test("syncOnCancel(Cancel) should not protect against fatal errors") { implicit s =>
    val ex = new InterruptedException()
    var caught: Throwable = null

    try Cancel.syncOnCancelOrFailure { throw ex } catch {
      case err: InterruptedException => caught = err
    }

    assertEquals(s.state.get.lastReportedError, null)
    assertEquals(caught, ex)
  }

  test("syncMap(Continue) should execute synchronously") { implicit s =>
    var triggered = false
    val source: Future[Ack] = Continue

    val result = source.syncMap {
      case Cancel => Cancel
      case Continue =>
        triggered = true
        Cancel
    }

    assertEquals(result, Cancel)
    assertEquals(triggered, true)
    assert(s.state.get.tasks.isEmpty, "there should be no async tasks registered")
  }

  test("syncMap(Cancel) should execute synchronously") { implicit s =>
    var triggered = false
    val source: Future[Ack] = Cancel

    val result = source.syncMap {
      case Continue => Continue
      case Cancel =>
        triggered = true
        Continue
    }

    assertEquals(result, Continue)
    assertEquals(triggered, true)
    assert(s.state.get.tasks.isEmpty, "there should be no async tasks registered")
  }


  test("syncMap should protect against exceptions") { implicit s =>
    val dummy = new RuntimeException("dummy")
    val result = (Continue : Future[Ack]).syncMap { x => throw dummy }

    assertEquals(result, Cancel)
    assertEquals(s.state.get.lastReportedError, dummy)
  }

  test("syncMap(Future(Continue)) should execute asynchronously") { implicit s =>
    var triggered = false
    val source: Future[Ack] = Future.successful(Continue)

    val result = source.syncMap {
      case Cancel => Cancel
      case Continue =>
        triggered = true
        Cancel
    }

    assertEquals(triggered, false)
    assert(s.state.get.tasks.nonEmpty)

    s.tick()
    assert(s.state.get.tasks.isEmpty)
    assertEquals(result.syncTryFlatten, Cancel)
    assertEquals(triggered, true)
  }

  test("syncMap(Future(Cancel)) should execute asynchronously") { implicit s =>
    var triggered = false
    val source: Future[Ack] = Future.successful(Cancel)

    val result = source.syncMap {
      case Continue => Continue
      case Cancel =>
        triggered = true
        Continue
    }

    assertEquals(triggered, false)
    assert(s.state.get.tasks.nonEmpty)

    s.tick()
    assert(s.state.get.tasks.isEmpty)
    assertEquals(result.syncTryFlatten, Continue)
    assertEquals(triggered, true)
  }

  test("syncMap(Continue) with impure function") { implicit s =>
    var triggered = false
    val source: Future[Ack] = Continue
    val fn: Ack => Ack = {
      val value = true
      ack: Ack => ack match {
        case Cancel => Cancel
        case Continue =>
          triggered = value
          Cancel
      }
    }

    val result = source.syncMap(fn)
    assertEquals(triggered, true)
    assertEquals(result, Cancel)
  }

  test("syncMap(Future(Continue)) with impure function") { implicit s =>
    var triggered = false
    val source: Future[Ack] = Future(Continue)
    val fn: Ack => Ack = {
      val value = true
      ack: Ack => ack match {
        case Cancel => Cancel
        case Continue =>
          triggered = value
          Cancel
      }
    }

    val result = source.syncMap(fn)

    s.tick()
    assertEquals(triggered, true)
    assertEquals(result.syncTryFlatten, Cancel)
  }

  // --

  test("syncFlatMap(Continue) should execute synchronously") { implicit s =>
    var triggered = false
    val source: Future[Ack] = Continue

    val result = source.syncFlatMap {
      case Cancel => Cancel
      case Continue =>
        triggered = true
        Cancel
    }

    assertEquals(result, Cancel)
    assertEquals(triggered, true)
    assert(s.state.get.tasks.isEmpty, "there should be no async tasks registered")
  }

  test("syncFlatMap(Cancel) should execute synchronously") { implicit s =>
    var triggered = false
    val source: Future[Ack] = Cancel

    val result = source.syncFlatMap {
      case Continue => Continue
      case Cancel =>
        triggered = true
        Continue
    }

    assertEquals(result, Continue)
    assertEquals(triggered, true)
    assert(s.state.get.tasks.isEmpty, "there should be no async tasks registered")
  }


  test("syncFlatMap should protect against exceptions") { implicit s =>
    val dummy = new RuntimeException("dummy")
    val result = (Continue : Future[Ack]).syncFlatMap { x => throw dummy }

    assertEquals(result, Cancel)
    assertEquals(s.state.get.lastReportedError, dummy)
  }

  test("syncFlatMap(Future(Continue)) should execute asynchronously") { implicit s =>
    var triggered = false
    val source: Future[Ack] = Future.successful(Continue)

    val result = source.syncFlatMap {
      case Cancel => Cancel
      case Continue =>
        triggered = true
        Cancel
    }

    assertEquals(triggered, false)
    assert(s.state.get.tasks.nonEmpty)

    s.tick()
    assert(s.state.get.tasks.isEmpty)
    assertEquals(result.syncTryFlatten, Cancel)
    assertEquals(triggered, true)
  }

  test("syncFlatMap(Future(Cancel)) should execute asynchronously") { implicit s =>
    var triggered = false
    val source: Future[Ack] = Future.successful(Cancel)

    val result = source.syncFlatMap {
      case Continue => Continue
      case Cancel =>
        triggered = true
        Continue
    }

    assertEquals(triggered, false)
    assert(s.state.get.tasks.nonEmpty)

    s.tick()
    assert(s.state.get.tasks.isEmpty)
    assertEquals(result.syncTryFlatten, Continue)
    assertEquals(triggered, true)
  }

  test("syncFlatMap(Continue) with impure function") { implicit s =>
    var triggered = false
    val source: Future[Ack] = Continue
    val fn: Ack => Ack = {
      val value = true
      ack: Ack => ack match {
        case Cancel => Cancel
        case Continue =>
          triggered = value
          Cancel
      }
    }

    val result = source.syncFlatMap(fn)
    assertEquals(triggered, true)
    assertEquals(result, Cancel)
  }

  test("syncFlatMap(Future(Continue)) with impure function") { implicit s =>
    var triggered = false
    val source: Future[Ack] = Future(Continue)
    val fn: Ack => Ack = {
      val value = true
      ack: Ack => ack match {
        case Cancel => Cancel
        case Continue =>
          triggered = value
          Cancel
      }
    }

    val result = source.syncFlatMap(fn)

    s.tick()
    assertEquals(triggered, true)
    assertEquals(result.syncTryFlatten, Cancel)
  }

  test("syncTryFlatten(Continue)") { implicit s =>
    val f = Continue.syncTryFlatten
    assertEquals(f, Continue)
  }

  test("syncTryFlatten(Cancel)") { implicit s =>
    val f = Cancel.syncTryFlatten
    assertEquals(f, Cancel)
  }

  test("syncTryFlatten(Future(Continue))") { implicit s =>
    val f = Future.successful(Continue).syncTryFlatten
    assertEquals(f, Continue)
  }

  test("syncTryFlatten(Future(Cancel))") { implicit s =>
    val f = Cancel.syncTryFlatten
    assertEquals(f, Cancel)
  }

  test("isSynchronous(Future(Continue)) == false") { implicit s =>
    val f: Future[Ack] = Future.successful(Continue)
    assert(!f.isSynchronous)
  }

  test("isSynchronous(Continue) == true") { implicit s =>
    val f: Future[Ack] = Continue
    assert(f.isSynchronous)
  }

  test("isSynchronous(Future(Cancel)) == false") { implicit s =>
    val f: Future[Ack] = Future.successful(Cancel)
    assert(!f.isSynchronous)
  }

  test("isSynchronous(Cancel) == true") { implicit s =>
    val f: Future[Ack] = Cancel
    assert(f.isSynchronous)
  }

  test("isSynchronous(failure) == false") { implicit s =>
    val f: Future[Ack] = Future.failed(new RuntimeException)
    assert(!f.isSynchronous)
  }

  test("isSynchronous(impure Future(Continue)) == false") { implicit s =>
    def f: Future[Ack] = Future.successful(Continue)
    assert(!f.isSynchronous)
  }

  test("isSynchronous(impure Continue) == true") { implicit s =>
    def f: Future[Ack] = Continue
    assert(f.isSynchronous)
  }

  test("isSynchronous(impure Future(Cancel)) == false") { implicit s =>
    def f: Future[Ack] = Future.successful(Cancel)
    assert(!f.isSynchronous)
  }

  test("isSynchronous(impure Cancel) == true") { implicit s =>
    def f: Future[Ack] = Cancel
    assert(f.isSynchronous)
  }

  test("isSynchronous(impure failure) == false") { implicit s =>
    def f: Future[Ack] = Future.failed(new RuntimeException)
    assert(!f.isSynchronous)
  }
}
