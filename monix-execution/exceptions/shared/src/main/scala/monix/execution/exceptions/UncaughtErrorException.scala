/*
 * Copyright (c) 2014-2022 Monix Contributors.
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

package monix.execution.exceptions

/**
  * The [[UncaughtErrorException]] wraps uncaught, generic errors.
  *
  * {{{
  *   val ex: UncaughtErrorException[String] =
  *     UncaughtErrorException("Error!")
  * }}}
  *
  */
class UncaughtErrorException[E] private (error: E) extends RuntimeException {

  override def toString: String = {
    getClass.getName + "(" + error.toString + ")"
  }
}

object UncaughtErrorException {
  /**
    * Builds an [[UncaughtErrorException]] value.
    */
  def apply[E](error: E): UncaughtErrorException[E] =
    new UncaughtErrorException[E](error)

  /**
    * Wraps any error value into a `Throwable`. If the given value is
    * already a `Throwable`, then use it as is without wrapping.
    */
  def wrap[E](error: E): Throwable =
    error match {
      case ref: Throwable => ref
      case _ => apply(error)
    }
}
