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

/** A mapping function type that is also able to handle errors.
  *
  * Used in the `Task` and `Coeval` implementations to specify
  * error handlers in their respective `FlatMap` internal states.
  */
private[eval] abstract class StackFrame[-A, +R]
  extends (A => R) { self =>

  def apply(a: A): R
  def recover(e: Throwable): R
}

private[eval] object StackFrame {
  /** [[StackFrame]] used in the implementation of `redeemWith`. */
  final class RedeemWith[-A, +R](fe: Throwable => R, fa: A => R)
    extends StackFrame[A, R] {

    def apply(a: A): R = fa(a)
    def recover(e: Throwable): R = fe(e)
  }

  /** [[StackFrame]] reference that only handles errors,
    * useful for quick filtering of `onErrorHandleWith` frames.
    */
  final class ErrorHandler[-A, +R](fe: Throwable => R, fa: A => R)
    extends StackFrame[A, R] {

    def apply(a: A): R = fa(a)
    def recover(e: Throwable): R = fe(e)
  }
}
