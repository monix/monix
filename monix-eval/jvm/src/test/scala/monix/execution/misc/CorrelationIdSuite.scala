package monix.execution.misc

import minitest.SimpleTestSuite
import monix.eval.Task
import monix.execution.Scheduler

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.util.Success

object CorrelationIdSuite extends SimpleTestSuite {


  def taskFlatMap(implicit sc: Scheduler): Task[(Int, Option[CorrelationId])] = {
    for {
      a <- Task.fromFuture(Future(1234))
      b <- Task.fromFuture(Future(1234))
      c <- Task.fromFuture(Future(1234))
      d <- Task.pure(CorrelationId.current)
    } yield (a + b + c, d)
  }

  def sampleTracedTaskTick: Task[Option[CorrelationId]] = {
    for {
      _ <- tick
      i <- pure
    } yield i
  }

  def pure: Task[Option[CorrelationId]] = {
    Task.pure(CorrelationId.current)
  }

  def logTask: Task[Unit] = {
    Task.delay(println(s"Log this map ${CorrelationId.current}"))
  }

  val tick: Task[Unit] = Task.unsafeCreate { (ctx, cb) =>
    import java.util.concurrent.Executors
    val ec = Executors.newSingleThreadExecutor()

    ec.execute(new Runnable () {
      def run() = {
        try {
          cb(Success(()))
        } finally ec.shutdown()
      }
    })
  }

  test("should get CorrelarionId with flatmapped Task with async boundary") {
    // Works with the TracingScheduler given a Task.fromFuture
    import monix.execution.schedulers.TracingScheduler.Implicits.traced
    val t = CorrelationId("5678").asCurrent {
      taskFlatMap.runAsync.map {
        case (x, v) =>
          (x, CorrelationId.current)
      }
    }
    val f = t
    val (_, cid) = Await.result(f, 10.seconds)
    assert(cid.contains(CorrelationId("5678")))
  }

  test("should NOT get CorrelarionId with flatmapped Task with async boundary") {
    // Does not work without TracingScheduler
    import monix.execution.Scheduler.Implicits.global
    val t = CorrelationId("5678").asCurrent {
      taskFlatMap.runAsyncTraced.map {
        case (x, v) =>
          (x, CorrelationId.current)
      }
    }
    val f = t
    val (_, cid) = Await.result(f, 10.seconds)
    assert(cid.isEmpty)
  }

  test("should get CorrelationId with no async boundary") {
    // Works without the TracingScheduler
    import monix.execution.Scheduler.Implicits.global

    val t = CorrelationId("1111").asCurrent {
      sampleTracedTaskTick.runAsyncTraced
    }
    val res = Await.result(t, 10.seconds)
    assert(res.contains(CorrelationId("1111")))
  }

  test("should NOT get CorrelationId with no async boundary") {
    // Even though we are using TrancingScheduler
    import monix.execution.schedulers.TracingScheduler.Implicits.traced

    val t = CorrelationId("2222").asCurrent {
      sampleTracedTaskTick.runAsync
    }
    val res = Await.result(t, 10.seconds)
    assert(res.isEmpty)
  }

  test("should get CorrelationId with a delayed Task") {
    import monix.execution.schedulers.TracingScheduler.Implicits.traced

    val f = CorrelationId("3333").asCurrent {
      logTask.runAsync.map { _ =>
        CorrelationId.current.map(_.id)
      }
    }
    val res = Await.result(f, 10.seconds)
    assert(res.contains("3333"))
  }

  test("should NOT get CorrelationId with a delayed Task") {
    import monix.execution.Scheduler.Implicits.global

    val f = CorrelationId("3333").asCurrent {
      logTask.runAsync.map { _ =>
        CorrelationId.current.map(_.id)
      }
    }
    val res = Await.result(f, 10.seconds)
    assert(res.isEmpty)
  }
}
