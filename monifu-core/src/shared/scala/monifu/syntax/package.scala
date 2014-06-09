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
    def equalsImpl[T : c.WeakTypeTag, U : c.WeakTypeTag](c: Context)(other: c.Expr[T]): c.Expr[Boolean] = {
      import c.universe._

      def canProve[A : WeakTypeTag] =
        c.inferImplicitValue(weakTypeOf[A], silent=true) != EmptyTree

      val selfExpr = c.Expr[T](Select(c.prefix.tree, newTermName("self")))
      if (canProve[T <:< U] || canProve[U <:< T])
        reify(selfExpr.splice == other.splice)
      else
        c.abort(c.macroApplication.pos, s"Cannot compare unrelated types ${weakTypeOf[T]} and ${weakTypeOf[U]}")
    }

    def notEqualsImpl[T : c.WeakTypeTag, U : c.WeakTypeTag](c: Context)(other: c.Expr[T]): c.Expr[Boolean] = {
      import c.universe._
      val equality = equalsImpl[T, U](c)(other)
      reify(!equality.splice)
    }
  }

  /**
   * Implementation for a `tryThenClose` extension method meant for closeable resources,
   * like input streams, for safe disposal of resources.
   *
   * This example: {{{
   *   import monifu.syntax.TryThenClose
   *   import java.io.{FileReader, BufferedReader}
   *
   *   val firstLine =
   *     new BufferedReader(new FileReader("file.txt")).tryThenClose { in =>
   *       in.readLine()
   *     }
   * }}}
   *
   * Is equivalent to this: {{{
   *   import java.io.{FileReader, BufferedReader}
   *
   *   val fistLine = {
   *     val in = new BufferedReader(new FileReader("file.txt"))
   *     try {
   *       in.readLine()
   *     } finally {
   *       in.close()
   *     }
   *   }
   * }}}
   *
   * Actually for this to work it doesn't have to be a Java
   * [[http://docs.oracle.com/javase/7/docs/api/java/io/Closeable.html Closable]] as the type-checking is based
   * on structural typing (yet because it's implemented as a macro, the actual call is not reflective).
   *
   * {{{
   *   import monifu.syntax.TryThenClose
   *   class Foo { def close() = println("CLOSING") }
   *
   *   new Foo().tryThenClose { _ =>
   *     println("Yay!")
   *   }
   * }}}
   */
  implicit class TryThenClose[T <: { def close(): Unit }](val self: T) extends AnyVal {
    def tryThenClose[R](f: T => R): R = macro TryThenClose.tryThenCloseImpl[T,R]
  }

  object TryThenClose {
    def tryThenCloseImpl[T <: { def close(): Unit } : c.WeakTypeTag, R : c.WeakTypeTag](c: Context)(f: c.Expr[T => R]): c.Expr[R] = {
      import c.universe._

      val self = c.Expr[T](Select(c.prefix.tree, newTermName("self")))
      reify {
        val closeable = self.splice
        try { f.splice(closeable) } finally { closeable.close() }
      }
    }
  }
}
