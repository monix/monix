package monix.eval

import cats.Eval
import monix.execution.atomic.Atomic
import monix.execution.exceptions.DummyException

object CoevalCatsConversions extends BaseTestSuite {
  test("Coeval.now(value).toEval") { _ =>
    assertEquals(Coeval.now(10).toEval.value, 10)
  }

  test("Coeval.raiseError(e).toEval") { _ =>
    val dummy = DummyException("dummy")
    val eval = Coeval.raiseError(dummy).toEval
    intercept[DummyException] { eval.value }
  }

  test("Coeval.eval(thunk).toEval") { _ =>
    val effect = Atomic(0)
    val eval = Coeval.eval(effect.incrementAndGet()).toEval

    assertEquals(eval.value, 1)
    assertEquals(eval.value, 2)
  }

  test("Coeval.evalOnce(thunk).toEval") { _ =>
    val effect = Atomic(0)
    val eval = Coeval.evalOnce(effect.incrementAndGet()).toEval

    assertEquals(eval.value, 1)
    assertEquals(eval.value, 1)
  }

  test("Coeval.now(value).toIO") { _ =>
    assertEquals(Coeval.now(10).toIO.unsafeRunSync(), 10)
  }

  test("Coeval.raiseError(e).toIO") { _ =>
    val dummy = DummyException("dummy")
    val ioRef = Coeval.raiseError(dummy).toIO
    intercept[DummyException] { ioRef.unsafeRunSync() }
  }

  test("Coeval.eval(thunk).toIO") { _ =>
    val effect = Atomic(0)
    val ioRef = Coeval.eval(effect.incrementAndGet()).toIO

    assertEquals(ioRef.unsafeRunSync(), 1)
    assertEquals(ioRef.unsafeRunSync(), 2)
  }

  test("Coeval.evalOnce(thunk).toIO") { _ =>
    val effect = Atomic(0)
    val eval = Coeval.evalOnce(effect.incrementAndGet()).toIO

    assertEquals(eval.unsafeRunSync(), 1)
    assertEquals(eval.unsafeRunSync(), 1)
  }

  test("Coeval.fromEval(Eval.now(v))") { _ =>
    assertEquals(Coeval.fromEval(Eval.now(10)), Coeval.Now(10))
  }

  test("Coeval.fromEval(Eval.always(v))") { _ =>
    val effect = Atomic(0)
    val eval = Coeval.fromEval(Eval.always(effect.incrementAndGet()))

    assertEquals(eval.value, 1)
    assertEquals(eval.value, 2)
    assertEquals(eval.value, 3)
  }

  test("Coeval.fromEval(Eval.later(v))") { _ =>
    val effect = Atomic(0)
    val eval = Coeval.fromEval(Eval.later(effect.incrementAndGet()))

    assertEquals(eval.value, 1)
    assertEquals(eval.value, 1)
  }
}
