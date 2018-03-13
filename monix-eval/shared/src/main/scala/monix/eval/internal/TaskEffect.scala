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
package internal

import cats.effect.IO
import monix.execution.cancelables.{SingleAssignCancelable, StackedCancelable}
import monix.execution.internal.AttemptCallback.noop
import monix.execution.misc.NonFatal
import monix.execution.{Cancelable, Scheduler}

/** INTERNAL API
  *
  * `Task` integration utilities for the `cats.effect.ConcurrentEffect`
  * instance, provided in `monix.eval.instances`.
  */
private[eval] object TaskEffect {
  /**
    * `cats.effect.Async#async`
    */
  def async[A](k: (Either[Throwable, A] => Unit) => Unit): Task[A] =
    Task.unsafeCreate { (ctx, cb) =>
      implicit val sc = ctx.scheduler
      try k {
        case Right(a) => cb.asyncOnSuccess(a)
        case Left(e) => cb.asyncOnError(e)
      } catch {
        case NonFatal(e) =>
          sc.reportFailure(e)
      }
    }

  /**
    * `cats.effect.Concurrent#cancelable`
    */
  def cancelable[A](k: (Either[Throwable, A] => Unit) => IO[Unit]): Task[A] =
    Task.unsafeCreate { (ctx, cb) =>
      implicit val sc = ctx.scheduler
      val conn = ctx.connection
      val cancelable = SingleAssignCancelable()
      conn push cancelable

      try {
        val io = k(new CreateCallback[A](conn, cb))
        if (io != IO.unit)
          cancelable := Cancelable.fromIOUnsafe(io)
      } catch {
        case NonFatal(e) =>
          sc.reportFailure(e)
      }
    }

  /**
    * `cats.effect.Effect#runAsync`
    */
  def runAsync[A](fa: Task[A])(cb: Either[Throwable, A] => IO[Unit])
    (implicit s: Scheduler): IO[Unit] =
    IO { execute(fa, cb); () }

  /**
    * `cats.effect.ConcurrentEffect#runCancelable`
    */
  def runCancelable[A](fa: Task[A])(cb: Either[Throwable, A] => IO[Unit])
    (implicit s: Scheduler): IO[IO[Unit]] =
    IO(execute(fa, cb).cancelIO)

  private def execute[A](fa: Task[A], cb: Either[Throwable, A] => IO[Unit])
    (implicit s: Scheduler) = {

    fa.runAsync(new Callback[A] {
      private def signal(value: Either[Throwable, A]): Unit =
        try cb(value).unsafeRunAsync(noop)
        catch { case NonFatal(e) => s.reportFailure(e) }

      def onSuccess(value: A): Unit =
        signal(Right(value))
      def onError(e: Throwable): Unit =
        signal(Left(e))
    })
  }

  private final class CreateCallback[A](
    conn: StackedCancelable, cb: Callback[A])
    (implicit s: Scheduler)
    extends (Either[Throwable, A] => Unit) {

    override def apply(value: Either[Throwable, A]): Unit = {
      conn.pop()
      cb.asyncApply(value)
    }
  }
}
