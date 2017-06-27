package monix.eval

import cats.effect.IO
import monix.execution.exceptions.DummyException

import scala.util.{Failure, Success}

object TaskCatsConversions extends BaseTestSuite {
  test("Task.fromIO(task.toIO) == task") { implicit s =>
    check1 { (task: Task[Int]) =>
      Task.fromIO(task.toIO) <-> task
    }
  }

  test("Task.fromIO(IO.raiseError(e))") { implicit s =>
    val dummy = DummyException("dummy")
    val task = Task.fromIO(IO.raiseError(dummy))
    assertEquals(task.runAsync.value, Some(Failure(dummy)))
  }

  test("Task.fromIO(IO.raiseError(e).shift)") { implicit s =>
    val dummy = DummyException("dummy")
    val task = Task.fromIO(for (_ <- IO.shift(s); x <- IO.raiseError[Int](dummy)) yield x)
    val f = task.runAsync

    assertEquals(f.value, None)
    s.tick()
    assertEquals(f.value, Some(Failure(dummy)))
  }

  test("Task.now(v).toIO") { implicit s =>
    assertEquals(Task.now(10).toIO.unsafeRunSync(), 10)
  }

  test("Task.raiseError(dummy).toIO") { implicit s =>
    val dummy = DummyException("dummy")
    intercept[DummyException] {
      Task.raiseError(dummy).toIO.unsafeRunSync()
    }
  }

  test("Task.eval(thunk).toIO") { implicit s =>
    assertEquals(Task.eval(10).toIO.unsafeRunSync(), 10)
  }

  test("Task.eval(fa).asyncBoundary.toIO") { implicit s =>
    val io = Task.eval(1).asyncBoundary.toIO
    val f = io.unsafeToFuture()

    assertEquals(f.value, None); s.tick()
    assertEquals(f.value, Some(Success(1)))
  }

  test("Task.raiseError(dummy).asyncBoundary.toIO") { implicit s =>
    val dummy = DummyException("dummy")
    val io = Task.fork(Task.raiseError[Int](dummy)).toIO
    val f = io.unsafeToFuture()

    assertEquals(f.value, None); s.tick()
    assertEquals(f.value, Some(Failure(dummy)))
  }
}