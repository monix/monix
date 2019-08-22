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

private[execution] trait CanIsolateForPlatform {
  /**
    * Instance for `java.util.concurrent.CompletableFuture`.
    */
  implicit def completableFuture[R]: CanBindLocals[CompletableFuture[R]] =
    CompletableFutureInstance.asInstanceOf[CanBindLocals[CompletableFuture[R]]]

  /** Implementation for [[completableFuture]]. */
  protected object CompletableFutureInstance extends CanBindLocals[CompletableFuture[Any]] {
    override def bindContext(ctx: Local.Context)(f: => CompletableFuture[Any]): CompletableFuture[Any] = {
      val prev = Local.getContext()
      Local.setContext(ctx)

      try {
        f.handle(
          new BiFunction[Any, Throwable, Any] {
            def apply(r: Any, error: Throwable): Any = {
              Local.setContext(prev)
              if (error != null) throw error
              else r
            }
          }
        )
      } finally {
        Local.setContext(prev)
      }
    }
  }
}
