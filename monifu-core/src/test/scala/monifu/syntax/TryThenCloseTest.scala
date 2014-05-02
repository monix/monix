package monifu.syntax

import org.scalatest.FunSuite

class TryThenCloseTest extends FunSuite {
  class Foo {
    var isClosed = false
    def close(): Unit = isClosed = true
  }

  test("close resource") {
    val inst = new Foo
    assert(!inst.isClosed)

    val out = inst.tryThenClose { f =>
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
      inst.tryThenClose { _ => throw new RuntimeException }
    }

    assert(inst.isClosed)
  }

  test("do not instantiate the resource multiple times") {
    var times = 0
    class Bar { times += 1; def close(): Unit = () }
    new Bar().tryThenClose { _ => ()}
    assert(times == 1)
  }
}
