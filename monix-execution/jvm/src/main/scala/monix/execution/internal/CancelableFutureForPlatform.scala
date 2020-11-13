/*
 * Copyright (c) 2014-2020 by The Monix Project Developers.
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

package monix.execution
package internal

import java.util.concurrent.{CancellationException, CompletableFuture, CompletionException}
import java.util.function.BiFunction
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

private[execution] abstract class CancelableFutureForPlatform {
  /**
    * Convert `java.util.concurrent.CompletableFuture` to
    * [[monix.execution.CancelableFuture CancelableFuture]]
    *
    * If the source is cancelled, returned `Future` will never terminate
    */
  def fromJavaCompletable[A](cfa: CompletableFuture[A])(implicit ec: ExecutionContext): CancelableFuture[A] =
    CancelableFuture.async(cb => {
      cfa.handle[Unit](new BiFunction[A, Throwable, Unit] {
        override def apply(result: A, err: Throwable): Unit = {
          err match {
            case null =>
              cb(Success(result))
            case _: CancellationException =>
              ()
            case ex: CompletionException if ex.getCause ne null =>
              cb(Failure(ex.getCause))
            case ex =>
              cb(Failure(ex))
          }
        }
      })
      Cancelable(() => { cfa.cancel(true); () })
    })

  /**
    * Convert [[monix.execution.CancelableFuture CancelableFuture]] to
    * `java.util.concurrent.CompletableFuture`.
    */
  def toJavaCompletable[A](source: CancelableFuture[A])(implicit ec: ExecutionContext): CompletableFuture[A] = {
    val cf = new CompletableFuture[A]()
    source.onComplete {
      case Success(a) =>
        cf.complete(a)
      case Failure(ex) =>
        cf.completeExceptionally(ex)
    }
    // We need to catch  and act on the cancellation signal
    cf.handle[Unit](new BiFunction[A, Throwable, Unit] {
      override def apply(result: A, err: Throwable): Unit = {
        err match {
          case _: CancellationException => source.cancel()
          case _ => ()
        }
      }
    })
    cf
  }
}
