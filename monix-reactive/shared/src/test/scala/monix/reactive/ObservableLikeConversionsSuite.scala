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

package monix.reactive

import cats.Eval
import cats.effect.{ContextShift, IO, SyncIO}
import monix.catnap.SchedulerEffect
import monix.eval.TaskConversionsSuite.{CIO, CustomConcurrentEffect, CustomEffect}
import monix.eval.{Coeval, Task}
import monix.execution.exceptions.DummyException
import org.reactivestreams.{Publisher, Subscriber, Subscription}

import scala.concurrent.Promise
import scala.util.{Failure, Success, Try}

object ObservableLikeConversionsSuite extends BaseTestSuite {
  test("Observable.from(future)") { implicit s =>
    val p = Promise[Int]()
    val f = Observable.from(p.future).runAsyncGetFirst

    s.tick()
    assertEquals(f.value, None)

    p.success(1)
    s.tick()
    assertEquals(f.value, Some(Success(Some(1))))
  }

  test("Observable.from(future) for errors") { implicit s =>
    val p = Promise[Int]()
    val dummy = DummyException("dummy")
    val f = Observable.from(p.future).runAsyncGetFirst

    s.tick()
    assertEquals(f.value, None)

    p.failure(dummy)
    s.tick()
    assertEquals(f.value, Some(Failure(dummy)))
  }

  test("Observable.from(IO)") { implicit s =>
    implicit val cs: ContextShift[IO] = SchedulerEffect.contextShift[IO](s)(IO.ioEffect)

    val p = Promise[Int]()
    val f = Observable.from(IO.fromFuture(IO.pure(p.future))).runAsyncGetFirst

    s.tick()
    assertEquals(f.value, None)

    p.success(1)
    s.tick()
    assertEquals(f.value, Some(Success(Some(1))))
  }

  test("Observable.from(IO) for errors") { implicit s =>
    implicit val cs: ContextShift[IO] = SchedulerEffect.contextShift[IO](s)(IO.ioEffect)

    val p = Promise[Int]()
    val dummy = DummyException("dummy")
    val f = Observable.from(IO.fromFuture(IO.pure(p.future))).runAsyncGetFirst

    s.tick()
    assertEquals(f.value, None)

    p.failure(dummy)
    s.tick()
    assertEquals(f.value, Some(Failure(dummy)))
  }

  test("Observable.from(Task)") { implicit s =>
    val p = Promise[Int]()
    val f = Observable.from(Task.fromFuture(p.future)).runAsyncGetFirst

    s.tick()
    assertEquals(f.value, None)

    p.success(1)
    s.tick()
    assertEquals(f.value, Some(Success(Some(1))))
  }

  test("Observable.from(Task) for errors") { implicit s =>
    val p = Promise[Int]()
    val dummy = DummyException("dummy")
    val f = Observable.from(Task.fromFuture(p.future)).runAsyncGetFirst

    s.tick()
    assertEquals(f.value, None)

    p.failure(dummy)
    s.tick()
    assertEquals(f.value, Some(Failure(dummy)))
  }

  test("Observable.from(Observable)") { _ =>
    val source = Observable(1)
    val conv = Observable.from(source)
    assertEquals(source, conv)
  }

  test("Observable.from(Coeval)") { implicit s =>
    var effect = false
    val source = Coeval { effect = true; 1 }
    val conv = Observable.from(source)
    assert(!effect)

    val f = conv.runAsyncGetFirst
    s.tick()
    assertEquals(f.value, Some(Success(Some(1))))
    assert(effect)
  }

  test("Observable.from(Coeval) for errors") { implicit s =>
    var effect = false
    val dummy = DummyException("dummy")
    val source = Coeval[Int] { effect = true; throw dummy }
    val conv = Observable.from(source)
    assert(!effect)

    val f = conv.runAsyncGetFirst
    s.tick()
    assertEquals(f.value, Some(Failure(dummy)))
    assert(effect)
  }

  test("Observable.from(Eval)") { implicit s =>
    var effect = false
    val source = Eval.always { effect = true; 1 }
    val conv = Observable.from(source)
    assert(!effect)

    val f = conv.runAsyncGetFirst
    s.tick()
    assertEquals(f.value, Some(Success(Some(1))))
    assert(effect)
  }

  test("Observable.from(Eval) for errors") { implicit s =>
    var effect = false
    val dummy = DummyException("dummy")
    val source = Eval.always[Int] { effect = true; throw dummy }
    val conv = Observable.from(source)
    assert(!effect)

    val f = conv.runAsyncGetFirst
    s.tick()
    assertEquals(f.value, Some(Failure(dummy)))
    assert(effect)
  }

  test("Observable.from(SyncIO)") { implicit s =>
    var effect = false
    val source = SyncIO { effect = true; 1 }
    val conv = Observable.from(source)
    assert(!effect)

    val f = conv.runAsyncGetFirst
    s.tick()
    assertEquals(f.value, Some(Success(Some(1))))
    assert(effect)
  }

  test("Observable.from(SyncIO) for errors") { implicit s =>
    var effect = false
    val dummy = DummyException("dummy")
    val source = SyncIO.defer[Int] { effect = true; SyncIO.raiseError(dummy) }
    val conv = Observable.from(source)
    assert(!effect)

    val f = conv.runAsyncGetFirst
    s.tick()
    assertEquals(f.value, Some(Failure(dummy)))
    assert(effect)
  }

  test("Observable.from(Try)") { implicit s =>
    val source = Success(1): Try[Int]
    val conv = Observable.from(source)
    assertEquals(conv.runAsyncGetFirst.value, Some(Success(Some(1))))
  }

  test("Observable.from(Try) for errors") { implicit s =>
    val dummy = DummyException("dummy")
    val source = Failure(dummy): Try[Int]
    val conv = Observable.from(source)
    assertEquals(conv.runAsyncGetFirst.value, Some(Failure(dummy)))
  }

  test("Observable.from(Either)") { implicit s =>
    val source: Either[Throwable, Int] = Right(1)
    val conv = Observable.from(source)
    assertEquals(conv.runAsyncGetFirst.value, Some(Success(Some(1))))
  }

  test("Observable.from(Either) for errors") { implicit s =>
    val dummy = DummyException("dummy")
    val source: Either[Throwable, Int] = Left(dummy)
    val conv = Observable.from(source)
    assertEquals(conv.runAsyncGetFirst.value, Some(Failure(dummy)))
  }

  test("Observable.from(custom Effect)") { implicit s =>
    implicit val cs: ContextShift[IO] = SchedulerEffect.contextShift[IO](s)(IO.ioEffect)
    implicit val F: CustomEffect = new CustomEffect()

    var effect = false
    val source = CIO(IO { effect = true; 1 })
    val conv = Observable.from(source)

    assert(!effect)
    val f = conv.runAsyncGetFirst
    s.tick()
    assert(effect)
    assertEquals(f.value, Some(Success(Some(1))))
  }

  test("Observable.from(custom Effect) for errors") { implicit s =>
    implicit val cs: ContextShift[IO] = SchedulerEffect.contextShift[IO](s)(IO.ioEffect)
    implicit val F: CustomEffect = new CustomEffect()

    var effect = false
    val dummy = DummyException("dummy")
    val source = CIO(IO { effect = true; throw dummy })
    val conv = Observable.from(source)

    assert(!effect)
    val f = conv.runAsyncGetFirst
    s.tick()
    assert(effect)
    assertEquals(f.value, Some(Failure(dummy)))
  }

  test("Observable.from(custom ConcurrentEffect)") { implicit s =>
    implicit val cs: ContextShift[IO] = SchedulerEffect.contextShift[IO](s)(IO.ioEffect)
    implicit val F: CustomConcurrentEffect = new CustomConcurrentEffect()

    var effect = false
    val source = CIO(IO { effect = true; 1 })
    val conv = Observable.from(source)

    assert(!effect)
    val f = conv.runAsyncGetFirst
    s.tick()
    assert(effect)
    assertEquals(f.value, Some(Success(Some(1))))
  }

  test("Observable.from(custom ConcurrentEffect) for errors") { implicit s =>
    implicit val cs: ContextShift[IO] = SchedulerEffect.contextShift[IO](s)(IO.ioEffect)
    implicit val F: CustomConcurrentEffect = new CustomConcurrentEffect()

    var effect = false
    val dummy = DummyException("dummy")
    val source = CIO(IO { effect = true; throw dummy })
    val conv = Observable.from(source)

    assert(!effect)
    val f = conv.runAsyncGetFirst
    s.tick()
    assert(effect)
    assertEquals(f.value, Some(Failure(dummy)))
  }

  test("Observable.from(ReactivePublisher)") { implicit s =>
    val pub = new Publisher[Int] {
      def subscribe(s: Subscriber[_ >: Int]): Unit = {
        s.onSubscribe(new Subscription {
          var isActive = true
          def request(n: Long): Unit = {
            if (n > 0 && isActive) {
              isActive = false
              s.onNext(1)
              s.onComplete()
            }
          }
          def cancel(): Unit = {
            isActive = false
          }
        })
      }
    }

    val conv = Observable.from(pub)
    val f = conv.runAsyncGetFirst
    s.tick()
    assertEquals(f.value, Some(Success(Some(1))))
  }

  test("Observable.from(Iterable)") { implicit s =>
    val iter = List(1, 2, 3, 4)
    val conv = Observable.from(iter)
    val f = conv.toListL.runToFuture

    s.tick()
    assertEquals(f.value, Some(Success(List(1, 2, 3, 4))))
  }

  test("Task.from(Function0)") { implicit s =>
    val task = Observable.from(() => 1).firstL
    val f = task.runToFuture
    s.tick()
    assertEquals(f.value, Some(Success(1)))
  }
}
