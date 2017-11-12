package monix.execution.schedulers

import java.util.concurrent.{CountDownLatch, TimeUnit, TimeoutException}

import minitest.SimpleTestSuite
import monix.execution.ExecutionModel.AlwaysAsyncExecution
import monix.execution.{Cancelable, Scheduler, ExecutionModel => ExecModel}
import monix.execution.cancelables.SingleAssignmentCancelable
import monix.execution.misc.Local

import scala.concurrent.duration._
import scala.concurrent.{Await, Promise}
import scala.concurrent.duration.FiniteDuration
import scala.util.Success

// Has to comply with the same behavior as AsyncScheduler
object TracingSchedulerSuite extends SimpleTestSuite {
  val s: Scheduler = monix.execution.Scheduler.traced

  def scheduleOnce(s: Scheduler, delay: FiniteDuration)(action: => Unit): Cancelable = {
    s.scheduleOnce(delay.length, delay.unit, runnableAction(action))
  }

  case class Tc(m: String = "1234") extends Local.LocalContext

  test("should propagate context") {
    val key = new Local.Key
    val ts: Scheduler = monix.execution.Scheduler.traced
    val as: Scheduler = monix.execution.Scheduler.global

    val p1 = Promise[Local.Context]()
    val p2 = Promise[Local.Context]()

    assert(Local.getContext() == null)

    def run(p: Promise[Local.Context]) = new Runnable {
      override def run(): Unit =
        p.complete(Success(Local.getContext()))
    }

    Local.setContext(Map(key -> Some(Tc())))
    assert(Local.getContext().get(key) contains Some(Tc()))

    as.execute(run(p1))
    val res1 = Await.result(p1.future, 5.seconds)
    assert(res1 == null)
    ts.execute(run(p2))
    val res2 = Await.result(p2.future, 5.seconds)
    assert(res2.get(key) contains Some(Tc()))
  }

  test("scheduleOnce with delay") {
    val p = Promise[Long]()
    val startedAt = System.nanoTime()
    scheduleOnce(s, 100.millis)(p.success(System.nanoTime()))

    val timeTaken = Await.result(p.future, 3.second)
    assert((timeTaken - startedAt).nanos.toMillis >= 100)
  }

  test("scheduleOnce with delay lower than 1.milli") {
    val p = Promise[Int]()
    scheduleOnce(s, 20.nanos)(p.success(1))
    assert(Await.result(p.future, 3.seconds) == 1)
  }

  test("scheduleOnce with delay and cancel") {
    val p = Promise[Int]()
    val task = scheduleOnce(s, 100.millis)(p.success(1))
    task.cancel()

    intercept[TimeoutException] {
      Await.result(p.future, 150.millis)
    }
  }

  test("schedule with fixed delay") {
    val sub = SingleAssignmentCancelable()
    val p = Promise[Int]()
    var value = 0

    sub := s.scheduleWithFixedDelay(10, 50, TimeUnit.MILLISECONDS, runnableAction {
      if (value + 1 == 4) {
        value += 1
        sub.cancel()
        p.success(value)
      }
      else if (value < 4) {
        value += 1
      }
    })

    assert(Await.result(p.future, 5.second) == 4)
  }

  test("schedule at fixed rate") {
    val sub = SingleAssignmentCancelable()
    val p = Promise[Int]()
    var value = 0

    sub := s.scheduleAtFixedRate(10, 50, TimeUnit.MILLISECONDS, runnableAction {
      if (value + 1 == 4) {
        value += 1
        sub.cancel()
        p.success(value)
      }
      else if (value < 4) {
        value += 1
      }
    })

    assert(Await.result(p.future, 5.second) == 4)
  }

  test("builder for ExecutionModel works") {
    import monix.execution.ExecutionModel.AlwaysAsyncExecution
    import monix.execution.Scheduler

    val s: Scheduler = Scheduler(AlwaysAsyncExecution)
    assertEquals(s.executionModel, AlwaysAsyncExecution)

    val latch = new CountDownLatch(1)
    s.execute(new Runnable {
      def run(): Unit = latch.countDown()
    })

    assert(latch.await(15, TimeUnit.MINUTES), "latch.await")
  }

  test("execute local") {
    var result = 0
    def loop(n: Int): Unit =
      s.executeTrampolined { () =>
        result += 1
        if (n-1 > 0) loop(n-1)
      }

    val count = 100000
    loop(count)
    assertEquals(result, count)
  }

  test("change execution model") {
    val s: Scheduler = monix.execution.Scheduler.traced
    assertEquals(s.executionModel, ExecModel.Default)
    val s2 = s.withExecutionModel(AlwaysAsyncExecution)
    assertEquals(s.executionModel, ExecModel.Default)
    assertEquals(s2.executionModel, AlwaysAsyncExecution)
  }

  def runnableAction(f: => Unit): Runnable =
    new Runnable { def run() = f }
}

