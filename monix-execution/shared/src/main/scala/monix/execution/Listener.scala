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

package monix.execution

import scala.concurrent.Promise

/** A simple listener interface, to be used in observer pattern
  * implementations or for specifying asynchronous callbacks.
  *
  * This interface does not provide any usage contract or
  * assumptions on how the `onValue(a)` function gets called.
  *
  * It's a replacement for `Function1[A,Unit]` but it does
  * not inherit from it, precisely because we want to attach
  * `Listener` semantics to types that cannot be `Function1[A,Unit]`.
  *
  * In particular `monix.eval.Callback` is a supertype of
  * `Listener`, even though `Callback` is a `Try[A] => Unit`.
  */
trait Listener[-A] extends Serializable {
  def onValue(value: A): Unit
}

object Listener {
  /** Builds a [[Listener]] from a `Function1`. */
  def apply[A,U](f: A => U): Listener[A] =
    new Listener[A] { def onValue(value: A) = f(value) }

  /** Builds a [[Listener]] from a `Function0`. */
  def unit(f: () => Unit): Listener[Unit] =
    new Listener[Unit] { def onValue(value: Unit) = f() }

  /** Converts a Scala `Promise` to a [[Listener]]. */
  def fromPromise[A](p: Promise[A]): Listener[A] =
    new Listener[A] { def onValue(value: A) = p.success(value) }
}