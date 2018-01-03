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

package monix.execution.misc

import monix.execution.Macros

object Local {
  /** Builds a new [[Local]] with the given `default` to be returned
    * if a value hasn't been set, or after the local gets cleared.
    *
    * {{{
    *   val num = Local(0)
    *   num() //=> 0
    *
    *   num := 100
    *   num() //=> 100
    *
    *   num.clear()
    *   num() //=> 0
    * }}}
    */
  def apply[A](default: A): Local[A] =
    new Local[A](default)

  /** Represents the current state of all [[Local locals]] for a given
    * execution context.
    *
    * This should be treated as an opaque value and direct modifications
    * and access are considered verboten.
    */
  type Context = scala.collection.immutable.Map[Key, _]

  /** Internal — key type used in [[Context]]. */
  final class Key extends Serializable

  /** Current [[Context]] kept in a `ThreadLocal`. */
  private[this] val localContext: ThreadLocal[Context] =
    ThreadLocal(Map.empty)

  /** Return the state of the current Local state. */
  def getContext(): Context =
    localContext.get

  /** Restore the Local state to a given Context. */
  def setContext(ctx: Context): Unit =
    localContext.set(ctx)

  /** Clear the Local state. */
  def clearContext(): Unit =
    localContext.reset()

  /** Execute a block of code using the specified state of
    * `Local.Context` and restore the current state when complete.
    */
  def bind[R](ctx: Context)(f: => R): R =
    macro Macros.localLet

  /** Execute a block of code with a clear state of `Local.Context`
    * and restore the current state when complete.
    */
  def bindClear[R](f: => R): R =
    macro Macros.localLetClear

  /** Convert a closure `() => R` into another closure of the same
    * type whose [[Local.Context]] is saved when calling closed
    * and restored upon invocation.
    */
  def closed[R](fn: () => R): () => R = {
    val closure = Local.getContext()
    () => {
      val save = Local.getContext()
      Local.setContext(closure)
      try fn() finally Local.setContext(save)
    }
  }

  private def getKey[A](key: Key): Option[A] =
    localContext.get().get(key).asInstanceOf[Option[A]]

  private def getKeyOrElse[A](key: Key, default: => A): A = {
    val ctx = localContext.get()
    ctx.getOrElse(key, default).asInstanceOf[A]
  }

  private def saveKey(key: Key, value: Any): Unit = {
    val newCtx = localContext.get().updated(key, value)
    localContext.set(newCtx)
  }

  private def clearKey(key: Key): Unit =
    localContext.set(localContext.get - key)

  private def restoreKey(key: Key, value: Option[_]): Unit =
    value match {
      case None => clearKey(key)
      case Some(v) => saveKey(key, v)
    }
}

/** A `Local` is a [[ThreadLocal]] whose scope is flexible. The state
  * of all Locals may be saved or restored onto the current thread by
  * the user. This is useful for threading Locals through execution
  * contexts.
  *
  * Because it's not meaningful to inherit control from two places,
  * Locals don't have to worry about having to merge two
  * [[monix.execution.misc.Local.Context contexts]].
  *
  * Note: the implementation is optimized for situations in which save
  * and restore optimizations are dominant.
  */
final class Local[A](default: => A) {
  import Local.Key
  private[this] val key: Key = new Key

  /** Returns the current value of this `Local`. */
  def apply(): A =
    Local.getKeyOrElse(key, default)

  /** Updates the value of this `Local`. */
  def update(value: A): Unit =
    Local.saveKey(key, value)

  /** Alias for [[apply]]. */
  def get: A = apply()

  /** Alis for [[update]]. */
  def `:=`(value: A): Unit = update(value)

  /** Returns the current value of this `Local`, or `None` in
    * case this local should fallback to the default.
    *
    * Use [[apply]] in case automatic fallback is needed.
    */
  def value: Option[A] =
    Local.getKey(key)

  /** Updates the current value of this `Local`. If the given
    * value is `None`, then the local gets [[clear cleared]].
    *
    * This operation is a mixture of [[apply]] and [[clear]].
    */
  def value_=(update: Option[A]): Unit =
    Local.restoreKey(key, update)

  /** Execute a block with a specific local value, restoring the
    * current state upon completion.
    */
  def bind[R](value: A)(f: => R): R = {
    val saved = this.value
    update(value)
    try f finally this.value = saved
  }

  /** Execute a block with the `Local` cleared, restoring the current
    * state upon completion.
    */
  def bindClear[R](f: => R): R = {
    val saved = Local.getKey[A](key)
    clear()
    try f finally Local.restoreKey(key, saved)
  }

  /** Clear the Local's value. Other [[Local Locals]] are not modified.
    *
    * General usage should be via [[bindClear]] to avoid leaks.
    */
  def clear(): Unit =
    Local.clearKey(key)
}
