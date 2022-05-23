/*
 * Copyright (c) 2014-2021 by The Monix Project Developers.
 * See the project homepage at: https://monix.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package monix.eval

import cats.Eval
import cats.effect.IO
import monix.execution.atomic.Atomic
import monix.execution.exceptions.DummyException
import scala.util.{ Failure, Success }

object CoevalCatsConversions extends BaseTestSuite {
  test("Coeval.now(value).to[Eval]") { _ =>
    assertEquals(Coeval.now(10).to[Eval].value, 10)
  }

  test("Coeval.raiseError(e).to[Eval]") { _ =>
    val dummy = DummyException("dummy")
    val eval = Coeval.raiseError[Unit](dummy).to[Eval]
    intercept[DummyException] { eval.value; () }
    ()
  }

  test("Coeval.eval(thunk).to[Eval]") { _ =>
    val effect = Atomic(0)
    val eval = Coeval.eval(effect.incrementAndGet()).to[Eval]

    assertEquals(eval.value, 1)
    assertEquals(eval.value, 2)
  }

  test("Coeval.evalOnce(thunk).to[Eval]") { _ =>
    val effect = Atomic(0)
    val eval = Coeval.evalOnce(effect.incrementAndGet()).to[Eval]

    assertEquals(eval.value, 1)
    assertEquals(eval.value, 1)
  }

  test("Coeval.now(value).to[IO]") { _ =>
    assertEquals(Coeval.now(10).to[IO].unsafeRunSync(), 10)
  }

  test("Coeval.raiseError(e).to[IO]") { _ =>
    val dummy = DummyException("dummy")
    val ioRef = Coeval.raiseError[Unit](dummy).to[IO]
    intercept[DummyException] { ioRef.unsafeRunSync(); () }
    ()
  }

  test("Coeval.eval(thunk).to[IO]") { _ =>
    val effect = Atomic(0)
    val ioRef = Coeval.eval(effect.incrementAndGet()).to[IO]

    assertEquals(ioRef.unsafeRunSync(), 1)
    assertEquals(ioRef.unsafeRunSync(), 2)
  }

  test("Coeval.evalOnce(thunk).to[IO]") { _ =>
    val effect = Atomic(0)
    val eval = Coeval.evalOnce(effect.incrementAndGet()).to[IO]

    assertEquals(eval.unsafeRunSync(), 1)
    assertEquals(eval.unsafeRunSync(), 1)
  }

  test("Coeval.from(Eval.now(v))") { _ =>
    assertEquals(Coeval.from(Eval.now(10)), Coeval.Now(10))
  }

  test("Coeval.from(Eval.always(v))") { _ =>
    val effect = Atomic(0)
    val eval = Coeval.from(Eval.always(effect.incrementAndGet()))

    assertEquals(eval.value(), 1)
    assertEquals(eval.value(), 2)
    assertEquals(eval.value(), 3)
  }

  test("Coeval.from(Eval.later(v))") { _ =>
    val effect = Atomic(0)
    val eval = Coeval.from(Eval.later(effect.incrementAndGet()))

    assertEquals(eval.value(), 1)
    assertEquals(eval.value(), 1)
  }

  test("Coeval.from protects against user error") { implicit s =>
    val dummy = DummyException("dummy")
    val eval = Coeval.from(Eval.always { throw dummy })
    assertEquals(eval.runTry(), Failure(dummy))
  }

  test("Coeval().toSync[IO]") { _ =>
    var effect = 0
    val test = Coeval { effect += 1; effect }
    val io = test.toSync[IO]

    assertEquals(effect, 0)
    assertEquals(io.unsafeRunSync(), 1)
    assertEquals(io.unsafeRunSync(), 2)
  }

  test("Coeval().toSync[IO]") { _ =>
    var effect = 0
    val test = Coeval { effect += 1; effect }
    val io = test.toSync[IO]

    assertEquals(effect, 0)
    assertEquals(io.unsafeRunSync(), 1)
    assertEquals(io.unsafeRunSync(), 2)
  }

  test("Coeval().toSync[Task]") { implicit s =>
    var effect = 0
    val test = Coeval { effect += 1; effect }
    val task = test.toSync[Task]

    assertEquals(effect, 0)
    assertEquals(task.runToFuture.value, Some(Success(1)))
    assertEquals(task.runToFuture.value, Some(Success(2)))
  }

  test("Coeval.liftTo[Task]") { implicit s =>
    var effect = 0
    val test = Coeval { effect += 1; effect }
    val task = Coeval.liftTo[Task].apply(test)

    assertEquals(effect, 0)
    assertEquals(task.runToFuture.value, Some(Success(1)))
    assertEquals(task.runToFuture.value, Some(Success(2)))
  }

  test("Coeval.liftToSync[IO]") { _ =>
    var effect = 0
    val test = Coeval { effect += 1; effect }
    val io = Coeval.liftToSync[IO].apply(test)

    assertEquals(effect, 0)
    assertEquals(io.unsafeRunSync(), 1)
    assertEquals(io.unsafeRunSync(), 2)
  }

  test("Coeval().to[Coeval]") { _ =>
    val ref1 = Coeval { 1 + 1 }
    val ref2 = ref1.to[Coeval]
    assertEquals(ref1, ref2)
  }

  test("Coeval().toSync[Coeval]") { _ =>
    val ref1 = Coeval { 1 + 1 }
    val ref2 = ref1.toSync[Coeval]
    assertEquals(ref1.value(), ref2.value())
  }
}
