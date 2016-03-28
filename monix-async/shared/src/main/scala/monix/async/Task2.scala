/*
 * Copyright (c) 2014-2016 by its authors. Some rights reserved.
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

package monix.async

import monix.execution.{Cancelable, Scheduler}
import scala.util.Try
import scala.util.control.NonFatal

sealed abstract class Task2[+A]

object Task2 {
  final case class Now[+A](a: A) extends Task2[A]
  final case class Error(ex: Throwable) extends Task2[Nothing]

  final case class Suspend[+A](thunk: () => Task2[A]) extends Task2[A]
  final case class BindSuspend[A,B](thunk: () => Task2[A], f: A => Task2[B]) extends Task2[A]

  final case class Async[+A](onFinish: (Try[A] => RunLoop) => Unit) extends Task2[A]
  final case class BindAsync[A,B](onFinish: (Try[A] => RunLoop) => Unit, f: A => Task2[B]) extends Task2[A]

  final class RunLoop[A](task: Task2[A]) {
    def start(cb: Callback[A])(implicit s: Scheduler): Unit =
      loop(task, cb)(s)

    def loop(task: Task2[A], cb: Callback[A])(implicit s: Scheduler): Unit =
      task match {
        case Now(a) =>
          cb.onSuccess(a)
        case Error(ex) =>
          cb.onError(ex)
        case Suspend(thunk) =>
          val fa = try thunk() catch { case NonFatal(ex) => Error(ex) }
          loop(fa, cb)
        case BindSuspend(thunk, f) =>
          val fa = try thunk() catch { case NonFatal(ex) => Error(ex) }
          ???
        case Async(onFinish) =>
          ???
      }

    def bindSuspend[B](task: Task2[A], f: A => Task2[B]): Task2[B] =
      task match {
        case Now(a) =>
          try f(a) catch { case NonFatal(ex) => Error(ex) }
        case error @ Error(_) =>
          error
        case Suspend(thunk) =>
          val continue = try thunk() catch { case NonFatal(ex) => Error(ex) }
          bindSuspend(continue, f)
        case BindSuspend(thunk, f2) =>
          ???
      }
  }
}