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

package monix.eval

import monix.execution.exceptions.DummyException
import monix.execution.internal.Platform
import scala.concurrent.Promise
import scala.util.{Failure, Success}

object TaskOrCoevalTransformWithSuite extends BaseTestSuite {
  test("Task.materialize flatMap loop") { implicit s =>
    val count = if (Platform.isJVM) 10000 else 1000

    def loop[A](source: Task[A], n: Int): Task[A] =
      source.materialize.flatMap {
        case Success(a) =>
          if (n <= 0) Task.now(a)
          else loop(source, n - 1)
        case Failure(ex) =>
          Task.raiseError(ex)
      }

    val f = loop(Task.eval("value"), count).runAsync

    s.tick()
    assertEquals(f.value, Some(Success("value")))
  }

  test("Task.materialize foldLeft sequence") { implicit s =>
    val count = if (Platform.isJVM) 10000 else 1000

    val loop = (0 until count).foldLeft(Task.eval(0)) { (acc, _) =>
      acc.materialize.flatMap {
        case Success(x) =>
          Task.now(x + 1)
        case Failure(ex) =>
          Task.raiseError(ex)
      }
    }

    val f = loop.runAsync
    s.tick()
    assertEquals(f.value, Some(Success(count)))
  }

  test("Task.eval(throw).materialize (callback)") { implicit s =>
    val dummy = DummyException("dummy")
    val task = Task.eval[Int](throw dummy).materialize.map {
      case Failure(`dummy`) => 100
      case _ => 0
    }

    val p = Promise[Int]()
    task.runOnComplete(r => p.complete(r))

    s.tick()
    assertEquals(p.future.value, Some(Success(100)))
  }

  test("Task.eval(throw).materialize (future)") { implicit s =>
    val dummy = DummyException("dummy")
    val task = Task.eval[Int](throw dummy).materialize.map {
      case Failure(`dummy`) => 100
      case _ => 0
    }

    val f = task.runAsync
    assertEquals(f.value, Some(Success(100)))
  }


  test("Task.eval(throw).map(...).materialize (future)") { implicit s =>
    val dummy = DummyException("dummy")
    val task = Task.eval[Int](throw dummy).map(_ + 1).materialize.map {
      case Failure(`dummy`) => 100
      case _ => 0
    }

    val f = task.runAsync
    assertEquals(f.value, Some(Success(100)))
  }

  test("Task.eval(throw).map(...).materialize (callback)") { implicit s =>
    val dummy = DummyException("dummy")
    val task = Task.eval[Int](throw dummy).map(_ + 1).materialize.map {
      case Failure(`dummy`) => 100
      case _ => 0
    }

    val p = Promise[Int]()
    task.runOnComplete(r => p.complete(r))

    s.tick()
    assertEquals(p.future.value, Some(Success(100)))
  }

  test("Task.apply(throw).materialize (callback)") { implicit s =>
    val dummy = DummyException("dummy")
    val task = Task.apply[Int](throw dummy).materialize.map {
      case Failure(`dummy`) => 100
      case _ => 0
    }

    val p = Promise[Int]()
    task.runOnComplete(r => p.complete(r))

    s.tick()
    assertEquals(p.future.value, Some(Success(100)))
  }

  test("Task.apply(throw).materialize (future)") { implicit s =>
    val dummy = DummyException("dummy")
    val task = Task.apply[Int](throw dummy).materialize.map {
      case Failure(`dummy`) => 100
      case _ => 0
    }

    val f = task.runAsync; s.tick()
    assertEquals(f.value, Some(Success(100)))
  }


  test("Task.apply(throw).map(...).materialize (future)") { implicit s =>
    val dummy = DummyException("dummy")
    val task = Task.apply[Int](throw dummy).map(_ + 1).materialize.map {
      case Failure(`dummy`) => 100
      case _ => 0
    }

    val f = task.runAsync; s.tick()
    assertEquals(f.value, Some(Success(100)))
  }

  test("Task.apply(throw).map(...).materialize (callback)") { implicit s =>
    val dummy = DummyException("dummy")
    val task = Task.apply[Int](throw dummy).map(_ + 1).materialize.map {
      case Failure(`dummy`) => 100
      case _ => 0
    }

    val p = Promise[Int]()
    task.runOnComplete(r => p.complete(r))

    s.tick()
    assertEquals(p.future.value, Some(Success(100)))
  }
  
  test("Task.suspend(throw).materialize (callback)") { implicit s =>
    val dummy = DummyException("dummy")
    val task = Task.suspend[Int](throw dummy).materialize.map {
      case Failure(`dummy`) => 100
      case _ => 0
    }

    val p = Promise[Int]()
    task.runOnComplete(r => p.complete(r))

    s.tick()
    assertEquals(p.future.value, Some(Success(100)))
  }

  test("Task.suspend(throw).materialize (future)") { implicit s =>
    val dummy = DummyException("dummy")
    val task = Task.suspend[Int](throw dummy).materialize.map {
      case Failure(`dummy`) => 100
      case _ => 0
    }

    val f = task.runAsync; s.tick()
    assertEquals(f.value, Some(Success(100)))
  }


  test("Task.suspend(throw).map(...).materialize (future)") { implicit s =>
    val dummy = DummyException("dummy")
    val task = Task.suspend[Int](throw dummy).map(_ + 1).materialize.map {
      case Failure(`dummy`) => 100
      case _ => 0
    }

    val f = task.runAsync; s.tick()
    assertEquals(f.value, Some(Success(100)))
  }

  test("Task.suspend(throw).map(...).materialize (callback)") { implicit s =>
    val dummy = DummyException("dummy")
    val task = Task.suspend[Int](throw dummy).map(_ + 1).materialize.map {
      case Failure(`dummy`) => 100
      case _ => 0
    }

    val p = Promise[Int]()
    task.runOnComplete(r => p.complete(r))

    s.tick()
    assertEquals(p.future.value, Some(Success(100)))
  }
  
  test("Task(throw).memoize.materialize (callback)") { implicit s =>
    val dummy = DummyException("dummy")
    val task = Task[Int](throw dummy).memoize.materialize.map {
      case Failure(`dummy`) => 100
      case _ => 0
    }

    val p = Promise[Int]()
    task.runOnComplete(r => p.complete(r))

    s.tick()
    assertEquals(p.future.value, Some(Success(100)))
  }

  test("Task(throw).memoize.materialize (future)") { implicit s =>
    val dummy = DummyException("dummy")
    val task = Task[Int](throw dummy).memoize.materialize.map {
      case Failure(`dummy`) => 100
      case _ => 0
    }

    val f = task.runAsync; s.tick()
    assertEquals(f.value, Some(Success(100)))
  }


  test("Task(throw).memoize.map(...).materialize (future)") { implicit s =>
    val dummy = DummyException("dummy")
    val task = Task[Int](throw dummy).memoize.map(_ + 1).materialize.map {
      case Failure(`dummy`) => 100
      case _ => 0
    }

    val f = task.runAsync; s.tick()
    assertEquals(f.value, Some(Success(100)))
  }

  test("Task(throw).memoize.map(...).materialize (callback)") { implicit s =>
    val dummy = DummyException("dummy")
    val task = Task[Int](throw dummy).memoize.map(_ + 1).materialize.map {
      case Failure(`dummy`) => 100
      case _ => 0
    }

    val p = Promise[Int]()
    task.runOnComplete(r => p.complete(r))

    s.tick()
    assertEquals(p.future.value, Some(Success(100)))
  }
  
  test("Task.raiseError.materialize (callback)") { implicit s =>
    val dummy = DummyException("dummy")
    val task = Task.raiseError[Int](dummy).materialize.map {
      case Failure(`dummy`) => 100
      case _ => 0
    }

    val p = Promise[Int]()
    task.runOnComplete(r => p.complete(r))

    s.tick()
    assertEquals(p.future.value, Some(Success(100)))
  }

  test("Task.raiseError.materialize (future)") { implicit s =>
    val dummy = DummyException("dummy")
    val task = Task.raiseError[Int](dummy).materialize.map {
      case Failure(`dummy`) => 100
      case _ => 0
    }

    val f = task.runAsync
    assertEquals(f.value, Some(Success(100)))
  }

  test("Task.raiseError.map(...).materialize (future)") { implicit s =>
    val dummy = DummyException("dummy")
    val task = Task.raiseError[Int](dummy).map(_ + 1).materialize.map {
      case Failure(`dummy`) => 100
      case _ => 0
    }

    val f = task.runAsync
    assertEquals(f.value, Some(Success(100)))
  }

  test("Task.raiseError.map(...).materialize (callback)") { implicit s =>
    val dummy = DummyException("dummy")
    val task = Task.raiseError[Int](dummy).map(_ + 1).materialize.map {
      case Failure(`dummy`) => 100
      case _ => 0
    }

    val p = Promise[Int]()
    task.runOnComplete(r => p.complete(r))

    s.tick()
    assertEquals(p.future.value, Some(Success(100)))
  }
  
  test("Coeval.materialize flatMap loop") { _ =>
    val count = if (Platform.isJVM) 10000 else 1000

    def loop[A](source: Coeval[A], n: Int): Coeval[A] =
      source.materialize.flatMap {
        case Success(a) =>
          if (n <= 0) Coeval.now(a)
          else loop(source, n - 1)
        case Failure(ex) =>
          Coeval.raiseError(ex)
      }

    val f = loop(Coeval.eval("value"), count).runTry
    assertEquals(f, Success("value"))
  }

  test("Coeval.materialize foldLeft sequence") { _ =>
    val count = if (Platform.isJVM) 10000 else 1000

    val loop = (0 until count).foldLeft(Coeval.eval(0)) { (acc, _) =>
      acc.materialize.flatMap {
        case Success(x) =>
          Coeval.now(x + 1)
        case Failure(ex) =>
          Coeval.raiseError(ex)
      }
    }

    assertEquals(loop.runTry, Success(count))
  }

  test("Coeval.eval(throw).materialize") { _ =>
    val dummy = DummyException("dummy")
    val coeval = Coeval.eval[Int](throw dummy).materialize.map {
      case Failure(`dummy`) => 100
      case _ => 0
    }

    assertEquals(coeval.runTry, Success(100))
  }

  test("Coeval.eval(throw).map(...).materialize") { _ =>
    val dummy = DummyException("dummy")
    val coeval = Coeval.eval[Int](throw dummy).map(_ + 1).materialize.map {
      case Failure(`dummy`) => 100
      case _ => 0
    }

    assertEquals(coeval.runTry, Success(100))
  }

  test("Coeval.apply(throw).materialize") { _ =>
    val dummy = DummyException("dummy")
    val coeval = Coeval.apply[Int](throw dummy).materialize.map {
      case Failure(`dummy`) => 100
      case _ => 0
    }

    assertEquals(coeval.runTry, Success(100))
  }

  test("Coeval.apply(throw).map(...).materialize") { _ =>
    val dummy = DummyException("dummy")
    val coeval = Coeval.apply[Int](throw dummy).map(_ + 1).materialize.map {
      case Failure(`dummy`) => 100
      case _ => 0
    }

    assertEquals(coeval.runTry, Success(100))
  }

  test("Coeval.suspend(throw).materialize") { _ =>
    val dummy = DummyException("dummy")
    val coeval = Coeval.suspend[Int](throw dummy).materialize.map {
      case Failure(`dummy`) => 100
      case _ => 0
    }

    assertEquals(coeval.runTry, Success(100))
  }

  test("Coeval.suspend(throw).map(...).materialize") { _ =>
    val dummy = DummyException("dummy")
    val coeval = Coeval.suspend[Int](throw dummy).map(_ + 1).materialize.map {
      case Failure(`dummy`) => 100
      case _ => 0
    }

    assertEquals(coeval.runTry, Success(100))
  }

  test("Coeval(throw).memoize.materialize") { _ =>
    val dummy = DummyException("dummy")
    val coeval = Coeval[Int](throw dummy).memoize.materialize.map {
      case Failure(`dummy`) => 100
      case _ => 0
    }

    assertEquals(coeval.runTry, Success(100))
  }

  test("Coeval(throw).memoize.map(...).materialize") { _ =>
    val dummy = DummyException("dummy")
    val coeval = Coeval[Int](throw dummy).memoize.map(_ + 1).materialize.map {
      case Failure(`dummy`) => 100
      case _ => 0
    }

    assertEquals(coeval.runTry, Success(100))
  }

  test("Coeval.raiseError.materialize (future)") { _ =>
    val dummy = DummyException("dummy")
    val coeval = Coeval.raiseError[Int](dummy).materialize.map {
      case Failure(`dummy`) => 100
      case _ => 0
    }

    assertEquals(coeval.runTry, Success(100))
  }

  test("Coeval.raiseError.map(...).materialize (future)") { _ =>
    val dummy = DummyException("dummy")
    val coeval = Coeval.raiseError[Int](dummy).map(_ + 1).materialize.map {
      case Failure(`dummy`) => 100
      case _ => 0
    }

    assertEquals(coeval.runTry, Success(100))
  }
}
