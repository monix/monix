/*
 * Copyright (c) 2014-2022 Monix Contributors.
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

package monix.eval.instances

import cats.arrow.FunctionK
import cats.effect.kernel.{ Async, Cont, Deferred, Fiber, Outcome, Poll, Ref, Sync }
import monix.eval.Task
import monix.execution.Scheduler

import java.util.concurrent.TimeUnit
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration

/** Cats Effect 3 `Async` instance backed by the [[Task]] run-loop.
  *
  * Primitive operations construct `IO` nodes. Operations such as `cede`, `sleep`,
  * and `racePair` use `AsyncCont` when callback signaling can happen before or after
  * its `get` effect starts. Setup which creates a cancel token is masked, then exposes
  * its wait through `Poll`.
  */
private[eval] final class CatsAsyncForTask extends Async[Task] {
  override def pure[A](a: A): Task[A] =
    Task.pure(a)

  override def flatMap[A, B](fa: Task[A])(f: A => Task[B]): Task[B] =
    fa.flatMap(f)

  override def tailRecM[A, B](a: A)(f: A => Task[Either[A, B]]): Task[B] =
    Task.defer {
      f(a).flatMap {
        case Left(next) => tailRecM(next)(f)
        case Right(value) => Task.pure(value)
      }
    }

  override def raiseError[A](e: Throwable): Task[A] =
    Task.raiseError(e)

  override def handleErrorWith[A](fa: Task[A])(f: Throwable => Task[A]): Task[A] =
    fa.handleErrorWith(f)

  override def forceR[A, B](fa: Task[A])(fb: Task[B]): Task[B] =
    fa.map(_ => ()).handleErrorWith(_ => Task.pure(())).flatMap(_ => fb)

  override def uncancelable[A](body: Poll[Task] => Task[A]): Task[A] =
    Task.uncancelable(body)

  override val canceled: Task[Unit] =
    Task.canceled

  override def onCancel[A](fa: Task[A], fin: Task[Unit]): Task[A] =
    fa.onCancel(fin)

  override def start[A](fa: Task[A]): Task[Fiber[Task, Throwable, A]] =
    fa.start

  override def ref[A](a: A): Task[Ref[Task, A]] =
    // These constructors allocate immediately, so delaying them invokes the
    // constructor during every IO evaluation rather than during IO construction.
    Task.delay(Ref.unsafe[Task, A](a)(this))

  override def deferred[A]: Task[Deferred[Task, A]] =
    Task.delay(Deferred.unsafe[Task, A](this))

  override def never[A]: Task[A] =
    Task.never

  override val cede: Task[Unit] =
    // Submit the signal to the current scheduler. The callback indirection also
    // covers a scheduler which invokes it before `get` starts.
    Task.cont0[Unit, Unit] { (scheduler, callback, get) =>
      scheduler.execute(() => callback.onSuccess(()))
      get
    }

  override def racePair[A, B](
    fa: Task[A],
    fb: Task[B]
  ): Task[Either[(Outcome[Task, Throwable, A], Fiber[Task, Throwable, B]),
    (Fiber[Task, Throwable, A], Outcome[Task, Throwable, B])]] = {
    type Result = Either[(Outcome[Task, Throwable, A], Fiber[Task, Throwable, B]),
      (Fiber[Task, Throwable, A], Outcome[Task, Throwable, B])]

    // Starting both children is masked, so cancellation cannot interrupt the setup.
    uncancelable { poll =>
      start(fa).flatMap { fiberA =>
        start(fb).flatMap { fiberB =>
          val await = Task.cont0[Result, Result] { (scheduler, callback, get) =>
            // Both observers race to signal one continuation. Its atomic state keeps
            // the first outcome and ignores the second signal.
            fiberA.join
              .flatMap(outcome => Task.delay(callback.onSuccess(Left((outcome, fiberB)))))
              .unsafeRunAndForget()(scheduler)
            fiberB.join
              .flatMap(outcome => Task.delay(callback.onSuccess(Right((fiberA, outcome)))))
              .unsafeRunAndForget()(scheduler)
            // The observer for the other child is not canceled after a winner. It
            // remains joined and its eventual second signal is ignored.
            get
          }

          // Only the wait is cancelable. Canceling it cancels both children and waits
          // for their finalizers before completing parent cancellation.
          poll(await).onCancel(forceR(fiberA.cancel)(fiberB.cancel))
        }
      }
    }
  }

  override def monotonic: Task[FiniteDuration] =
    readScheduler(scheduler => FiniteDuration(scheduler.clockMonotonic(TimeUnit.NANOSECONDS), TimeUnit.NANOSECONDS))

  override def realTime: Task[FiniteDuration] =
    readScheduler(scheduler => FiniteDuration(scheduler.clockRealTime(TimeUnit.MICROSECONDS), TimeUnit.MICROSECONDS))

  override protected def sleep(time: FiniteDuration): Task[Unit] =
    if (time.length <= 0) cede
    else
      // Timer installation is masked, while `get` is polled. This prevents a race in
      // which cancellation occurs after scheduling but before its token is attached.
      uncancelable { poll =>
        Task.cont0[Unit, Unit] { (scheduler, callback, get) =>
          val cancelable = scheduler.scheduleOnce(time)(callback.onSuccess(()))
          poll(get).onCancel(Task.delay(cancelable.cancel()))
        }
      }

  override def suspend[A](hint: Sync.Type)(thunk: => A): Task[A] =
    // IO has one delayed-evaluation encoding; `Sync.Type` is an optimization hint only.
    Task.delay(thunk)

  override def evalOn[A](fa: Task[A], ec: ExecutionContext): Task[A] =
    // The source runs as a child on `target`, but completion resumes this continuation
    // on its original scheduler. Masking covers child startup and cancel-token setup.
    uncancelable { poll =>
      Task.cont0[A, A] { (_, callback, get) =>
        // Preserve an existing `Scheduler`, including its clock and execution model;
        // adapt only a plain `ExecutionContext`.
        val target = ec match {
          case scheduler: Scheduler => scheduler
          case _ => Scheduler(ec)
        }
        val cancel = fa.unsafeRunAsync(callback)(target)
        poll(get).onCancel(cancel)
      }
    }

  override def executionContext: Task[ExecutionContext] =
    readScheduler(identity)

  override def cont[K, R](body: Cont[Task, K, R]): Task[R] =
    // Cats Effect's `Either` callback is adapted to Monix's two-channel `Callback`.
    // `get` already belongs to `IO`, therefore `lift` is the identity.
    Task.cont0[K, R] { (_, callback, get) =>
      val resume: Either[Throwable, K] => Unit = {
        case Right(value) => callback.onSuccess(value)
        case Left(error) => callback.onError(error)
      }
      val lift = FunctionK.id[Task]
      body.apply[Task](this)(resume, get, lift)
    }

  private def readScheduler[A](f: Scheduler => A): Task[A] =
    // `cont0` exposes the interpreter's current Scheduler. No callback handshake is
    // needed here; the returned delayed node performs the actual read.
    Task.cont0[A, A]((scheduler, _, _) => Task.delay(f(scheduler)))
}
