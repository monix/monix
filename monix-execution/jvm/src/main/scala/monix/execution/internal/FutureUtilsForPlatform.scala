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

package monix.execution
package internal

import java.util.concurrent.CompletableFuture
import monix.execution.CancelableFuture
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success }

private[execution] abstract class FutureUtilsForPlatform { self =>
  /**
    * Convert any Scala `Future` to Java's `CompletableFuture`
    */
  def toJavaCompletable[A](source: Future[A])(implicit ec: ExecutionContext): CompletableFuture[A] = {

    source match {
      case ref: CancelableFuture[A] @unchecked =>
        CancelableFuture.toJavaCompletable(ref)
      case _ =>
        val cf = new CompletableFuture[A]()
        source.onComplete {
          case Success(a) =>
            cf.complete(a)
          case Failure(ex) =>
            cf.completeExceptionally(ex)
        }
        cf
    }
  }

  /**
    * Convert [[CancelableFuture]] to Java `CompletableFuture`
    */
  def fromJavaCompletable[A](cfa: CompletableFuture[A])(implicit ec: ExecutionContext): Future[A] =
    CancelableFuture.fromJavaCompletable(cfa)

  /**
    * Extension methods specific for Java 8 and up.
    */
  implicit final class Java8Extensions[F[T] <: Future[T], A](val source: F[A]) {
    /**
      * Extension method, alias of [[FutureUtils.toJavaCompletable]].
      */
    def asJava(implicit ec: ExecutionContext): CompletableFuture[A] =
      self.toJavaCompletable(source)
  }
}
