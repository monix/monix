package monifu

import language.experimental.macros
import scala.reflect.macros.Context
import scala.language.reflectiveCalls

package object syntax {
  /**
   * Provides type-safe equality and inequality operators, implemented with
   * macros for efficiency reasons.
   */
  implicit class TypeSafeEquals[T](val self: T) extends AnyVal {
    def ===[U](other: U): Boolean = macro TypeSafeEquals.equalsImpl[T, U]

    def ≟[U](other: U): Boolean = macro TypeSafeEquals.equalsImpl[T, U]

    def !==[U](other: U): Boolean = macro TypeSafeEquals.notEqualsImpl[T, U]

    def ≠[U](other: U): Boolean = macro TypeSafeEquals.notEqualsImpl[T, U]
  }

  object TypeSafeEquals {
    def equalsImpl[T : c.WeakTypeTag, U : c.WeakTypeTag](c: Context { type PrefixType = TypeSafeEquals[T] })(other: c.Expr[T]): c.Expr[Boolean] = {
      import c.universe._

      def canProve[A : WeakTypeTag] =
        c.inferImplicitValue(weakTypeOf[A], silent=true) != EmptyTree

      if (canProve[T <:< U] || canProve[U <:< T])
        reify(c.prefix.splice.self == other.splice)
      else
        c.abort(c.macroApplication.pos, s"Cannot compare unrelated types ${weakTypeOf[T]} and ${weakTypeOf[U]}")
    }

    def notEqualsImpl[T : c.WeakTypeTag, U : c.WeakTypeTag](c: Context { type PrefixType = TypeSafeEquals[T] })(other: c.Expr[T]): c.Expr[Boolean] = {
      import c.universe._
      val equality = equalsImpl[T, U](c)(other)
      reify(!equality.splice)
    }
  }
}
