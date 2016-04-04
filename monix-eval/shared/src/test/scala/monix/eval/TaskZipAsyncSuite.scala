package monix.eval

import concurrent.duration._
import scala.util.{Success, Failure}

object TaskZipAsyncSuite extends BaseTestSuite{
  test("Task#zipAsync should work if source finishes first") { implicit s =>
    val f = Task(1).zipAsync(Task(2).delayExecution(1.second)).runAsync

    s.tick()
    assertEquals(f.value, None)
    s.tick(1.second)
    assertEquals(f.value, Some(Success((1,2))))
  }

  test("Task#zipAsync should work if other finishes first") { implicit s =>
    val f = Task(1).delayExecution(1.second).zipAsync(Task(2)).runAsync

    s.tick()
    assertEquals(f.value, None)
    s.tick(1.second)
    assertEquals(f.value, Some(Success((1,2))))
  }

  test("Task#zipAsync should cancel both") { implicit s =>
    val f = Task(1).delayExecution(1.second).zipAsync(Task(2).delayExecution(2.seconds)).runAsync

    s.tick()
    assertEquals(f.value, None)
    f.cancel()

    s.tick()
    assertEquals(f.value, None)
  }

  test("Task#zipAsync should cancel just the source") { implicit s =>
    val f = Task(1).delayExecution(1.second).zipAsync(Task(2).delayExecution(2.seconds)).runAsync

    s.tick(1.second)
    assertEquals(f.value, None)
    f.cancel()

    s.tick()
    assertEquals(f.value, None)
  }

  test("Task#zipAsync should cancel just the other") { implicit s =>
    val f = Task(1).delayExecution(2.second).zipAsync(Task(2).delayExecution(1.seconds)).runAsync

    s.tick(1.second)
    assertEquals(f.value, None)
    f.cancel()

    s.tick()
    assertEquals(f.value, None)
  }

  test("Task#zipAsync should onError from the source before other") { implicit s =>
    val ex = DummyException("dummy")
    val f = Task[Int](throw ex).delayExecution(1.second).zipAsync(Task(2).delayExecution(2.seconds)).runAsync

    s.tick(1.second)
    assertEquals(f.value, Some(Failure(ex)))
  }

  test("Task#zipAsync should onError from the source after other") { implicit s =>
    val ex = DummyException("dummy")
    val f = Task[Int](throw ex).delayExecution(2.second).zipAsync(Task(2).delayExecution(1.seconds)).runAsync

    s.tick(2.second)
    assertEquals(f.value, Some(Failure(ex)))
  }

  test("Task#zipAsync should onError from the other after the source") { implicit s =>
    val ex = DummyException("dummy")
    val f = Task(1).delayExecution(1.second).zipAsync(Task(throw ex).delayExecution(2.seconds)).runAsync

    s.tick(2.second)
    assertEquals(f.value, Some(Failure(ex)))
  }

  test("Task#zipAsync should onError from the other before the source") { implicit s =>
    val ex = DummyException("dummy")
    val f = Task(1).delayExecution(2.second).zipAsync(Task(throw ex).delayExecution(1.seconds)).runAsync

    s.tick(1.second)
    assertEquals(f.value, Some(Failure(ex)))
  }

  test("Task#zipAsyncWith works") { implicit s =>
    val f1 = Task(1).zipAsync(Task(2)).runAsync
    val f2 = Task(1).zipAsyncWith(Task(2))((a,b) => (a,b)).runAsync
    s.tick()
    assertEquals(f1.value.get, f2.value.get)
  }
}
