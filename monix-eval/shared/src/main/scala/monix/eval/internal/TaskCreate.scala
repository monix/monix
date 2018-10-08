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

package monix.eval.internal

import cats.effect.{CancelToken, IO}
import monix.eval.Task.{Async, Context}
import monix.eval.{Callback, Coeval, Task}
import monix.execution.{Cancelable, Scheduler}
import scala.util.control.NonFatal

private[eval] object TaskCreate {
  /**
    * Implementation for `Task.cancelable`
    */
  def cancelable0[A](fn: (Scheduler, Callback[A]) => CancelToken[Task]): Task[A] = {
    val start = new Cancelable0Start[A, CancelToken[Task]](fn) {
      def setConnection(ref:  TaskConnectionRef, token:  CancelToken[Task])
        (implicit s:  Scheduler): Unit = ref := token
    }
    Async(start, trampolineBefore = false, trampolineAfter = false)
  }

  private abstract class Cancelable0Start[A, Token](fn: (Scheduler, Callback[A]) => Token)
    extends ((Context, Callback[A]) => Unit) {

    def setConnection(ref: TaskConnectionRef, token: Token)
      (implicit s: Scheduler): Unit

    final def apply(ctx: Context, cb: Callback[A]): Unit = {
      implicit val s = ctx.scheduler
      val conn = ctx.connection
      val cancelable = TaskConnectionRef()
      conn push cancelable.cancel

      try {
        val ref = fn(s, TaskConnection.trampolineCallback(conn, cb))
        // Optimization to skip the assignment, as it's expensive
        if (!ref.isInstanceOf[Cancelable.IsDummy])
          setConnection(cancelable, ref)
      } catch {
        case ex if NonFatal(ex) =>
          // We cannot stream the error, because the callback might have
          // been called already and we'd be violating its contract,
          // hence the only thing possible is to log the error.
          s.reportFailure(ex)
      }
    }
  }

  /**
    * Implementation for `cats.effect.Concurrent#cancelable`.
    */
  def cancelableEffect[A](k: (Either[Throwable, A] => Unit) => CancelToken[Task]): Task[A] =
    cancelable0 { (_, cb) => k(cb) }

  /**
    * Implementation for `Task.create`, used via `TaskBuilder`.
    */
  def cancelableIO[A](start: (Scheduler, Callback[A]) => CancelToken[IO]): Task[A] =
    cancelable0 { (sc, cb) => Task.fromIO(start(sc, cb)) }

  /**
    * Implementation for `Task.create`, used via `TaskBuilder`.
    */
  def cancelableCancelable[A](fn: (Scheduler, Callback[A]) => Cancelable): Task[A] = {
    val start = new Cancelable0Start[A, Cancelable](fn) {
      def setConnection(ref:  TaskConnectionRef, token:  Cancelable)
        (implicit s:  Scheduler): Unit = ref := token
    }
    Async(start, trampolineBefore = false, trampolineAfter = false)
  }

  /**
    * Implementation for `Task.create`, used via `TaskBuilder`.
    */
  def cancelableCoeval[A](start: (Scheduler, Callback[A]) => Coeval[Unit]): Task[A] =
    cancelable0 { (sc, cb) => Task.from(start(sc, cb)) }

  /**
    * Implementation for `Task.async`
    */
  def async0[A](fn: (Scheduler, Callback[A]) => Any): Task[A] = {
    val start = (ctx: Context, cb: Callback[A]) => {
      implicit val s = ctx.scheduler
      try {
        fn(s, Callback.trampolined(cb))
        ()
      } catch {
        case ex if NonFatal(ex) =>
          // We cannot stream the error, because the callback might have
          // been called already and we'd be violating its contract,
          // hence the only thing possible is to log the error.
          s.reportFailure(ex)
      }
    }
    Async(start, trampolineBefore = false, trampolineAfter = false)
  }

  /**
    * Implementation for `cats.effect.Async#async`.
    *
    * It duplicates the implementation of `Task.simple` with the purpose
    * of avoiding extraneous callback allocations.
    */
  def async[A](k: Callback[A] => Unit): Task[A] = {
    val start = (ctx: Context, cb: Callback[A]) => {
      implicit val s = ctx.scheduler
      try {
        k(Callback.trampolined(cb))
      } catch {
        case ex if NonFatal(ex) =>
          // We cannot stream the error, because the callback might have
          // been called already and we'd be violating its contract,
          // hence the only thing possible is to log the error.
          s.reportFailure(ex)
      }
    }
    Async(start, trampolineBefore = false, trampolineAfter = false)
  }

  /**
    * Implementation for `Task.asyncF`.
    */
  def asyncF[A](k: Callback[A] => Task[Unit]): Task[A] = {
    val start = (ctx: Context, cb: Callback[A]) => {
      implicit val s = ctx.scheduler
      try {
        // Creating new connection, because we can have a race condition
        // between the bind continuation and executing the generated task
        val ctx2 = Context(ctx.scheduler, ctx.options)
        val conn = ctx.connection
        conn.push(ctx2.connection.cancel)
        // Provided callback takes care of `conn.pop()`
        val task = k(TaskConnection.trampolineCallback(conn, cb))
        Task.unsafeStartNow(task, ctx2, Callback.empty)
      } catch {
        case ex if NonFatal(ex) =>
          // We cannot stream the error, because the callback might have
          // been called already and we'd be violating its contract,
          // hence the only thing possible is to log the error.
          s.reportFailure(ex)
      }
    }
    Async(start, trampolineBefore = false, trampolineAfter = false)
  }
}
