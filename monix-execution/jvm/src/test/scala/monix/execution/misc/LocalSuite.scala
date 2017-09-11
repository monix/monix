package monix.execution.misc

import minitest.SimpleTestSuite
import Local.LocalContext

object LocalSuite extends SimpleTestSuite {

  case class TestLocalContext(id: String) extends LocalContext

  test("Local should start None") {
    val local = new Local[TestLocalContext]
    assert(local().isEmpty)
  }

  test("Local should store context") {
    val tc = TestLocalContext("aaaa")
    val local = new Local[TestLocalContext]
    local() = tc
    assert(local().contains(tc))
  }

  test("Local should be different in separate thread") {
    val local = new Local[TestLocalContext]

    import scala.concurrent.ExecutionContext.Implicits.global
    import scala.concurrent.duration._
    import scala.concurrent.{Await, Future}

    local() = TestLocalContext("0000")

    val res: Future[Option[TestLocalContext]] = Future {
      assert(local().isEmpty)
      local() = TestLocalContext("1111")
      local()
    }

    val v = Await.result(res, 10.seconds)
    assert(local().exists(_.id == "0000"))
    assert(v.exists(_.id == "1111"))
  }

  test("Local should keep values when other locals change") {
    val l0 = new Local[TestLocalContext]
    l0() = TestLocalContext("1234")
    val ctx0 = Local.getContext()

    val l1 = new Local[TestLocalContext]
    assert(l0().exists(_.id == "1234"))
    l1() = TestLocalContext("5678")
    assert(l1().exists(_.id == "5678"))

    val ctx1 = Local.getContext()
    Local.setContext(ctx0)
    assert(l0().exists(_.id == "1234"))
    assert(l1().isEmpty)

    Local.setContext(ctx1)
    assert(l0().exists(_.id == "1234"))
    assert(l1().exists(_.id == "5678"))
  }

  test("setContext should set saved values") {
    val local = new Local[TestLocalContext]
    local() = TestLocalContext("1234")
    val saved = Local.getContext()
    local() = TestLocalContext("5678")

    Local.setContext(saved)
    assert(local().exists(_.id == "1234"))
  }

  test("withContext should set locals and restore previous value") {
    val l1 = new Local[TestLocalContext]
    val l2 = new Local[TestLocalContext]
    l1() = TestLocalContext("x")
    l2() = TestLocalContext("y")
    val ctx = Local.getContext()
    l1() = TestLocalContext("a")
    l2() = TestLocalContext("b")

    val done = Local.withContext(ctx) {
      assert(l1().exists(_.id == "x"))
      assert(l2().exists(_.id == "y"))
      true
    }

    assert(l1().exists(_.id == "a"))
    assert(l2().exists(_.id == "b"))
    assert(done)
  }

  test("withClearContext should clear all locals and restore previous value") {
    val l1 = new Local[TestLocalContext]
    val l2 = new Local[TestLocalContext]
    l1() = TestLocalContext("1")
    l2() = TestLocalContext("2")

    Local.withClearContext {
      assert(l1().isEmpty)
      assert(l2().isEmpty)
    }

    assert(l1().exists(_.id == "1"))
    assert(l2().exists(_.id == "2"))
  }

  test("setContext should unset undefined variables when restoring") {
    val local = new Local[TestLocalContext]
    val saved = Local.getContext()
    local() = TestLocalContext("0")
    Local.setContext(saved)

    assert(local().isEmpty)
  }

  test("setContext should not restore cleared variables") {
    val local = new Local[TestLocalContext]

    local() = TestLocalContext("z")
    Local.getContext()
    local.clear()
    Local.setContext(Local.getContext())
    assert(local().isEmpty)
  }

  test("withContext should scope with a value and restore previous value") {
    val local = new Local[TestLocalContext]
    local() = TestLocalContext("a1")
    local.withContext(TestLocalContext("b2")) {
      assert(local().exists(_.id == "b2"))
    }
    assert(local().exists(_.id == "a1"))
  }

  test("withClearContext should clear Local and restore previous value") {
    val local = new Local[TestLocalContext]
    local() = TestLocalContext("00")
    local.withClearContext {
      assert(local().isEmpty)
    }
    assert(local().exists(_.id == "00"))
  }

  test("clear should make a copy when clearing") {
    val l = new Local[TestLocalContext]
    l() = TestLocalContext("11")
    val ctx0 = Local.getContext()
    l.clear()
    assert(l().isEmpty)
    Local.setContext(ctx0)
    assert(l().exists(_.id == "11"))
  }

}
