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

package monix.eval

import monix.execution.Callback
import monix.execution.exceptions.CallbackCalledMultipleTimesException
import monix.execution.schedulers.TestScheduler
import scala.util.{Failure, Success}

object TaskCallbackSafetySuite extends BaseTestSuite {
  test("Task.async's callback can be called multiple times") { implicit sc =>
    runTestCanCallMultipleTimes(Task.async)
  }

  test("Task.async0's callback can be called multiple times") { implicit sc =>
    runTestCanCallMultipleTimes(r => Task.async0((_, cb) => r(cb)))
  }

  test("Task.asyncF's callback can be called multiple times") { implicit sc =>
    runTestCanCallMultipleTimes(r => Task.asyncF(cb => Task(r(cb))))
  }

  test("Task.cancelable's callback can be called multiple times") { implicit sc =>
    runTestCanCallMultipleTimes(r =>
      Task.cancelable { cb =>
        r(cb); Task.unit
      })
  }

  test("Task.cancelable0's callback can be called multiple times") { implicit sc =>
    runTestCanCallMultipleTimes(r =>
      Task.cancelable0 { (_, cb) =>
        r(cb); Task.unit
      })
  }

  test("Task.async's register throwing is signaled as error") { implicit sc =>
    runTestRegisterCanThrow(Task.async)
  }

  test("Task.async0's register throwing is signaled as error") { implicit sc =>
    runTestRegisterCanThrow(r => Task.async0((_, cb) => r(cb)))
  }

  test("Task.asyncF's register throwing is signaled as error (1)") { implicit sc =>
    runTestRegisterCanThrow(r => Task.asyncF(cb => Task(r(cb))))
  }

  test("Task.asyncF's register throwing is signaled as error (2)") { implicit sc =>
    runTestRegisterCanThrow(r => Task.asyncF(cb => { r(cb); Task.unit }))
  }

  test("Task.cancelable's register throwing is signaled as error") { implicit sc =>
    runTestRegisterCanThrow(r =>
      Task.cancelable { cb =>
        r(cb); Task.unit
      })
  }

  test("Task.cancelable0's register throwing is signaled as error") { implicit sc =>
    runTestRegisterCanThrow(r =>
      Task.cancelable0 { (_, cb) =>
        r(cb); Task.unit
      })
  }

  test("Task.async's register throwing, after result, is reported") { implicit sc =>
    runTestRegisterThrowingCanBeReported(Task.async)
  }

  test("Task.async0's register throwing, after result, is reported") { implicit sc =>
    runTestRegisterThrowingCanBeReported(r => Task.async0((_, cb) => r(cb)))
  }

  test("Task.asyncF's register throwing, after result, is reported (1)") { implicit sc =>
    runTestRegisterThrowingCanBeReported(r => Task.asyncF(cb => Task(r(cb))))
  }

  test("Task.asyncF's register throwing, after result, is reported (2)") { implicit sc =>
    runTestRegisterThrowingCanBeReported(r => Task.asyncF(cb => { r(cb); Task.unit }))
  }

  test("Task.cancelable's register throwing, after result, is reported") { implicit sc =>
    runTestRegisterThrowingCanBeReported(r =>
      Task.cancelable { cb =>
        r(cb); Task.unit
      })
  }

  test("Task.cancelable0's register throwing, after result, is reported") { implicit sc =>
    runTestRegisterThrowingCanBeReported(r =>
      Task.cancelable0 { (_, cb) =>
        r(cb); Task.unit
      })
  }

  def runTestRegisterCanThrow(create: (Callback[Throwable, Int] => Unit) => Task[Int])(implicit
    sc: TestScheduler): Unit = {

    var effect = 0
    val task = create { _ =>
      throw WrappedEx(10)
    }

    task.runAsync {
      case Right(_) => ()
      case Left(WrappedEx(nr)) => effect += nr
      case Left(e) => throw e
    }

    sc.tick()
    assertEquals(effect, 10)
    assertEquals(sc.state.lastReportedError, null)
  }

  def runTestRegisterThrowingCanBeReported(create: (Callback[Throwable, Int] => Unit) => Task[Int])(implicit
    sc: TestScheduler): Unit = {

    var effect = 0
    val task = create { cb =>
      cb.onSuccess(1)
      throw WrappedEx(10)
    }

    task.runAsync {
      case Right(_) => effect += 1
      case Left(WrappedEx(nr)) => effect += nr
      case Left(e) => throw e
    }

    sc.tick()
    assertEquals(effect, 1)
    assertEquals(sc.state.lastReportedError, WrappedEx(10))
  }

  def runTestCanCallMultipleTimes(create: (Callback[Throwable, Int] => Unit) => Task[Int])(implicit
    sc: TestScheduler): Unit = {

    def run(expected: Int)(trySignal: Callback[Throwable, Int] => Boolean) = {
      var effect = 0
      val task = create { cb =>
        assert(trySignal(cb))
        assert(!trySignal(cb))
        assert(!trySignal(cb))
      }

      task.runAsync {
        case Right(nr) => effect += nr
        case Left(WrappedEx(nr)) => effect += nr
        case Left(e) => throw e
      }

      sc.tick()
      assertEquals(effect, expected)
      assertEquals(sc.state.lastReportedError, null)
    }

    run(1)(_.tryOnSuccess(1))
    run(1)(_.tryApply(Success(1)))
    run(1)(_.tryApply(Right(1)))

    run(1)(cb =>
      try {
        cb.onSuccess(1); true
      } catch { case _: CallbackCalledMultipleTimesException => false })
    run(1)(cb =>
      try {
        cb(Right(1)); true
      } catch { case _: CallbackCalledMultipleTimesException => false })
    run(1)(cb =>
      try {
        cb(Success(1)); true
      } catch { case _: CallbackCalledMultipleTimesException => false })

    run(10)(_.tryOnError(WrappedEx(10)))
    run(10)(_.tryApply(Failure(WrappedEx(10))))
    run(10)(_.tryApply(Left(WrappedEx(10))))

    run(10)(cb =>
      try {
        cb.onError(WrappedEx(10)); true
      } catch { case _: CallbackCalledMultipleTimesException => false })
    run(10)(cb =>
      try {
        cb(Left(WrappedEx(10))); true
      } catch { case _: CallbackCalledMultipleTimesException => false })
    run(10)(cb =>
      try {
        cb(Failure(WrappedEx(10))); true
      } catch { case _: CallbackCalledMultipleTimesException => false })
  }

  case class WrappedEx(nr: Int) extends RuntimeException
}
