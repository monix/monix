package monifu.concurrent.schedulers

import org.scalatest.FunSuite
import scala.concurrent.{Future, Await, Promise}
import concurrent.duration._
import java.util.concurrent.TimeoutException
import monifu.concurrent.cancelables.SingleAssignmentCancelable
import monifu.concurrent.atomic.Atomic
import monifu.concurrent.ThreadLocal
import concurrent.blocking


class TrampolineSchedulerTest extends FunSuite {
  val s = new TrampolineScheduler(
    fallback = ConcurrentScheduler.defaultInstance,
    reporter = (ex) => {
      if (!ex.getMessage.contains("test-exception"))
        throw ex
    }
  )

  test("scheduleOnce") {
    val p = Promise[Int]()
    s.scheduleOnce { p.success(1) }

    assert(Await.result(p.future, 100.millis) === 1)
  }

  test("scheduleOnce with delay") {
    val p = Promise[Long]()
    val startedAt = System.nanoTime()
    s.scheduleOnce(100.millis, p.success(System.nanoTime()))

    val endedAt = Await.result(p.future, 1.second)
    val timeTaken = (endedAt - startedAt).nanos.toMillis
    assert(timeTaken >= 100, s"timeTaken ($timeTaken millis) < 100 millis")
  }

  test("scheduleOnce with delay and cancel") {
    val p = Promise[Int]()
    val task = s.scheduleOnce(100.millis, p.success(1))
    task.cancel()

    intercept[TimeoutException] {
      Await.result(p.future, 150.millis)
    }
  }

  test("schedule") {
    val p = Promise[Int]()
    s.schedule(s2 => s2.scheduleOnce(p.success(1)))
    assert(Await.result(p.future, 100.millis) === 1)
  }

  test("schedule with delay") {
    val p = Promise[Long]()
    val startedAt = System.nanoTime()
    s.schedule(100.millis, s2 => {
      s2.scheduleOnce({
        p.success(System.nanoTime())
      })
    })

    val timeTaken = Await.result(p.future, 1.second)
    assert((timeTaken - startedAt).nanos.toMillis >= 100)
  }

  test("schedule with delay and cancel") {
    val p = Promise[Long]()
    val t = s.schedule(100.millis, s2 => s2.scheduleOnce(p.success(1)))
    t.cancel()

    intercept[TimeoutException] {
      Await.result(p.future, 150.millis)
    }
  }

  test("schedule periodically") {
    val sub = SingleAssignmentCancelable()
    val p = Promise[Int]()
    val value = Atomic(0)

    sub() = s.scheduleRepeated(10.millis, 50.millis, {
      if (value.incrementAndGet() > 3) {
        sub.cancel()
        p.success(value.get)
      }
    })

    assert(Await.result(p.future, 1.second) === 4)
  }

  test("immediate execution happens") {
    implicit val ec = s

    @volatile var effect = 0
    val seed = ThreadLocal(0)
    seed set 100

    for (v1 <- Future(seed.get()).map(_ * 100).filter(_ % 2 == 0); v2 <- Future(seed.get() * 3).map(_ - 100)) {
      effect = v1 + v2
    }

    assert(effect === 10000 + 200)
  }

  test("should execute async") {
    var stackDepth = 0
    var iterations = 0

    s.scheduleOnce { 
      stackDepth += 1
      iterations += 1
      s.scheduleOnce { 
        stackDepth += 1
        iterations += 1
        assert(stackDepth === 1)

        s.scheduleOnce { 
          stackDepth += 1
          iterations += 1
          assert(stackDepth === 1)
          stackDepth -= 1
        }

        assert(stackDepth === 1)
        stackDepth -= 1
      }
      assert(stackDepth === 1)
      stackDepth -= 1
    }

    assert(iterations === 3)
    assert(stackDepth === 0)
  }

  test("triggering an exception shouldn't blow the thread, but should reschedule pending tasks") {
    val p = Promise[String]()
    val tl = ThreadLocal("result")
    tl.set("wrong-result")

    def run() =
      s.scheduleOnce({
        s.scheduleOnce({
          p.success(tl.get())
        })

        assert(p.isCompleted === false)
        throw new RuntimeException("test-exception-please-ignore")
      })

    run()
    assert(Await.result(p.future, 1.second) === "result")
  }

  test("blocking the thread reschedules pending tasks") {
    val seenValue = ThreadLocal("async-value")
    seenValue set "local-value"

    val async = Promise[String]()
    val local = Promise[String]()

    s.scheduleOnce({
      // first schedule
      s.scheduleOnce(async.success(seenValue.get()))
      // then do some blocking
      blocking {
        local.success(seenValue.get())
      }
    })

    assert(Await.result(local.future, 1.second) === "local-value")
    assert(Await.result(async.future, 1.second) === "async-value")
  }

  test("while blocking, all future tasks should be scheduled on the fallback") {
    val seenValue = ThreadLocal("async-value")
    seenValue set "local-value"

    val async = Promise[String]()
    val local = Promise[String]()

    s.scheduleOnce({
      blocking {
        s.scheduleOnce(async.success(seenValue.get()))
        local.success(seenValue.get())
      }
    })

    assert(Await.result(local.future, 1.second) === "local-value")
    assert(Await.result(async.future, 1.second) === "async-value")
  }
}
