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

package monix.tail

import cats.laws._
import cats.laws.discipline._
import monix.eval.Coeval
import monix.execution.exceptions.DummyException
import monix.tail.batches.{ Batch, BatchCursor }

object IterantResourceSuite extends BaseTestSuite {
  class Resource(var acquired: Int = 0, var released: Int = 0) {
    def acquire: Coeval[Handle] =
      Coeval { acquired += 1 }.map(_ => Handle(this))
  }

  case class Handle(r: Resource) {
    def release = Coeval { r.released += 1 }
  }

  test("Iterant.resource.flatMap(use) yields all elements `use` provides") { _ =>
    check1 { (source: Iterant[Coeval, Int]) =>
      val bracketed = Iterant.resource(Coeval.unit)(_ => Coeval.unit).flatMap(_ => source)
      source <-> bracketed
    }
  }

  test("Iterant.resource.flatMap(use) preserves earlyStop of stream returned from `use`") { _ =>
    var earlyStopDone = false
    val bracketed = Iterant
      .resource(Coeval.unit)(_ => Coeval.unit)
      .flatMap(_ =>
        Iterant[Coeval]
          .of(1, 2, 3)
          .guarantee(Coeval {
            earlyStopDone = true
          })
      )

    bracketed.take(1).completedL.value()
    assert(earlyStopDone)
  }

  test("Iterant.resource releases resource on normal completion") { _ =>
    val rs = new Resource
    val bracketed = Iterant
      .resource(rs.acquire)(_.release)
      .flatMap(_ => Iterant.range(1, 10))

    bracketed.completedL.value()
    assertEquals(rs.acquired, 1)
    assertEquals(rs.released, 1)
  }

  test("Iterant.resource releases resource on early stop") { _ =>
    val rs = new Resource
    val bracketed = Iterant
      .resource(rs.acquire)(_.release)
      .flatMap(_ => Iterant.range(1, 10))
      .take(1)

    bracketed.completedL.value()
    assertEquals(rs.acquired, 1)
    assertEquals(rs.released, 1)
  }

  test("Iterant.resource releases resource on exception") { _ =>
    val rs = new Resource
    val error = DummyException("dummy")

    val bracketed = Iterant.resource(rs.acquire)(_.release).flatMap { _ =>
      Iterant.range[Coeval](1, 10) ++ Iterant.raiseError[Coeval, Int](error)
    }

    intercept[DummyException] {
      bracketed.completedL.value()
      ()
    }
    assertEquals(rs.acquired, 1)
    assertEquals(rs.released, 1)
  }

  test("Iterant.resource.flatMap(use) releases resource if `use` throws") { _ =>
    val rs = new Resource
    val dummy = DummyException("dummy")
    val bracketed = Iterant.resource(rs.acquire)(_.release).flatMap { _ =>
      throw dummy
    }

    intercept[DummyException] {
      bracketed.completedL.value()
      ()
    }
    assertEquals(rs.acquired, 1)
    assertEquals(rs.released, 1)
  }

  test("Iterant.resource does not call `release` if `acquire` has an error") { _ =>
    val rs = new Resource
    val dummy = DummyException("dummy")
    val bracketed = Iterant
      .resource(Coeval.raiseError[Int](dummy).flatMap(_ => rs.acquire))(_.release)
      .flatMap { _ =>
        Iterant.empty[Coeval, Int]
      }

    intercept[DummyException] {
      bracketed.completedL.value()
      ()
    }
    assertEquals(rs.acquired, 0)
    assertEquals(rs.released, 0)
  }

  test("resource(r)(_ => raiseError(e)).flatMap(_ => fa) <-> fa ++ raiseError(e)") { _ =>
    val dummy = DummyException("dummy")
    check1 { (fa: Iterant[Coeval, Int]) =>
      val lh = Iterant.resource(Coeval.unit)(_ => Coeval.raiseError(dummy)).flatMap(_ => fa)
      val rh = fa ++ Iterant.raiseError[Coeval, Int](dummy)
      lh <-> rh
    }
  }

  test("Iterant.resource nesting: outer releases even if inner release fails") { _ =>
    var released = false
    val dummy = DummyException("dummy")
    val bracketed = Iterant.resource(Coeval.unit)(_ => Coeval { released = true }).flatMap { _ =>
      Iterant
        .resource(Coeval.unit)(_ => Coeval.raiseError(dummy))
        .flatMap(_ => Iterant[Coeval].of(1, 2, 3))
    }

    intercept[DummyException] {
      bracketed.completedL.value()
      ()
    }
    assert(released)
  }

  test("Iterant.resource.flatMap(child) calls release when child is broken") { _ =>
    var released = false
    val dummy = DummyException("dummy")
    val bracketed = Iterant.resource(Coeval.unit)(_ => Coeval { released = true }).flatMap { _ =>
      Iterant[Coeval].suspendS[Int](Coeval.raiseError(dummy))
    }

    intercept[DummyException] {
      bracketed.completedL.value()
      ()
    }
    assert(released)
  }

  test("Iterant.resource nesting: inner releases even if outer release fails") { _ =>
    var released = false
    val dummy = DummyException("dummy")
    val bracketed = Iterant.resource(Coeval.unit)(_ => Coeval.raiseError(dummy)).flatMap { _ =>
      Iterant
        .resource(Coeval.unit)(_ => Coeval { released = true })
        .flatMap(_ => Iterant[Coeval].of(1, 2, 3))
    }

    intercept[DummyException] {
      bracketed.completedL.value()
      ()
    }
    assert(released)
  }

  test("Iterant.resource handles broken batches & cursors") { _ =>
    val rs = new Resource
    val dummy = DummyException("dummy")

    def withEmpty(ctor: Coeval[Iterant[Coeval, Int]] => Iterant[Coeval, Int]) =
      Iterant.resource(rs.acquire)(_.release).flatMap(_ => ctor(Coeval(Iterant.empty)))

    val broken = Array(
      withEmpty(Iterant.nextBatchS(ThrowExceptionBatch(dummy), _)),
      withEmpty(Iterant.nextCursorS(ThrowExceptionCursor(dummy), _))
    )

    for (iter <- broken) {
      intercept[DummyException] {
        iter.completedL.value()
        ()
      }
    }

    assertEquals(rs.acquired, broken.length)
    assertEquals(rs.released, broken.length)
  }

  test("Iterant.resource handles broken `next` continuations") { _ =>
    val rs = new Resource
    val dummy = DummyException("dummy")
    def withError(ctor: Coeval[Iterant[Coeval, Int]] => Iterant[Coeval, Int]) =
      Iterant.resource(rs.acquire)(_.release).flatMap(_ => ctor(Coeval.raiseError(dummy)))

    val broken = Array(
      withError(Iterant.nextS(0, _)),
      withError(Iterant.nextBatchS(Batch(1, 2, 3), _)),
      withError(Iterant.nextCursorS(BatchCursor(1, 2, 3), _)),
      withError(Iterant.suspendS)
    )

    for (iter <- broken) {
      intercept[DummyException] {
        iter.completedL.value()
        ()
      }
    }
    assertEquals(rs.acquired, broken.length)
    assertEquals(rs.released, broken.length)
  }

  test("Iterant.resource releases resource on all completion methods") { _ =>
    val rs = new Resource
    val completes: Array[Iterant[Coeval, Int] => Coeval[Unit]] =
      Array(
        _.completedL,
        _.foldLeftL(())((_, _) => ()),
        _.foldWhileLeftL(())((_, _) => Left(())),
        _.foldWhileLeftEvalL(Coeval.unit)((_, _) => Coeval(Left(()))),
        _.headOptionL.map(_ => ()),
        _.reduceL(_ + _).map(_ => ())
      )

    val pure = Iterant
      .resource(rs.acquire)(_.release)
      .flatMap(_ => Iterant[Coeval].of(1, 2, 3))

    for (method <- completes) {
      method(pure).value()
    }

    assertEquals(rs.acquired, completes.length)
    assertEquals(rs.released, completes.length)

    val dummy = DummyException("dummy")
    val faulty = Iterant
      .resource(rs.acquire)(_.release)
      .flatMap(_ => Iterant[Coeval].raiseError[Int](dummy))

    for (method <- completes) {
      intercept[DummyException] {
        method(faulty).value()
        ()
      }
    }
    assertEquals(rs.acquired, completes.length * 2)
    assertEquals(rs.released, completes.length * 2)

    val broken = Iterant
      .resource(rs.acquire)(_.release)
      .flatMap(_ => Iterant[Coeval].suspendS[Int](Coeval.raiseError(dummy)))

    for (method <- completes) {
      intercept[DummyException] {
        method(broken).value()
        ()
      }
    }

    assertEquals(rs.acquired, completes.length * 3)
    assertEquals(rs.released, completes.length * 3)
  }

  test("Iterant.resource does not require non-strict use") { _ =>
    var log = Vector[String]()
    def safeCloseable(key: String): Iterant[Coeval, Unit] =
      Iterant[Coeval]
        .resource(Coeval { log :+= s"Start: $key" })(_ => Coeval { log :+= s"Stop: $key" })
        .flatMap(Iterant[Coeval].pure)

    val iterant = for {
      _ <- safeCloseable("Outer")
      _ <- safeCloseable("Inner")
    } yield ()

    iterant.completedL.value()
    assertEquals(log, Vector("Start: Outer", "Start: Inner", "Stop: Inner", "Stop: Outer"))
  }
}
