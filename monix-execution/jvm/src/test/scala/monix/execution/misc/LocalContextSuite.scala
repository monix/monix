package monix.execution.misc

import minitest.SimpleTestSuite
import LocalContext.TracingContext

object LocalContextSuite extends SimpleTestSuite {

  case class TestTracingContext(id: String) extends TracingContext

  test("LocalContext should start None") {
    val local = new LocalContext[TestTracingContext]
    assert(local().isEmpty)
  }

  test("LocalContext should store context") {
    val tc = TestTracingContext("aaaa")
    val local = new LocalContext[TestTracingContext]
    local() = tc
    assert(local().contains(tc))
  }

  test("LocalContext should be different in separate thread") {
    println(s"========Thread ${Thread.currentThread.getName}")
    val local = new LocalContext[TestTracingContext]
    var threadValue: Option[TestTracingContext] = null
    import java.util.concurrent.Executors
    val ec = Executors.newSingleThreadExecutor()

    local() = TestTracingContext("0000")

    ec.execute {
      new Runnable {
        override def run() = {
          assert(local().isEmpty)
          local() = TestTracingContext("1111")
          threadValue = local()
        }
      }
    }
    assert(local().exists(_.id == "0000"))
    assert(threadValue.exists(_.id == "1111"))
  }

  test("LocalContext should keep values when other locals change") {
    println(s"========Thread ${Thread.currentThread.getName}")
    val l0 = new LocalContext[TestTracingContext]
    l0() = TestTracingContext("1234")
    val ctx0 = LocalContext.getContext()

    val l1 = new LocalContext[TestTracingContext]
    assert(l0().exists(_.id == "1234"))
    l1() = TestTracingContext("5678")
    assert(l1().exists(_.id == "5678"))

    val ctx1 = LocalContext.getContext()
    LocalContext.setContext(ctx0)
    assert(l0().exists(_.id == "1234"))
    assert(l1().isEmpty)

    LocalContext.setContext(ctx1)
    assert(l0().exists(_.id == "1234"))
    assert(l1().exists(_.id == "5678"))
  }

  test("setContext should set saved values") {
    val local = new LocalContext[TestTracingContext]
    local() = TestTracingContext("1234")
    val saved = LocalContext.getContext()
    local() = TestTracingContext("5678")

    LocalContext.setContext(saved)
    assert(local().exists(_.id == "1234"))
  }

  test("withContext should set locals and restore previous value") {
    val l1 = new LocalContext[TestTracingContext]
    val l2 = new LocalContext[TestTracingContext]
    l1() = TestTracingContext("x")
    l2() = TestTracingContext("y")
    val ctx = LocalContext.getContext()
    l1() = TestTracingContext("a")
    l2() = TestTracingContext("b")

    val done = LocalContext.withContext(ctx) {
      assert(l1().exists(_.id == "x"))
      assert(l2().exists(_.id == "y"))
      true
    }

    assert(l1().exists(_.id == "a"))
    assert(l2().exists(_.id == "b"))
    assert(done)
  }

  test("withClearContext should clear all locals and restore previous value") {
    val l1 = new LocalContext[TestTracingContext]
    val l2 = new LocalContext[TestTracingContext]
    l1() = TestTracingContext("1")
    l2() = TestTracingContext("2")

    LocalContext.withClearContext {
      assert(l1().isEmpty)
      assert(l2().isEmpty)
    }

    assert(l1().exists(_.id == "1"))
    assert(l2().exists(_.id == "2"))
  }

  test("setContext should unset undefined variables when restoring") {
    val local = new LocalContext[TestTracingContext]
    val saved = LocalContext.getContext()
    local() = TestTracingContext("0")
    LocalContext.setContext(saved)

    assert(local().isEmpty)
  }

  test("setContext should not restore cleared variables") {
    val local = new LocalContext[TestTracingContext]

    local() = TestTracingContext("z")
    LocalContext.getContext()
    local.clear()
    LocalContext.setContext(LocalContext.getContext())
    assert(local().isEmpty)
  }

  test("withContext should scope with a value and restore previous value") {
    val local = new LocalContext[TestTracingContext]
    local() = TestTracingContext("a1")
    local.withContext(TestTracingContext("b2")) {
      assert(local().exists(_.id == "b2"))
    }
    assert(local().exists(_.id == "a1"))
  }

  test("withClearContext should clear LocalContext and restore previous value") {
    val local = new LocalContext[TestTracingContext]
    local() = TestTracingContext("00")
    local.withClearContext {
      assert(local().isEmpty)
    }
    assert(local().exists(_.id == "00"))
  }

  test("clear should make a copy when clearing") {
    val l = new LocalContext[TestTracingContext]
    l() = TestTracingContext("11")
    val ctx0 = LocalContext.getContext()
    l.clear()
    assert(l().isEmpty)
    LocalContext.setContext(ctx0)
    assert(l().exists(_.id == "11"))
  }

}
