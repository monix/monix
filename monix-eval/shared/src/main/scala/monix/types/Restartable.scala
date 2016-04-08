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

package monix.types

/** Type-class for computations or run-loops that can be restarted. */
trait Restartable[F[_]] extends Deferrable[F] {
  /** Given a predicate function, keep retrying the
    * source until the function returns true.
    */
  def restartUntil[A](fa: F[A])(p: A => Boolean): F[A]

  /** Creates a new instance that in case of error will retry
    * executing the source again and again, until it succeeds,
    * or until the maximum retries count is reached.
    *
    * In case of continuous failure the total number of executions
    * will be `maxRetries + 1`.
    */
  def onErrorRestart[A](fa: F[A], maxRetries: Long): F[A]

  /** Creates a new instance that in case of error will
    * retry executing the source again and again,
    * until it succeeds or until the given predicate
    * is `false`.
    *
    * In case of continuous failure the total number of executions
    * will be `maxRetries + 1`.
    */
  def onErrorRestartIf[A](fa: F[A])(p: Throwable => Boolean): F[A]
}
