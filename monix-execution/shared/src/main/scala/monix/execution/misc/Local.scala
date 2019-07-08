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

import monix.execution.atomic.AtomicAny
import scala.annotation.tailrec
import scala.reflect.macros.whitebox

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
    new Local[A](() => default)

  /** Internal — key type used in [[Context]]. */
  final class Key extends Serializable

  def defaultContext(): Local.Context = new Unbound(AtomicAny(Map()))

  /** Current [[Context]] kept in a `ThreadLocal`. */
  private[this] val localContext: ThreadLocal[Context] =
    ThreadLocal(defaultContext())

  /** Return the state of the current Local state. */
  def getContext(): Context =
    localContext.get

  /** Restore the Local state to a given Context. */
  def setContext(ctx: Context): Unit =
    localContext.set(ctx)

  /** Clear the Local state. */
  def clearContext(): Unit =
    localContext.set(defaultContext())

  def isolate[R](f: => R): R =
    macro Macros.isolate

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

  private[monix] def bindCurrentIf[R](b: => Boolean)(f: => R): R =
    macro Macros.localLetCurrentIf

  /** Convert a closure `() => R` into another closure of the same
    * type whose [[Local.Context]] is saved when calling closed
    * and restored upon invocation.
    */
  def closed[R](fn: () => R): () => R = {
    val closure = Local.getContext()
    () => {
      val save = Local.getContext()
      Local.setContext(closure)
      try fn()
      finally Local.setContext(save)
    }
  }

  private def getKey[A](key: Key): Option[A] =
    localContext.get().getOption(key)

  private def getKeyOrElse[A](key: Key, default: => A): A = {
    localContext.get().getOr(key, default)
  }

  private def saveKey(key: Key, value: Any): Unit = {
    localContext.get().set(key, value, isPresent = true)
  }

  private def clearKey(key: Key): Unit = {
    localContext.get().set(key, null, isPresent = false)
  }

  private def restoreKey(key: Key, value: Option[_]): Unit =
    value match {
      case None => clearKey(key)
      case Some(v) => saveKey(key, v)
    }

  /** Macros implementations for [[bind]] and [[bindClear]]. */
  private class Macros(override val c: whitebox.Context) extends InlineMacros with HygieneUtilMacros {
    import c.universe._

    def localLet(ctx: Tree)(f: Tree): Tree = {
      // TODO - reduce copy-paste in localLetXXX macros
      val ctxRef = util.name("ctx")
      val saved = util.name("saved")
      val Local = symbolOf[Local[_]].companion
      val AnyRefSym = symbolOf[AnyRef]

      resetTree(q"""
       val $ctxRef = ($ctx)
       if (($ctxRef : $AnyRefSym) eq null) {
         $f
       } else {
         val $saved = $Local.getContext()
         $Local.setContext($ctxRef)
         try { $f } finally { $Local.setContext($saved) }
       }
       """)
    }

    def localLetClear(f: Tree): Tree = {
      val Local = symbolOf[Local[_]].companion
      localLet(q"$Local.defaultContext()")(f)
    }

    def isolate(f: Tree): Tree =
      localLet(q"${symbolOf[Local[_]].companion}.getContext().mkIsolated")(f)

    def localLetCurrentIf(b: Tree)(f: Tree): Tree = {
      resetTree(q"""
           if (!$b) { $f }
           else ${isolate(f)}
         """)
    }
  }
  /** Represents the current state of all [[Local locals]] for a given
    * execution context.
    *
    * This should be treated as an opaque value and direct modifications
    * and access are considered verboten.
    */
  sealed abstract class Context {
    @tailrec private[misc] final def set(key: Key, value: Any, isPresent: Boolean): Unit = this match {
      case unbound: Unbound =>
        val start = unbound.ref.get()
        val update = if (isPresent) start.updated(key, value) else start - key
        if (!unbound.ref.compareAndSet(start, update)) set(key, value, isPresent)
      case bound: Bound if bound.key == key =>
        bound.hasValue = isPresent
        bound.value = value
      case bound: Bound => bound.rest.set(key, value, isPresent)
    }

    @tailrec private[misc] final def getOr[A](key: Key, default: => A): A = this match {
      case unbound: Unbound => unbound.ref.get().getOrElse(key, default).asInstanceOf[A]
      case bound: Bound if bound.key == key =>
        if (bound.hasValue) bound.value.asInstanceOf[A] else default
      case bound: Bound =>
        bound.rest.getOr(key, default)
    }

    private[misc] final def getOption[A](key: Key): Option[A] = {
      var it = this
      var r: Option[A] = null
      while (r eq null) {
        this match {
          case unbound: Unbound =>
            r = unbound.ref.get().get(key).asInstanceOf[Option[A]]
          case bound: Bound if bound.key == key =>
            r = if (bound.hasValue) Some(bound.value.asInstanceOf[A]) else None
          case bound: Bound =>
            it = bound.rest
        }
      }
      r
    }

    final def mkIsolated: Context = {
      this match {
        case unbound: Unbound =>
          val map = unbound.ref.get()
          new Unbound(AtomicAny(map))
        case _ =>
          var it = this
          var done = false
          var map = Map.empty[Key, Any]
          val bannedKeys = collection.mutable.Set.empty[Key]
          while (!done) {
            it match {
              case unbound: Unbound =>
                done = true
                unbound.ref.get().foreach {
                  case (k, v) if !bannedKeys(k) && !map.contains(k) => map = map.updated(k, v)
                  case _ => ()
                }
              case bound: Bound =>
                if (!map.contains(bound.key) && !bannedKeys(bound.key)) {
                  if (bound.hasValue) map = map.updated(bound.key, bound.value)
                  else bannedKeys += bound.key
                }
                it = bound.rest
            }
          }
          new Unbound(AtomicAny(map))
      }
    }

    final def bind(key: Key, value: Option[Any]): Context =
      new Bound(key, value.orNull, value.isDefined, this)
  }
  private final class Unbound(val ref: AtomicAny[Map[Key, Any]]) extends Context

  private final class Bound(
    val key: Key,
    @volatile var value: Any,
    @volatile var hasValue: Boolean,
    val rest: Context
  ) extends Context
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
final class Local[A](default: () => A) {
  import Local.Key
  val key: Key = new Key

  /** Returns the current value of this `Local`. */
  def apply(): A =
    Local.getKeyOrElse(key, default())

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
    val parent = Local.getContext()
    Local.setContext(parent.bind(key, Some(value)))
    try f
    finally Local.setContext(parent)
  }

  /** Execute a block with the `Local` cleared, restoring the current
    * state upon completion.
    */
  def bindClear[R](f: => R): R = {
    val parent = Local.getContext()
    Local.setContext(parent.bind(key, None))
    try f
    finally Local.setContext(parent)
  }

  /** Clear the Local's value. Other [[Local Locals]] are not modified.
    *
    * General usage should be in [[Local.isolate]] to avoid leaks.
    */
  def clear(): Unit =
    Local.clearKey(key)
}
