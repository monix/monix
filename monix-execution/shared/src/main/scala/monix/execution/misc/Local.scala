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

package monix.execution.misc

import monix.execution.atomic.AtomicAny
import scala.annotation.tailrec
import scala.reflect.macros.whitebox

/**
  * @define canBindLocalsDesc The implementation uses the [[CanBindLocals]]
  *         type class because in case of asynchronous data types that
  *         should be waited on, like `Future` or `CompletableFuture`,
  *         then the locals context also needs to be cleared on the
  *         future's completion, for correctness.
  *
  *         There's no default instance for synchronous actions available
  *         in scope. If you need to work with synchronous actions, you
  *         need to import it explicitly:
  *         {{{
  *           import monix.execution.misc.CanBindLocals.Implicits.synchronousAsDefault
  *         }}}
  */
object Local extends LocalCompanionDeprecated {
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

  /**
    * Creates a new, empty [[Context]].
    */
  def newContext(): Context = new Unbound(AtomicAny(Map()))

  /** Current [[Context]] kept in a `ThreadLocal`. */
  private[this] val localContext: ThreadLocal[Context] =
    ThreadLocal(newContext())

  /** Return the state of the current Local state. */
  def getContext(): Context =
    localContext.get()

  /** Restore the Local state to a given Context. */
  def setContext(ctx: Context): Unit =
    localContext.set(ctx)

  /** Clear the Local state. */
  def clearContext(): Unit =
    localContext.set(newContext())

  /** Execute a  block of code without propagating any `Local.Context`
    * changes outside.
    *
    * $canBindLocalsDesc
    */
  def isolate[R](f: => R)(implicit R: CanBindLocals[R]): R =
    R.isolate(f)

  /** Execute a block of code using the specified state of
    * `Local.Context` and restore the current state when complete.
    *
    * $canBindLocalsDesc
    */
  def bind[R](ctx: Context)(f: => R)(implicit R: CanBindLocals[R]): R =
    if (ctx != null) R.bindContext(ctx)(f) else f

  /** Execute a block of code with a clear state of `Local.Context`
    * and restore the current state when complete.
    *
    * $canBindLocalsDesc
    */
  def bindClear[R](f: => R)(implicit R: CanBindLocals[R]): R =
    CanBindLocals[R].bindContext(Local.newContext())(f)

  /** Convert a closure `() => R` into another closure of the same
    * type whose [[Local.Context]] is saved when calling closed
    * and restored upon invocation.
    */
  def closed[R](fn: () => R)(implicit R: CanBindLocals[R]): () => R = {
    val closure = Local.getContext()
    () => Local.bind(closure)(fn())
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

  /** If `b` evaluates to `true`, execute a block of code using a current
    * state of `Local.Context` and restore the current state when complete.
    */
  private[monix] def bindCurrentIf[R](b: Boolean)(f: => R): R =
    macro Macros.localLetCurrentIf

  /** Macros implementations for [[bind]] and [[bindClear]]. */
  private class Macros(override val c: whitebox.Context) extends InlineMacros with HygieneUtilMacros {
    import c.universe._

    def localLet(ctx: Tree)(f: Tree): Tree =
      c.abort(c.macroApplication.pos, "Macro no longer implemented!")
    def localLetClear(f: Tree): Tree =
      c.abort(c.macroApplication.pos, "Macro no longer implemented!")
    def isolate(f: Tree): Tree =
      c.abort(c.macroApplication.pos, "Macro no longer implemented!")

    def localLetCurrentIf(b: Tree)(f: Tree): Tree = {
      val Local = symbolOf[Local[_]].companion
      val CanBindLocals = symbolOf[CanBindLocals[_]].companion

      resetTree(
        q"""if (!$b) { $f } else {
           import $CanBindLocals.Implicits.synchronousAsDefault
           $Local.isolate($f)
        }"""
      )
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

    final def bind(key: Key, value: Option[Any]): Context =
      new Bound(key, value.orNull, value.isDefined, this)

    final def isolate(): Context =
      isolateLoop()

    /**
      * DEPRECATED — renamed to [[isolate]].
      */
    @deprecated("Renamed to isolate()", "3.0.0")
    private[misc] def mkIsolated(): Unbound = {
      // $COVERAGE-OFF$
      isolateLoop()
      // $COVERAGE-ON$
    }

    private[this] final def isolateLoop(): Unbound =
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

  private[execution] final class Unbound(val ref: AtomicAny[Map[Key, Any]]) extends Context

  private[execution] final class Bound(
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
  *
  * @define canBindLocalsDesc The implementation uses the [[CanBindLocals]]
  *         type class because in case of asynchronous data types that
  *         should be waited on, like `Future` or `CompletableFuture`,
  *         then the locals context also needs to be cleared on the
  *         future's completion, for correctness.
  */
final class Local[A](default: () => A) extends LocalDeprecated[A] {
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
    *
    * $canBindLocalsDesc
    */
  def bind[R](value: A)(f: => R)(implicit R: CanBindLocals[R]): R =
    R.bindKey(this, Some(value))(f)

  /** Execute a block with the `Local` cleared, restoring the current
    * state upon completion.
    *
    * $canBindLocalsDesc
    */
  def bindClear[R](f: => R)(implicit R: CanBindLocals[R]): R =
    R.bindKey(this, None)(f)

  /** Clear the Local's value. Other [[Local Locals]] are not modified.
    *
    * General usage should be in [[Local.isolate[R](f:=>R* Local.isolate]] to avoid leaks.
    */
  def clear(): Unit =
    Local.clearKey(key)
}
