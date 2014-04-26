package monifu.syntax

import org.scalatest.FunSuite

class ForeachCloseableTest extends FunSuite {
  class Foo {
    var isClosed = false
    def close(): Unit = isClosed = true
  }

  test("close resource") {
    val inst = new Foo
    assert(!inst.isClosed)

    val out: String = for (f <- inst) {
      assert(!f.isClosed)
      "Hello, world!"
    }

    assert(inst.isClosed)
    assert(out == "Hello, world!")
  }

  test("close resource on exception thrown") {
    val inst = new Foo
    assert(!inst.isClosed)

    intercept[RuntimeException] {
      for (f <- inst) { throw new RuntimeException }
    }

    assert(inst.isClosed)
  }

  test("do not instantiate the resource multiple times") {
    var times = 0
    class Bar { times += 1; def close(): Unit = () }
    for (b <- new Bar) {}
    assert(times == 1)
  }

  test("nested foreach statements work") {
    val foo1 = new Foo
    val foo2 = new Foo

    for (f1 <- foo1; f2 <- foo2) {
      assert(!f1.isClosed && !f2.isClosed)
    }

    assert(foo1.isClosed && foo2.isClosed)
  }
}
