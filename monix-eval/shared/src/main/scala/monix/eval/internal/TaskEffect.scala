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
package internal

import cats.effect.{ CancelToken, IO, SyncIO }
import monix.execution.Callback
import monix.execution.Scheduler
import monix.execution.internal.AttemptCallback.noop
import scala.util.control.NonFatal

/** INTERNAL API
  *
  * `Task` integration utilities for the `cats.effect.ConcurrentEffect`
  * instance, provided in `monix.eval.instances`.
  */
private[eval] object TaskEffect {
  /**
    * `cats.effect.Effect#runAsync`
    */
  def runAsync[A](fa: Task[A])(cb: Either[Throwable, A] => IO[Unit])(
    implicit
    s: Scheduler,
    opts: Task.Options
  ): SyncIO[Unit] = SyncIO {
    execute(fa, cb)
    ()
  }

  /**
    * `cats.effect.ConcurrentEffect#runCancelable`
    */
  def runCancelable[A](fa: Task[A])(cb: Either[Throwable, A] => IO[Unit])(
    implicit
    s: Scheduler,
    opts: Task.Options
  ): SyncIO[CancelToken[Task]] = SyncIO {
    execute(fa, cb)
  }

  private def execute[A](fa: Task[A], cb: Either[Throwable, A] => IO[Unit])(
    implicit
    s: Scheduler,
    opts: Task.Options
  ): CancelToken[Task] = {
    fa.runAsyncOptF(new Callback[Throwable, A] {
      private def signal(value: Either[Throwable, A]): Unit =
        try cb(value).unsafeRunAsync(noop)
        catch { case NonFatal(e) => s.reportFailure(e) }

      def onSuccess(value: A): Unit =
        signal(Right(value))
      def onError(e: Throwable): Unit =
        signal(Left(e))
    })
  }
}
