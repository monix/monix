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

import monix.execution.{CancelableFuture, FutureUtils}
import monix.execution.schedulers.TrampolineExecutionContext

import scala.concurrent.Future

/**
  * Type class for describing how isolation work for specific data types.
  * There is a default instance for any type but some of them (e.g. `Future`) require
  * special handling and using type class is an alternative to overloads and `asInstanceOf` calls.
  */
trait CanIsolate[R] {
  /** Execute a  block of code without propagating any `Local.Context`
    * changes outside.
    */
  def isolate(f: => R): R = bind(Local.getContext().mkIsolated)(f)

  /** Execute a block with a specific local value, restoring the
    * current state upon completion.
    */
  def bind(ctx: Local.Context)(f: => R): R
}

object CanIsolate extends CanIsolateInstancesLevel1 {
  def apply[R](implicit c: CanIsolate[R]): CanIsolate[R] = c
}

private[misc] abstract class CanIsolateInstancesLevel1 extends CanIsolateInstancesLevel0 {
  implicit def cancelableFuture[R]: CanIsolate[CancelableFuture[R]] = new CanIsolate[CancelableFuture[R]] {
    override def bind(ctx: Local.Context)(f: => CancelableFuture[R]): CancelableFuture[R] = {
      val prev = Local.getContext()
      Local.setContext(ctx)

      try {
        f.transform(result => {
          Local.setContext(prev)
          result
        })(TrampolineExecutionContext.immediate)
      } finally {
        Local.setContext(prev)
      }
    }
  }

  implicit def future[R]: CanIsolate[Future[R]] = new CanIsolate[Future[R]] {
    override def bind(ctx: Local.Context)(f: => Future[R]): Future[R]  = {
      val prev = Local.getContext()
      Local.setContext(ctx)

      try {
        FutureUtils
          .transform[R, R](f, result => {
          Local.setContext(prev)
          result
        })(TrampolineExecutionContext.immediate)
      } finally {
        Local.setContext(prev)
      }
    }
  }
}

private[misc] abstract class CanIsolateInstancesLevel0  {
  implicit def default[R]: CanIsolate[R] = new CanIsolate[R] {
    override def bind(ctx: Local.Context)(f: => R): R = {
      val prev = Local.getContext()
      Local.setContext(ctx)
      try f
      finally Local.setContext(prev)
    }
  }
}
