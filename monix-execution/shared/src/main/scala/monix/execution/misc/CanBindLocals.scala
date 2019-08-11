/*
 * Copyright (c) 2014-2019 by The Monix Project Developers.
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

package monix.execution.misc

import java.util.concurrent.CompletableFuture
import java.util.function.BiFunction
import monix.execution.{CancelableFuture, FutureUtils}
import monix.execution.schedulers.TrampolineExecutionContext
import scala.concurrent.Future

/**
  * Type class for describing how isolation work for specific data types.
  * There is a default instance for any type but some of them (e.g. `Future`) require
  * special handling and using type class is an alternative to overloads and `asInstanceOf` calls.
  */
trait CanBindLocals[R] {
  def withSuspendedContext(ctx: () => Local.Context)(f: => R): R

  def withContext(ctx: Local.Context)(f: => R): R =
    withSuspendedContext(() => ctx)(f)

  def bind[A](local: Local[A], value: Option[A])(f: => R): R =
    withSuspendedContext(() => Local.getContext().bind(local.key, value))(f)

  /** Execute a  block of code without propagating any `Local.Context`
    * changes outside.
    */
  def isolate(f: => R): R =
    withSuspendedContext(CanBindLocals.mkIsolatedRef)(f)
}

object CanBindLocals extends CanIsolateInstancesLevel1 {
  def apply[R](implicit R: CanBindLocals[R]): CanBindLocals[R] = R

  private val mkIsolatedRef =
    () => Local.getContext().isolate()
}

private[misc] abstract class CanIsolateInstancesLevel1 extends CanIsolateInstancesLevel0 {
  /**
    * Instance for `scala.concurrent.Future`.
    */
  implicit def cancelableFuture[R]: CanBindLocals[CancelableFuture[R]] =
    FutureInstance.asInstanceOf[CanBindLocals[CancelableFuture[R]]]

  /**
    * Instance for `java.util.concurrent.CompletableFuture`.
    */
  implicit def completableFuture[R]: CanBindLocals[CompletableFuture[R]] =
    CompletableFutureInstance.asInstanceOf[CanBindLocals[CompletableFuture[R]]]

  /**
    * Instance for `scala.concurrent.Future`.
    */
  implicit def future[R]: CanBindLocals[Future[R]] =
    FutureInstance.asInstanceOf[CanBindLocals[Future[R]]]
}

private[misc] abstract class CanIsolateInstancesLevel0 {
  /**
    * Instance for `scala.Unit`.
    */
  implicit def synchronous[R]: CanBindLocals[R] =
    SynchronousInstance.asInstanceOf[CanBindLocals[R]]

  /** Implementation for [[CanBindLocals.synchronous]]. */
  protected object SynchronousInstance extends CanBindLocals[Any] {
    override def withSuspendedContext(ctx: () => Local.Context)(f: => Any): Any =
      withContext(ctx())(f)

    override def withContext(ctx: Local.Context)(f: => Any): Any = {
      val prev = Local.getContext()
      Local.setContext(ctx)
      try f
      finally Local.setContext(prev)
    }
  }

  /** Implementation for [[CanBindLocals.future]]. */
  protected object FutureInstance extends CanBindLocals[Future[Any]] {
    def withSuspendedContext(ctx: () => Local.Context)(f: => Future[Any]): Future[Any] =
      withContext(ctx())(f)

    override def withContext(ctx: Local.Context)(f: => Future[Any]): Future[Any] = {
      val prev = Local.getContext()
      Local.setContext(ctx)

      try {
        FutureUtils
          .transform[Any, Any](f, result => {
            Local.setContext(prev)
            result
          })(TrampolineExecutionContext.immediate)
      } finally {
        Local.setContext(prev)
      }
    }

    override def bind[A](local: Local[A], value: Option[A])(f: => Future[Any]): Future[Any] = super.bind(local, value)(f)

    override def isolate(f: => Future[Any]): Future[Any] = super.isolate(f)
  }

  /** Implementation for [[CanBindLocals.completableFuture]]. */
  protected object CompletableFutureInstance extends CanBindLocals[CompletableFuture[Any]] {
    def withSuspendedContext(ctx: () => Local.Context)(f: => CompletableFuture[Any]): CompletableFuture[Any] =
      withContext(ctx())(f)

    override def withContext(ctx: Local.Context)(f: => CompletableFuture[Any]): CompletableFuture[Any] = {
      val prev = Local.getContext()
      Local.setContext(ctx)

      try {
        f.handleAsync(new BiFunction[Any, Throwable, Any] {
          def apply(r: Any, error: Throwable): Any = {
            Local.setContext(prev)
            if (error != null) throw error
            else r
          }
        }, TrampolineExecutionContext.immediate)
      } finally {
        Local.setContext(prev)
      }
    }
  }
}
