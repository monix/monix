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

package monix.execution.atomic

import monix.execution.atomic.internal._
import scala.reflect.macros.whitebox
import monix.execution.atomic.PaddingStrategy.NoPadding
import scala.annotation.nowarn

/**
  * Base trait of all atomic references, no matter the type.
  */
abstract class Atomic[A] extends Serializable with internal.AtomicDocs {
  /** $atomicGetDesc */
  def get(): A

  /** $atomicSetDesc */
  def set(update: A): Unit

  /** $compareAndSetDesc
    * 
    * $atomicBestPractices 
    *
    * @param expect $atomicCASExpectParam
    * @param update $atomicCASUpdateParam
    * @return $atomicCASReturn $atomicCASReturn
    */
  def compareAndSet(expect: A, update: A): Boolean

  /** $atomicGetAndSetDesc 
    * 
    * @param update $atomicGetAndSetParam
    * @return $atomicGetAndSetReturn
    */
  def getAndSet(update: A): A

  /** $atomicLazySetDesc */
  final def lazySet(value: A): Unit = macro Atomic.Macros.setMacro[A]

  /** $atomicTransformExtractDesc
    *
    * $atomicTransformBestPractices 
    *
    * @param f $atomicTransformExtractParamF
    * 
    * @return $atomicTransformExtractReturn   
    */
  final def transformAndExtract[U](f: A => (U, A)): U =
    macro Atomic.Macros.transformAndExtractMacro[A, U]

  /** $atomicTransformAndGetDesc
    * 
    * $atomicTransformBestPractices 
    *
    * @param f $atomicTransformParam
    * 
    * @return $atomicTransformAndGetReturn
    */
  final def transformAndGet(f: A => A): A =
    macro Atomic.Macros.transformAndGetMacro[A]

  /** $atomicGetAndTransformDesc
    *
    * $atomicTransformBestPractices 
    *
    * @param f $atomicTransformParam
    * 
    * @return $atomicGetAndTransformReturn
    */
  final def getAndTransform(f: A => A): A =
    macro Atomic.Macros.getAndTransformMacro[A]

  /** $atomicTransformDesc
    *
    * $atomicTransformBestPractices 
    * 
    * @param f $atomicTransformParam
    */
  final def transform(f: A => A): Unit =
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
  def withPadding[A, R <: Atomic[A]](initialValue: A, padding: PaddingStrategy)(
    implicit builder: AtomicBuilder[A, R]
  ): R =
    macro Atomic.Macros.buildAnyWithPaddingMacro[A, R]

  /** Returns the builder that would be chosen to construct Atomic
    * references for the given `initialValue`.
    */
  @nowarn("cat=unused-params")
  def builderFor[A, R <: Atomic[A]](initialValue: A)(implicit builder: AtomicBuilder[A, R]): AtomicBuilder[A, R] =
    builder

  implicit final class DeprecatedExtensions[A](val self: Atomic[A]) extends AnyVal {
    /** DEPRECATED - switch to [[Atomic.get]]. */
    @deprecated("Switch to .get()", "4.0.0")
    def apply(): A = {
      // $COVERAGE-OFF$
      self.get()
      // $COVERAGE-ON$
    }

    /** DEPRECATED — switch to [[Atomic.set]]. */
    @deprecated("Switch to .set()", "4.0.0")
    def update(value: A): Unit = {
      // $COVERAGE-OFF$
      self.set(value)
      // $COVERAGE-ON$
    }

    /** DEPRECATED — switch to [[Atomic.set]]. */
    @deprecated("Switch to .set()", "4.0.0")
    def `:=`(value: A): Unit = {
      // $COVERAGE-OFF$
      self.set(value)
      // $COVERAGE-ON$
    }
  }

  /** Macros implementations for the [[Atomic]] type */
  class Macros(override val c: whitebox.Context) extends InlineMacros with HygieneUtilMacros {
    import c.universe._

    def transformMacro[A](f: c.Expr[A => A]): c.Expr[Unit] = {
      val selfExpr = c.Expr[Atomic[A]](c.prefix.tree)
      val self = util.name("self")

      /* If our arguments are all clean (stable identifiers or simple functions)
       * then inline them directly, otherwise bind arguments to a val for safety.
       */
      val tree =
        if (util.isClean(f))
          q"""
          val $self = $selfExpr
          $self.set($f($self.get()))
          """
        else {
          val fn = util.name("fn")
          q"""
          val $self = $selfExpr
          val $fn = $f
          $self.set($fn($self.get()))
          """
        }
      inlineAndReset[Unit](tree)
    }

    def transformAndGetMacro[A](f: c.Expr[A => A]): c.Expr[A] = {
      val selfExpr = c.Expr[Atomic[A]](c.prefix.tree)
      val self = util.name("self")
      val current = util.name("current")
      val update = util.name("update")

      /* If our arguments are all clean (stable identifiers or simple functions)
       * then inline them directly, otherwise bind arguments to a val for safety.
       */
      val tree =
        if (util.isClean(f)) {
          q"""
          val $self = $selfExpr
          val $current = $self.get()
          val $update = $f($current)
          $self.set($update)
          $update
          """
        } else {
          val fn = util.name("fn")
          q"""
          val $self = $selfExpr
          val $fn = $f
          val $current = $self.get()
          val $update = $fn($current)
          $self.set($update)
          $update
          """
        }

      inlineAndReset[A](tree)
    }

    def getAndTransformMacro[A](f: c.Expr[A => A]): c.Expr[A] = {
      val selfExpr = c.Expr[Atomic[A]](c.prefix.tree)
      val self = util.name("self")
      val current = util.name("current")
      val update = util.name("update")

      /* If our arguments are all clean (stable identifiers or simple functions)
       * then inline them directly, otherwise bind arguments to a val for safety.
       */
      val tree =
        if (util.isClean(f)) {
          q"""
          val $self = $selfExpr
          val $current = $self.get()
          val $update = $f($current)
          $self.set($update)
          $current
          """
        } else {
          val fn = util.name("fn")
          q"""
          val $self = $selfExpr
          val $fn = $f
          val $current = $self.get()
          val $update = $fn($current)
          $self.set($update)
          $current
          """
        }

      inlineAndReset[A](tree)
    }

    def transformAndExtractMacro[S, A](f: c.Expr[S => (A, S)]): c.Expr[A] = {
      val selfExpr = c.Expr[Atomic[S]](c.prefix.tree)
      val self = util.name("self")
      val current = util.name("current")
      val update = util.name("update")
      val result = util.name("result")

      /* If our arguments are all clean (stable identifiers or simple functions)
       * then inline them directly, otherwise bind arguments to a val for safety.
       */
      val tree =
        if (util.isClean(f)) {
          q"""
          val $self = $selfExpr
          val $current = $self.get()
          val ($result, $update) = $f($current)
          $self.set($update)
          $result
          """
        } else {
          val fn = util.name("fn")
          q"""
          val $self = $selfExpr
          val $fn = $f
          val $current = $self.get()
          val ($result, $update) = $fn($current)
          $self.set($update)
          $result
          """
        }
      inlineAndReset[A](tree)
    }

    def buildAnyMacro[A, R <: Atomic[A]](initialValue: c.Expr[A])(
      builder: c.Expr[AtomicBuilder[A, R]]
    ): c.Expr[R] = {
      val expr = reify {
        builder.splice.buildInstance(initialValue.splice, NoPadding, allowPlatformIntrinsics = true)
      }
      inlineAndReset[R](expr.tree)
    }

    def buildAnyWithPaddingMacro[A, R <: Atomic[A]](
      initialValue: c.Expr[A],
      padding: c.Expr[PaddingStrategy]
    )(builder: c.Expr[AtomicBuilder[A, R]]): c.Expr[R] = {
      val expr = reify {
        builder.splice.buildInstance(initialValue.splice, padding.splice, allowPlatformIntrinsics = true)
      }
      inlineAndReset[R](expr.tree)
    }

    def applyMacro[A](): c.Expr[A] = {
      val selfExpr = c.Expr[Atomic[A]](c.prefix.tree)
      val tree = q"""$selfExpr.get()"""
      inlineAndReset[A](tree)
    }

    def setMacro[A](value: c.Expr[A]): c.Expr[Unit] = {
      val selfExpr = c.Expr[Atomic[A]](c.prefix.tree)
      val tree = q"""$selfExpr.set($value)"""
      inlineAndReset[Unit](tree)
    }

    def addMacro[A](value: c.Expr[A]): c.Expr[Unit] = {
      val selfExpr = c.Expr[Atomic[A]](c.prefix.tree)
      val tree = q"""$selfExpr.add($value)"""
      inlineAndReset[Unit](tree)
    }

    def subtractMacro[A](value: c.Expr[A]): c.Expr[Unit] = {
      val selfExpr = c.Expr[Atomic[A]](c.prefix.tree)
      val tree = q"""$selfExpr.subtract($value)"""
      inlineAndReset[Unit](tree)
    }
  }
}
