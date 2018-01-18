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

package monix.execution.schedulers

import scala.annotation.implicitNotFound

/** Marker for blocking operations that need to be disallowed on top of
  * JavaScript engines, or other platforms that don't support the blocking
  * of threads.
  *
  * As sample, lets implement a low-level blocking operation; but kids,
  * don't do this at home, since this is error prone and you already have
  * Scala's `Await.result`, this sample being shown for pedagogical purposes:
  *
  * {{{
  *   import monix.execution.schedulers.CanBlock
  *   import java.util.concurrent.CountDownLatch
  *   import scala.concurrent.{ExecutionContext, Future}
  *   import scala.util.Try
  *
  *   def block[A](fa: Future[A])
  *     (implicit ec: ExecutionContext, permit: CanBlock): Try[A] = {
  *
  *     var result = Option.empty[Try[A]]
  *     val latch = new CountDownLatch(1)
  *
  *     fa.onComplete { r =>
  *       result = r
  *       latch.countDown()
  *     }
  *
  *     latch.await()
  *     result.get
  *   }
  * }}}
  *
  * And then for JavaScript engines (Scala.js) you could describe the same
  * function, with the same signature, but without any implementation, since
  * this operation isn't supported:
  *
  * {{{
  *   def block[A](fa: Future[A])
  *     (implicit ec: ExecutionContext, permit: CanBlock): Try[A] =
  *     throw new UnsupportedOperationException("Cannot block threads on top of JavaScript")
  * }}}
  *
  * Now in usage, when the caller is invoking `block` as described, it will
  * work without issues on top of the JVM, but when compiled with Scala.js
  * it will trigger a message like this:
  *
  * {{{
  *   [error] Playground.scala:30:8: Blocking operations aren't supported
  *   [error] on top of JavaScript, because it cannot block threads!
  *   [error] Please use asynchronous API calls.
  *   [error]   block(Future(1))
  *   [error]        ^
  * }}}
  */
@implicitNotFound(
  "Blocking operations aren't supported \n" +
  "on top of JavaScript, because it cannot block threads! \n" +
  "Please use asynchronous API calls.")
final class CanBlock private ()
