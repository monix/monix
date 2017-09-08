package monix.execution.misc

import minitest.SimpleTestSuite
import monix.eval.Task
import monix.execution.schedulers.TracingScheduler.Implicits.traced

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.util.Success

object CorrelationIdSuite extends SimpleTestSuite {



  def sampleTracedTask(s: String): Task[Int] = CorrelationId(s).asCurrent {
    Task.now(1234)
  }

  def sampleTracedTaskFlatMap(s: String): Task[Int] = CorrelationId(s).asCurrent {
    for {
      a <- Task.now(1234)
      b <- Task.fromFuture(Future(1234))
      c <- Task.fromFuture(Future(1234))
      d <- Task.fromFuture(Future(1234))
      e <- Task.fromFuture(Future(1234))
      f <- Task.fromFuture(Future(1234))
      g <- Task.fromFuture(Future(1234))
    } yield a + b + c + d + e + f + g
  }

  def sampleTracedTaskTick(s: String): Task[Option[CorrelationId]] = CorrelationId(s).asCurrent {
    tick.flatMap(_ => Task.pure(CorrelationId.current))
  }

  def logTask(s: String): Task[Unit] = CorrelationId(s).asCurrent {
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

  test("Traced") {
    Future {
      val t = sampleTracedTask("1234")
      val f = t.runAsync.map { _ =>
        CorrelationId.current.map(_.id)
      }
      val res = Await.result(f, 10.seconds)
      assert(res.contains("1234"))
    }
  }

  test("Traced flatmapped") {
    Future {
      val t = sampleTracedTaskFlatMap("0000")
      val f = t.runAsync.map { _ =>
        CorrelationId.current.map(_.id)
      }
      val res = Await.result(f, 10.seconds)
      assert(res.contains("0000"))
    }
  }

  test("Tick") {
    Future {
      val t = sampleTracedTaskTick("1111")
      val f = t.runAsync.map { _ =>
        CorrelationId.current.map(_.id)
      }
      val res = Await.result(f, 10.seconds)
      println(s"Tick ID $res")
      assert(res.contains("1111"))
    }
  }

  test("Log") {
    Future {
      val t = logTask("2222")
      val f = t.runAsync.map { _ =>
        CorrelationId.current.map(_.id)
      }
      val res = Await.result(f, 10.seconds)
      assert(res.contains("2222"))
    }
  }

}
