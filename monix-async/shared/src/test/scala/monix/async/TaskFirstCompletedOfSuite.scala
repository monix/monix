package monix.async

import concurrent.duration._
import scala.util.{Success, Failure}

object TaskFirstCompletedOfSuite extends BaseTestSuite {
  test("Task.firstCompletedOf should switch to other") { implicit s =>
    val task = Task.firstCompletedOf(Seq(Task(1).delayExecution(10.seconds), Task(99).delayExecution(1.second)))
    val f = task.runAsync

    s.tick()
    assertEquals(f.value, None)
    s.tick(1.second)
    assertEquals(f.value, Some(Success(99)))
  }

  test("Task.firstCompletedOf should onError from other") { implicit s =>
    val ex = DummyException("dummy")
    val task = Task.firstCompletedOf(Seq(Task(1).delayExecution(10.seconds), Task(throw ex).delayExecution(1.second)))
    val f = task.runAsync

    s.tick()
    assertEquals(f.value, None)
    s.tick(1.second)
    assertEquals(f.value, Some(Failure(ex)))
  }

  test("Task.firstCompletedOf should mirror the source") { implicit s =>
    val task = Task.firstCompletedOf(Seq(Task(1).delayExecution(1.seconds), Task(99).delayExecution(10.second)))
    val f = task.runAsync

    s.tick()
    assertEquals(f.value, None)
    s.tick(1.second)
    assertEquals(f.value, Some(Success(1)))
    assert(s.state.get.tasks.isEmpty, "other should be canceled")
  }

  test("Task.firstCompletedOf should onError from the source") { implicit s =>
    val ex = DummyException("dummy")
    val task = Task.firstCompletedOf(Seq(Task(throw ex).delayExecution(1.seconds), Task(99).delayExecution(10.second)))
    val f = task.runAsync

    s.tick()
    assertEquals(f.value, None)
    s.tick(1.second)
    assertEquals(f.value, Some(Failure(ex)))
    assert(s.state.get.tasks.isEmpty, "other should be canceled")
  }

  test("Task.firstCompletedOf should cancel both") { implicit s =>
    val task = Task.firstCompletedOf(Seq(Task(1).delayExecution(10.seconds), Task(99).delayExecution(1.second)))
    val f = task.runAsync

    s.tick()
    assertEquals(f.value, None)
    f.cancel()
    s.tick()

    assertEquals(f.value, None)
    assert(s.state.get.tasks.isEmpty, "both should be canceled")
  }
}
