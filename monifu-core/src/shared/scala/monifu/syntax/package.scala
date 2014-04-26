package monifu

import language.experimental.macros
import scala.reflect.macros.Context

package object syntax {
  /**
   * Provides a type-safe equality operator,
   * implemented with macros for efficiency reasons.
   */
  implicit class TypeSafeEquals[T](val self: T) extends AnyVal {
    def ===(other: T): Boolean = macro TypeSafeEquals.equalsImpl[T]

    def ≟(other: T): Boolean = macro TypeSafeEquals.equalsImpl[T]
  }

  object TypeSafeEquals {
    def equalsImpl[T : c.WeakTypeTag](c: Context)(other: c.Expr[T]): c.Expr[Boolean] = {
      import c.universe._

      val value = c.Expr[T](Select(c.prefix.tree, newTermName("self")))
      reify {
        value.splice == other.splice
      }
    }
  }
}
