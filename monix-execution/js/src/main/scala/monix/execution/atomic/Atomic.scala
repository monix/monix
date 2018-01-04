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

package monix.execution.atomic


import monix.execution.misc._
import scala.reflect.macros.whitebox
import monix.execution.atomic.PaddingStrategy.NoPadding
import scala.language.experimental.macros

/**
  * Base trait of all atomic references, no matter the type.
  */
abstract class Atomic[A] extends Serializable {
  /** Get the current value persisted by this Atomic. */
  def get: A

  /** Get the current value persisted by this Atomic, an alias for `get()`. */
  final def apply(): A = macro Atomic.Macros.applyMacro[A]

  /** Updates the current value.
    *
    * @param update will be the new value returned by `get()`
    */
  def set(update: A): Unit

  /** Alias for [[set]]. Updates the current value.
    *
    * @param value will be the new value returned by `get()`
    */
  final def update(value: A): Unit = macro Atomic.Macros.setMacro[A]

  /** Alias for [[set]]. Updates the current value.
    *
    * @param value will be the new value returned by `get()`
    */
  final def `:=`(value: A): Unit = macro Atomic.Macros.setMacro[A]

  /** Does a compare-and-set operation on the current value. For more info, checkout the related
    * [[https://en.wikipedia.org/wiki/Compare-and-swap Compare-and-swap Wikipedia page]].
    *
    * It's an atomic, worry free operation.
    *
    * @param expect is the value you expect to be persisted when the operation happens
    * @param update will be the new value, should the check for `expect` succeeds
    * @return either true in case the operation succeeded or false otherwise
    */
  def compareAndSet(expect: A, update: A): Boolean

  /** Sets the persisted value to `update` and returns the old value that was in place.
    * It's an atomic, worry free operation.
    */
  def getAndSet(update: A): A

  /** Eventually sets to the given value.
    * Has weaker visibility guarantees than the normal `set()`.
    */
  final def lazySet(value: A): Unit = macro Atomic.Macros.setMacro[A]

  /** Abstracts over `compareAndSet`. You specify a transformation by specifying a callback to be
    * executed, a callback that transforms the current value. This method will loop until it will
    * succeed in replacing the current value with the one produced by your callback.
    *
    * Note that the callback will be executed on each iteration of the loop, so it can be called
    * multiple times - don't do destructive I/O or operations that mutate global state in it.
    *
    * @param cb is a callback that receives the current value as input and returns a tuple that specifies
    *           the update + what should this method return when the operation succeeds.
    * @return whatever was specified by your callback, once the operation succeeds
    */
  final def transformAndExtract[U](cb: (A) => (U, A)): U =
    macro Atomic.Macros.transformAndExtractMacro[A, U]

  /** Abstracts over `compareAndSet`. You specify a transformation by specifying a callback to be
    * executed, a callback that transforms the current value. This method will loop until it will
    * succeed in replacing the current value with the one produced by the given callback.
    *
    * Note that the callback will be executed on each iteration of the loop, so it can be called
    * multiple times - don't do destructive I/O or operations that mutate global state in it.
    *
    * @param cb is a callback that receives the current value as input and returns the `update` which is the
    *           new value that should be persisted
    * @return whatever the update is, after the operation succeeds
    */
  final def transformAndGet(cb: (A) => A): A =
    macro Atomic.Macros.transformAndGetMacro[A]

  /** Abstracts over `compareAndSet`. You specify a transformation by specifying a callback to be
    * executed, a callback that transforms the current value. This method will loop until it will
    * succeed in replacing the current value with the one produced by the given callback.
    *
    * Note that the callback will be executed on each iteration of the loop, so it can be called
    * multiple times - don't do destructive I/O or operations that mutate global state in it.
    *
    * @param cb is a callback that receives the current value as input and returns the `update` which is the
    *           new value that should be persisted
    * @return the old value, just prior to when the successful update happened
    */
  final def getAndTransform(cb: (A) => A): A =
    macro Atomic.Macros.getAndTransformMacro[A]

  /** Abstracts over `compareAndSet`. You specify a transformation by specifying a callback to be
    * executed, a callback that transforms the current value. This method will loop until it will
    * succeed in replacing the current value with the one produced by the given callback.
    *
    * Note that the callback will be executed on each iteration of the loop, so it can be called
    * multiple times - don't do destructive I/O or operations that mutate global state in it.
    *
    * @param cb is a callback that receives the current value as input and returns the `update` which is the
    *           new value that should be persisted
    */
  final def transform(cb: (A) => A): Unit =
    macro Atomic.Macros.transformMacro[A]
}

object Atomic {
  /** Constructs an `Atomic[A]` reference.
    *
    * Based on the `initialValue`, it will return the best, most
    * specific type. E.g. you give it a number, it will return
    * something inheriting from `AtomicNumber[A]`. That's why it takes
    * an `AtomicBuilder[T, R]` as an implicit parameter - but worry
    * not about such details as it just works.
    *
    * @param initialValue is the initial value with which to
    *        initialize the Atomic reference
    *
    * @param builder is the builder that helps us to build the
    *        best reference possible, based on our `initialValue`
    */
  def apply[A, R <: Atomic[A]](initialValue: A)(implicit builder: AtomicBuilder[A, R]): R =
    macro Atomic.Macros.buildAnyMacro[A, R]

  /** Constructs an `Atomic[A]` reference, applying the provided
    * [[PaddingStrategy]] in order to counter the "false sharing"
    * problem.
    *
    * Based on the `initialValue`, it will return the best, most
    * specific type. E.g. you give it a number, it will return
    * something inheriting from `AtomicNumber[A]`. That's why it takes
    * an `AtomicBuilder[A, R]` as an implicit parameter - but worry
    * not about such details as it just works.
    *
    * Note that for ''Scala.js'' we aren't applying any padding, as it
    * doesn't make much sense, since Javascript execution is single
    * threaded, but this builder is provided for syntax compatibility
    * anyway across the JVM and Javascript and we never know how
    * Javascript engines will evolve.
    *
    * @param initialValue is the initial value with which to
    *        initialize the Atomic reference
    *
    * @param padding is the [[PaddingStrategy]] to apply
    *
    * @param builder is the builder that helps us to build the
    *        best reference possible, based on our `initialValue`
    */
  def withPadding[A, R <: Atomic[A]](initialValue: A, padding: PaddingStrategy)(implicit builder: AtomicBuilder[A, R]): R =
    macro Atomic.Macros.buildAnyWithPaddingMacro[A, R]

  /** Returns the builder that would be chosen to construct Atomic
    * references for the given `initialValue`.
    */
  def builderFor[A, R <: Atomic[A]](initialValue: A)(implicit builder: AtomicBuilder[A, R]): AtomicBuilder[A, R] =
    builder

  /** Macros implementations for the [[Atomic]] type */
  class Macros(override val c: whitebox.Context) extends InlineMacros with HygieneUtilMacros {
    import c.universe._

    def transformMacro[A : c.WeakTypeTag](cb: c.Expr[A => A]): c.Expr[Unit] = {
      val selfExpr = c.Expr[Atomic[A]](c.prefix.tree)
      val self = util.name("self")

      /* If our arguments are all clean (stable identifiers or simple functions)
       * then inline them directly, otherwise bind arguments to a val for safety.
       */
      val tree =
        if (util.isClean(cb))
          q"""
          val $self = $selfExpr
          $self.set($cb($self.get))
          """
        else {
          val fn = util.name("fn")
          q"""
          val $self = $selfExpr
          val $fn = $cb
          $self.set($fn($self.get))
          """
        }

      inlineAndReset[Unit](tree)
    }

    def transformAndGetMacro[A : c.WeakTypeTag](cb: c.Expr[A => A]): c.Expr[A] = {
      val selfExpr = c.Expr[Atomic[A]](c.prefix.tree)
      val self = util.name("self")
      val current = util.name("current")
      val update = util.name("update")

      /* If our arguments are all clean (stable identifiers or simple functions)
       * then inline them directly, otherwise bind arguments to a val for safety.
       */
      val tree =
        if (util.isClean(cb)) {
          q"""
          val $self = $selfExpr
          val $current = $self.get
          val $update = $cb($current)
          $self.set($update)
          $update
          """
        } else {
          val fn = util.name("fn")
          q"""
          val $self = $selfExpr
          val $fn = $cb
          val $current = $self.get
          val $update = $fn($current)
          $self.set($update)
          $update
          """
        }

      inlineAndReset[A](tree)
    }

    def getAndTransformMacro[A : c.WeakTypeTag](cb: c.Expr[A => A]): c.Expr[A] = {
      val selfExpr = c.Expr[Atomic[A]](c.prefix.tree)
      val self = util.name("self")
      val current = util.name("current")
      val update = util.name("update")

      /* If our arguments are all clean (stable identifiers or simple functions)
       * then inline them directly, otherwise bind arguments to a val for safety.
       */
      val tree =
        if (util.isClean(cb)) {
          q"""
          val $self = $selfExpr
          val $current = $self.get
          val $update = $cb($current)
          $self.set($update)
          $current
          """
        } else {
          val fn = util.name("fn")
          q"""
          val $self = $selfExpr
          val $fn = $cb
          val $current = $self.get
          val $update = $fn($current)
          $self.set($update)
          $current
          """
        }

      inlineAndReset[A](tree)
    }

    def transformAndExtractMacro[S : c.WeakTypeTag, A : c.WeakTypeTag]
      (cb: c.Expr[S => (A, S)]): c.Expr[A] = {

      val selfExpr = c.Expr[Atomic[S]](c.prefix.tree)
      val self = util.name("self")
      val current = util.name("current")
      val update = util.name("update")
      val result = util.name("result")

      /* If our arguments are all clean (stable identifiers or simple functions)
       * then inline them directly, otherwise bind arguments to a val for safety.
       */
      val tree =
        if (util.isClean(cb)) {
          q"""
          val $self = $selfExpr
          val $current = $self.get
          val ($result, $update) = $cb($current)
          $self.set($update)
          $result
          """
        } else {
          val fn = util.name("fn")
          q"""
          val $self = $selfExpr
          val $fn = $cb
          val $current = $self.get
          val ($result, $update) = $fn($current)
          $self.set($update)
          $result
          """
        }

      inlineAndReset[A](tree)
    }

    def buildAnyMacro[A : c.WeakTypeTag, R <: Atomic[A] : c.WeakTypeTag]
      (initialValue: c.Expr[A])
      (builder: c.Expr[AtomicBuilder[A, R]]): c.Expr[R] = {

      val expr = reify {
        builder.splice.buildInstance(initialValue.splice, NoPadding, allowPlatformIntrinsics = true)
      }

      inlineAndReset[R](expr.tree)
    }

    def buildAnyWithPaddingMacro[A : c.WeakTypeTag, R <: Atomic[A] : c.WeakTypeTag]
      (initialValue: c.Expr[A], padding: c.Expr[PaddingStrategy])
      (builder: c.Expr[AtomicBuilder[A, R]]): c.Expr[R] = {

      val expr = reify {
        builder.splice.buildInstance(initialValue.splice, padding.splice, allowPlatformIntrinsics = true)
      }

      inlineAndReset[R](expr.tree)
    }

    def applyMacro[A : c.WeakTypeTag](): c.Expr[A] = {
      val selfExpr = c.Expr[Atomic[A]](c.prefix.tree)
      val tree = q"""$selfExpr.get"""
      inlineAndReset[A](tree)
    }

    def setMacro[A : c.WeakTypeTag](value: c.Expr[A]): c.Expr[Unit] = {
      val selfExpr = c.Expr[Atomic[A]](c.prefix.tree)
      val tree = q"""$selfExpr.set($value)"""
      inlineAndReset[Unit](tree)
    }

    def addMacro[A : c.WeakTypeTag](value: c.Expr[A]): c.Expr[Unit] = {
      val selfExpr = c.Expr[Atomic[A]](c.prefix.tree)
      val tree = q"""$selfExpr.add($value)"""
      inlineAndReset[Unit](tree)
    }

    def subtractMacro[A : c.WeakTypeTag](value: c.Expr[A]): c.Expr[Unit] = {
      val selfExpr = c.Expr[Atomic[A]](c.prefix.tree)
      val tree = q"""$selfExpr.subtract($value)"""
      inlineAndReset[Unit](tree)
    }
  }
}
