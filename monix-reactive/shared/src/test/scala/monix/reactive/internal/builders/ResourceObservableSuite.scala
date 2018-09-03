/*
 * Copyright (c) 2014-2018 by The Monix Project Developers.
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

package monix.reactive.internal.builders

import cats.laws._
import cats.laws.discipline._
import monix.eval.Task
import monix.execution.Ack.Continue
import monix.execution.exceptions.DummyException
import monix.reactive.observers.Subscriber
import monix.reactive.{BaseTestSuite, Consumer, Observable}

import scala.util.Failure

object ResourceObservableSuite extends BaseTestSuite {
  class Resource(var acquired: Int = 0, var released: Int = 0) {
    def acquire: Task[Handle] =
      Task { acquired += 1 }.map(_ => Handle(this))
  }

  case class Handle(r: Resource) {
    def release = Task { r.released += 1 }
  }

  test("Observable.resource.flatMap(use) yields all elements `use` provides") { implicit s =>
    check1 { source: Observable[Int] =>
      val bracketed = Observable.resource(Task.unit)(_ => Task.unit).flatMap(_ => source)
      source <-> bracketed
    }
  }

  test("Observable.resource.flatMap(use) preserves earlyStop of stream returned from `use`") { implicit s =>
    var earlyStopDone = false
    val bracketed = Observable.resource(Task.unit)(_ => Task.unit)
      .flatMap(_ => Observable(1, 2, 3).doOnEarlyStopTask(Task {
        earlyStopDone = true
      }))

    bracketed.take(1).completedL.runAsync
    s.tick()
    assert(earlyStopDone)
  }

  test("Observable.resource releases resource on normal completion") { implicit s =>
    val rs = new Resource
    val bracketed = Observable.resource(rs.acquire)(_.release)
      .flatMap(_ => Observable.range(1, 10))

    bracketed.completedL.runAsync
    s.tick()
    assertEquals(rs.acquired, 1)
    assertEquals(rs.released, 1)
  }

  test("Observable.resource releases resource on early stop") { implicit s =>
    val rs = new Resource
    val bracketed = Observable
      .resource(rs.acquire)(_.release)
      .flatMap(_ => Observable.range(1, 10))
      .take(1)

    bracketed.completedL.runAsync
    s.tick()
    assertEquals(rs.acquired, 1)
    assertEquals(rs.released, 1)
  }

  test("Observable.resource should be cancelable") { implicit s =>
    val rs = new Resource
    var wasCompleted = false

    val cancelable = Observable
      .resource(rs.acquire)(_.release)
      .unsafeSubscribeFn(
        new Subscriber[Handle] {
          implicit val scheduler = s

          def onNext(elem: Handle) = Continue
          def onComplete() = wasCompleted = true
          def onError(ex: Throwable) = wasCompleted = true
        })

    cancelable.cancel()
    s.tick()

    assert(!wasCompleted)
    assertEquals(rs.acquired, 1)
    assertEquals(rs.released, 1)
  }

  test("Observable.resource releases resource on exception") { implicit s =>
    val rs = new Resource
    val error = DummyException("dummy")

    val bracketed = Observable.resource(rs.acquire)(_.release)
      .flatMap { _ =>
        Observable.range(1, 10) ++ Observable.raiseError[Long](error)
      }

    val f = bracketed.completedL.runAsync
    s.tick()

    assertEquals(rs.acquired, 1)
    assertEquals(rs.released, 1)
    assertEquals(f.value, Some(Failure(error)))
  }

  test("Observable.resource.flatMap(use) releases resource if `use` throws") { implicit s =>
    val rs = new Resource
    val dummy = DummyException("dummy")
    val bracketed = Observable.resource(rs.acquire)(_.release)
      .flatMap { _ => throw dummy }

    val f = bracketed.completedL.runAsync
    s.tick()

    assertEquals(rs.acquired, 1)
    assertEquals(rs.released, 1)
    assertEquals(f.value, Some(Failure(dummy)))
  }

  test("Observable.resource does not call `release` if `acquire` has an error") { implicit s =>
    val rs = new Resource
    val dummy = DummyException("dummy")
    val bracketed = Observable
      .resource(Task.raiseError(dummy).flatMap(_ => rs.acquire))(_.release)
      .flatMap { _ =>
        Observable.empty[Int]
      }

    val f = bracketed.completedL.runAsync
    s.tick()

    assertEquals(rs.acquired, 0)
    assertEquals(rs.released, 0)
    assertEquals(f.value, Some(Failure(dummy)))
  }

  test("resource(r)(_ => raiseError(e)).flatMap(_ => fa) <-> fa ++ raiseError(e)") { implicit s =>
    val dummy = DummyException("dummy")
    check1 { (fa: Observable[Int]) =>
      val lh = Observable.resource(Task.unit)(_ => Task.raiseError(dummy)).flatMap(_ => fa)
      lh <-> fa ++ Observable.raiseError[Int](dummy)
    }
  }

  test("Observable.resource nesting: outer releases even if inner release fails") { implicit s =>
    var released = false
    val dummy = DummyException("dummy")
    val bracketed = Observable.resource(Task.unit)(_ => Task {
      released = true
    })
      .flatMap { _ =>
        Observable.resource(Task.unit)(_ => Task.raiseError(dummy))
          .flatMap(_ => Observable(1, 2, 3))
      }

    val f = bracketed.completedL.runAsync
    s.tick()

    assertEquals(f.value, Some(Failure(dummy)))
    assert(released)
  }

  test("Observable.resource.flatMap(child) calls release when child is broken") { implicit s =>
    var released = false
    val dummy = DummyException("dummy")
    val bracketed = Observable.resource(Task.unit)(_ => Task {
      released = true
    })
      .flatMap { _ =>
        Observable.suspend[Int](Observable.raiseError(dummy))
      }

    val f = bracketed.completedL.runAsync
    s.tick()

    assertEquals(f.value, Some(Failure(dummy)))
    assert(released)
  }

  test("Observable.resource nesting: inner releases even if outer release fails") { implicit s =>
    var released = false
    val dummy = DummyException("dummy")
    val bracketed = Observable.resource(Task.unit)(_ => Task.raiseError(dummy))
      .flatMap { _ =>
        Observable.resource(Task.unit)(_ => Task {
          released = true
        })
          .flatMap(_ => Observable(1, 2, 3))
      }

    val f = bracketed.completedL.runAsync
    s.tick()

    assertEquals(f.value, Some(Failure(dummy)))
    assert(released)
  }

  test("Observable.resource releases resource on all completion methods") { implicit s =>
    val rs = new Resource
    val completes: Array[Observable[Int] => Task[Unit]] =
      Array(
        _.completedL,
        _.consumeWith(Consumer.complete),
        _.lastOrElseL(()).map(_ => ()),
        _.findL(_ => true).map(_ => ()),
        _.foldL.map(_ => ()),
        _.foldWhileLeftL(())((_, _) => Left(())),
        _.firstOrElseL(()).map(_ => ()),
        _.forAllL(_ => true).map(_ => ()),
        _.existsL(_ => true).map(_ => ()),
        _.foldLeftL(())((_, _) => ()),
        _.headOrElseL(()).map(_ => ()),
        _.maxL.map(_ => ()),
        _.maxByL(identity).map(_ => ()),
        _.minL.map(_ => ()),
        _.minByL(identity).map(_ => ()),
        _.sumL.map(_ => ())
      )

    val pure = Observable.resource(rs.acquire)(_.release)
      .flatMap(_ => Observable(1, 2, 3))

    for (method <- completes) {
      method(pure).runAsync
      s.tick()
    }

    assertEquals(rs.acquired, completes.length)
    assertEquals(rs.released, completes.length)

    val dummy = DummyException("dummy")
    val faulty = Observable.resource(rs.acquire)(_.release)
      .flatMap(_ => Observable.raiseError[Int](dummy))

    for (method <- completes) {
      val f = method(faulty).runAsync
      s.tick()
      assertEquals(f.value, Some(Failure(dummy)))
    }

    assertEquals(rs.acquired, completes.length * 2)
    assertEquals(rs.released, completes.length * 2)

    val broken = Observable.resource(rs.acquire)(_.release)
      .flatMap(_ => Observable.suspend[Int](Observable.raiseError(dummy)))

    for (method <- completes) {
      val f = method(broken).runAsync
      s.tick()
      assertEquals(f.value, Some(Failure(dummy)))
    }

    assertEquals(rs.acquired, completes.length * 3)
    assertEquals(rs.released, completes.length * 3)
  }

  test("Observable.resource does not require non-strict use") { implicit s =>
    var log = Vector[String]()

    def safeCloseable(key: String): Observable[Unit] =
      Observable
        .resource(Task {
          log :+= s"Start: $key"
        })(_ => Task {
          log :+= s"Stop: $key"
        })
        .flatMap(Observable.pure)

    val observable = for {
      _ <- safeCloseable("Outer")
      _ <- safeCloseable("Inner")
    } yield ()

    observable.completedL.runAsync
    s.tick()
    assertEquals(log, Vector("Start: Outer", "Start: Inner", "Stop: Inner", "Stop: Outer"))
  }
}