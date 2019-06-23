package monix.eval

import minitest.SimpleTestSuite
import monix.execution.Scheduler
import monix.execution.exceptions.DummyException

import scala.concurrent.ExecutionContext

object TaskCancelableJVMSuite extends SimpleTestSuite {
  private val ThreadName = "test-thread"

  private val TestEC = new ExecutionContext {
    def execute(r: Runnable): Unit = {
      val th = new Thread(r)
      th.setName(ThreadName)
      th.start()
    }

    def reportFailure(cause: Throwable): Unit =
      throw cause
  }

  test("Task.cancelable should shift back to the main scheduler on success") {
    implicit val s = Scheduler(TestEC)

    val s2 = Scheduler.global

    val task = Task
      .cancelable[Int] { cb =>
        s2.executeAsync { () =>
          cb.onSuccess(1)
        }
        Task.unit
      }
      .flatMap(_ => Task(Thread.currentThread().getName))

    assertEquals(task.runSyncUnsafe(), ThreadName)
  }

  test("Task.cancelable should shift back to the main scheduler on error") {
    val e = DummyException("dummy")

    implicit val s = Scheduler(TestEC)
    val s2 = Scheduler.global

    val task = Task
      .cancelable[Int] { cb =>
        s2.executeAsync { () =>
          cb.onError(e)
        }
        Task.unit
      }
      .attempt
      .flatMap(_ => Task(Thread.currentThread().getName))

    assertEquals(task.runSyncUnsafe(), ThreadName)
  }

  test("Task.cancelable0 should shift back to the main scheduler on success") {
    implicit val s = Scheduler(TestEC)
    val s2 = Scheduler.global

    val task = Task
      .cancelable0[Int] { (_, cb) =>
        s2.executeAsync { () =>
          cb.onSuccess(1)
        }
        Task.unit
      }
      .flatMap(_ => Task(Thread.currentThread().getName))

    assertEquals(task.runSyncUnsafe(), ThreadName)
  }

  test("Task.cancelable0 should shift back to the main scheduler on error") {
    val e = DummyException("dummy")

    implicit val s = Scheduler(TestEC)
    val s2 = Scheduler.global

    val task = Task
      .cancelable0[Int] { (_, cb) =>
        s2.executeAsync { () =>
          cb.onError(e)
        }
        Task.unit
      }
      .attempt
      .flatMap(_ => Task(Thread.currentThread().getName))

    assertEquals(task.runSyncUnsafe(), ThreadName)
  }
}
