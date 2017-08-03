/*
 * Copyright (c) 2014-2017 by The Monix Project Developers.
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

package monix.eval.internal

import cats.effect.IO
import monix.eval.Task
import monix.execution.Scheduler
import scala.util.{Failure, Success}

private[monix] object TaskIOConversions {
  /** Implementation for `Task#toIO`. */
  def taskToIO[A](source: Task[A])(implicit s: Scheduler): IO[A] =
    source match {
      case Task.Now(v) => IO.pure(v)
      case Task.Error(e) => IO.raiseError(e)
      case Task.Eval(thunk) => IO(thunk())
      case _ => IO.suspend {
        val f = source.runAsync(s)
        f.value match {
          case Some(tryA) => tryA match {
            case Success(v) => IO.pure(v)
            case Failure(e) => IO.raiseError(e)
          }
          case None => IO.async { cb =>
            f.onComplete {
              case Success(v) => cb(Right(v))
              case Failure(e) => cb(Left(e))
            }
          }
        }
      }
    }

  /** Implementation for `Task#fromIO`. */
  def taskFromIO[A](io: IO[A]): Task[A] =
    io.to[Task]
}
