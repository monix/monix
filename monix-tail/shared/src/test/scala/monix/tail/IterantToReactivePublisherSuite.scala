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

package monix.tail

import cats.laws._
import cats.laws.discipline._
import cats.effect.{Effect, IO}
import monix.eval.Task
import monix.execution.ExecutionModel.AlwaysAsyncExecution
import monix.execution.exceptions.DummyException
import monix.execution.internal.Platform
import monix.execution.rstreams.SingleAssignmentSubscription
import monix.tail.batches.Batch
import org.reactivestreams.{Subscriber, Subscription}
import scala.util.{Failure, Success}

object IterantToReactivePublisherSuite extends BaseTestSuite {
  test("sum with Task and request(1)") { implicit s =>
    check1 { (stream: Iterant[Task, Int]) =>
      sum(stream, 1) <-> stream.foldLeftL(0L)(_ + _)
    }
  }

  test("sum with Task and request(2)") { implicit s =>
    check1 { (stream: Iterant[Task, Int]) =>
      sum(stream, 2) <-> stream.foldLeftL(0L)(_ + _)
    }
  }

  test("sum with Task and request(6)") { implicit s =>
    check1 { (stream: Iterant[Task, Int]) =>
      sum(stream, 2) <-> stream.foldLeftL(0L)(_ + _)
    }
  }

  test("sum with Task and request(Long.MaxValue)") { implicit s =>
    check1 { (stream: Iterant[Task, Int]) =>
      sum(stream, Long.MaxValue) <-> stream.foldLeftL(0L)(_ + _)
    }
  }

  test("stack-safety for Next nodes") { implicit s =>
    val count = if (Platform.isJVM) 100000 else 10000
    val stream = Iterant[Task].range(0, count).mapEval(_ => Task.now(1))
    val f = sum(stream, 1).runAsync

    assertEquals(f.value, None)
    s.tick()
    assertEquals(f.value, Some(Success(count)))
  }

  test("stack-safety for NextBatch nodes") { implicit s =>
    val count = if (Platform.isJVM) 100000 else 10000
    val stream = Iterant[Task].range(0, count).map(_ => 1).batched(6)
    val f = sum(stream, 1).runAsync

    assertEquals(f.value, None)
    s.tick()
    assertEquals(f.value, Some(Success(count)))
  }

  test("works with IO") { implicit s =>
    check1 { (stream: Iterant[IO, Int]) =>
      sum(stream, 1) <-> Task.fromIO(stream.foldLeftL(0L)(_ + _))
    }
  }

  test("works with any Effect") { implicit s =>
    implicit val ioEffect: Effect[IO] = new CustomIOEffect
    check1 { (stream: Iterant[IO, Int]) =>
      sum(stream, 1) <-> Task.fromEffect(stream.foldLeftL(0L)(_ + _))
    }
  }

  test("loop is cancelable") { implicit s =>
    val count = 10000
    var emitted = 0
    var wasStopped = 0
    var wasCompleted = false
    var received = 0

    val subscription = SingleAssignmentSubscription()

    Iterant[Task].range(0, count)
      .doOnEarlyStop(Task { wasStopped += 1 })
      .mapEval(x => Task.eval { emitted += 1; x })
      .toReactivePublisher
      .subscribe(new Subscriber[Int] {
        def onSubscribe(s: Subscription): Unit =
          subscription := s
        def onNext(t: Int): Unit =
          received += t
        def onError(t: Throwable): Unit =
          wasCompleted = true
        def onComplete(): Unit =
          wasCompleted = true
      })

    s.tick()
    assertEquals(emitted, 1)
    assertEquals(received, 0)

    subscription.request(10)
    s.tick()

    assertEquals(wasStopped, 0)
    assert(!wasCompleted, "!wasCompleted")
    assertEquals(emitted, 10)
    assertEquals(received, 5 * 9)

    for (i <- 0 until 4) {
      if (i % 2 == 0) subscription.cancel()
      else subscription.request(10)

      s.tick()
      assertEquals(wasStopped, 1)
      assert(!wasCompleted, "!wasCompleted")
      assertEquals(received, 5 * 9)
    }
  }

  test("loop is cancellable in flight") { implicit s =>
    val count = 10000
    var effect = 0
    var wasStopped = false

    val source = Iterant[Task].range(0, count)
      .doOnEarlyStop(Task { wasStopped = true })
      .mapEval(_ => Task.eval { effect += 1; 1 })

    val f = sum(source, Long.MaxValue).runAsync
    assert(effect > 0 && effect < count, s"$effect > 0 && $effect < $count (count)")

    f.cancel()
    s.tick()

    assert(effect < count, s"$effect < $count (count)")
    assert(wasStopped, "wasStopped")
    assertEquals(f.value, None)
    assert(s.state.tasks.isEmpty, "tasks.isEmpty")
  }

  test("long batch is cancelable in flight") { s =>
    implicit val ec = s.withExecutionModel(AlwaysAsyncExecution)

    val count = 1000
    var effect = 0
    var wasStopped = false

    val batch = Batch.fromIterable(Iterable.range(0, count), Int.MaxValue)
    val source = Iterant[Task].nextBatchS(batch, Task.pure(Iterant[Task].empty[Int]), Task.unit)
      .doOnEarlyStop(Task { wasStopped = true })
      .map { _ => effect += 1; 1 }

    val f = sum(source, 16).runAsync
    var times = 1000
    while (effect == 0 && times > 0) { s.tickOne(); times -= 1 }

    assert(effect > 0 && effect < count, s"$effect > 0 && $effect < $count (count)")

    f.cancel()
    s.tick()

    assert(effect < count, s"$effect < $count (count)")
    assert(wasStopped, "wasStopped")
    assertEquals(f.value, None)
    assert(s.state.tasks.isEmpty, "tasks.isEmpty")
  }

  test("protects against invalid request") { implicit s =>
    val count = 10000
    var emitted = 0
    var wasStopped = 0
    var wasCompleted: Option[Throwable] = null
    var received = 0

    val subscription = SingleAssignmentSubscription()

    Iterant[Task].range(0, count)
      .doOnEarlyStop(Task { wasStopped += 1 })
      .mapEval(_ => Task.eval { emitted += 1; 1 })
      .toReactivePublisher
      .subscribe(new Subscriber[Int] {
        def onSubscribe(s: Subscription): Unit =
          subscription := s
        def onNext(t: Int): Unit =
          received += t
        def onError(t: Throwable): Unit =
          wasCompleted = Some(t)
        def onComplete(): Unit =
          wasCompleted = None
      })

    s.tick()
    assertEquals(emitted, 1)

    subscription.request(10)
    s.tick()

    assertEquals(emitted, 10)
    assertEquals(wasStopped, 0)
    assertEquals(wasCompleted, null)
    assertEquals(received, 10)

    subscription.request(0)
    s.tick()

    assertEquals(wasStopped, 1)
    assert(
      wasCompleted.exists(_.isInstanceOf[IllegalArgumentException]),
      "wasCompleted == Some(_: IllegalArgumentException)"
    )
  }

  test("protects against invalid subscriber") { implicit s =>
    intercept[NullPointerException] {
      Iterant[Task].of(1).toReactivePublisher.subscribe(null)
    }
  }

  test("protects against broken cursors") { implicit s =>
    val dummy = DummyException("dummy")
    var effect = 0
    val stream = Iterant[Task]
      .nextCursorS[Int](ThrowExceptionCursor(dummy), Task.now(Iterant[Task].empty[Int]), Task.unit)
      .doOnEarlyStop(Task.eval { effect += 1 })

    assertEquals(effect,0)

    val f = sum(stream, Long.MaxValue).runAsync
    s.tick()

    assertEquals(effect, 1)
    assertEquals(f.value, Some(Failure(dummy)))
  }

  test("protects against broken batches") { implicit s =>
    val dummy = DummyException("dummy")
    var effect = 0

    val stream = Iterant[Task]
      .nextBatchS[Int](ThrowExceptionBatch(dummy), Task.now(Iterant[Task].empty[Int]), Task.unit)
      .doOnEarlyStop(Task.eval { effect += 1 })

    assertEquals(effect,0)

    val f = sum(stream, Long.MaxValue).runAsync
    s.tick()

    assertEquals(effect, 1)
    assertEquals(f.value, Some(Failure(dummy)))
  }

  test("Iterant.empty completes immediately on subscribe") { implicit s =>
    var wasCompleted: Option[Throwable] = null

    Iterant[Task].empty[Int].toReactivePublisher.subscribe(
      new Subscriber[Int] {
        def onSubscribe(s: Subscription): Unit = ()
        def onNext(t: Int): Unit = ()

        def onError(e: Throwable): Unit =
          wasCompleted = Some(e)
        def onComplete(): Unit =
          wasCompleted = None
      })

    assertEquals(wasCompleted, None)
  }

  test("Iterant.raiseError completes immediately on subscribe") { implicit s =>
    val dummy = DummyException("dummy")
    var wasCompleted: Option[Throwable] = null

    Iterant[Task].raiseError[Int](dummy).toReactivePublisher.subscribe(
      new Subscriber[Int] {
        def onSubscribe(s: Subscription): Unit = ()
        def onNext(t: Int): Unit = ()

        def onError(t: Throwable): Unit =
          wasCompleted = Some(t)
        def onComplete(): Unit =
          wasCompleted = None
      })

    assertEquals(wasCompleted, Some(dummy))
  }

  test("Iterant.empty produces EmptySubscription") { implicit s =>
    var wasCompleted: Option[Throwable] = null

    Iterant[Task].empty[Int].toReactivePublisher.subscribe(
      new Subscriber[Int] {
        def onNext(t: Int): Unit = ()
        def onSubscribe(s: Subscription): Unit = {
          s.request(10000)
          s.cancel()
        }

        def onError(e: Throwable): Unit =
          wasCompleted = Some(e)
        def onComplete(): Unit =
          wasCompleted = None
      })

    assertEquals(wasCompleted, None)
  }

  def sum[F[_]](stream: Iterant[F, Int], request: Long)(implicit F: Effect[F]): Task[Long] =
    Task.create { (scheduler, cb) =>
      implicit val ec = scheduler
      val subscription = SingleAssignmentSubscription()

      stream.toReactivePublisher.subscribe(
        new Subscriber[Int] {
          private[this] var s: Subscription = _
          private[this] var requested = 0L
          private[this] var sum = 0L

          def onSubscribe(s: Subscription): Unit = {
            this.s = s
            subscription := s
            requested = request
            s.request(request)
          }

          def onNext(t: Int): Unit = {
            sum += t
            if (request != Long.MaxValue) requested -= 1

            if (requested <= 0) {
              requested = request
              s.request(request)
            }
          }

          def onError(t: Throwable): Unit =
            cb.onError(t)
          def onComplete(): Unit =
            cb.onSuccess(sum)
        })

      subscription
    }

  class CustomIOEffect extends Effect[IO] {
    def runAsync[A](fa: IO[A])(cb: (Either[Throwable, A]) => IO[Unit]): IO[Unit] =
      fa.runAsync(cb)
    def async[A](k: ((Either[Throwable, A]) => Unit) => Unit): IO[A] =
      IO.async(k)
    def suspend[A](thunk: => IO[A]): IO[A] =
      IO.suspend(thunk)
    def flatMap[A, B](fa: IO[A])(f: (A) => IO[B]): IO[B] =
      fa.flatMap(f)
    def tailRecM[A, B](a: A)(f: (A) => IO[Either[A, B]]): IO[B] =
      IO.ioEffect.tailRecM(a)(f)
    def raiseError[A](e: Throwable): IO[A] =
      IO.raiseError(e)
    def handleErrorWith[A](fa: IO[A])(f: (Throwable) => IO[A]): IO[A] =
      IO.ioEffect.handleErrorWith(fa)(f)
    def pure[A](x: A): IO[A] =
      IO.pure(x)
    override def liftIO[A](ioa: IO[A]): IO[A] =
      ioa
  }
}
