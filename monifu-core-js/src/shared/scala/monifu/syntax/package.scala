package monifu

import language.experimental.macros
import scala.reflect.macros.Context
import scala.language.reflectiveCalls

package object syntax {
  /**
   * Provides type-safe equality and inequality operators, implemented with
   * macros for efficiency reasons.
   *
   * In comparison with other solutions for type-safe equality in Scala, this one
   * takes sub-typing out of the picture completely and simply doesn't allow it.
   *
   * {{{
   *   scala> 1.0 === 2.0
   *   res6: Boolean = false
   *
   *   scala> 1.0 === 2.0f
   *   <console>:11: error: Cannot prove that Double =:= Float.
   *                 1.0 === 2.0f
   *                     ^
   *
   *   scala> 1.0 === 1.0
   *   res8: Boolean = true
   *
   *   scala> 1.0 === 1.0f
   *   <console>:11: error: Cannot prove that Double =:= Float.
   *                 1.0 === 1.0f
   *                     ^
   *
   *   scala> 1.0 === 1
   *   <console>:11: error: Cannot prove that Double =:= Int.
   *                 1.0 === 1
   *                     ^
   *
   *   scala> 1 === 1.0
   *   <console>:11: error: Cannot prove that Int =:= Double.
   *                 1 === 1.0
   *                   ^
   * }}}
   */
  implicit class TypeSafeEquals[T](val self: T) extends AnyVal {
    def ===[U](other: U)(implicit ev: T =:= U): Boolean = macro TypeSafeEquals.equalsImpl[T, U]

    def ≟[U](other: U)(implicit ev: T =:= U): Boolean = macro TypeSafeEquals.equalsImpl[T, U]

    def !==[U](other: U)(implicit ev: T =:= U): Boolean = macro TypeSafeEquals.notEqualsImpl[T, U]

    def ≠[U](other: U)(implicit ev: T =:= U): Boolean = macro TypeSafeEquals.notEqualsImpl[T, U]
  }

  object TypeSafeEquals {
    def equalsImpl[T : c.WeakTypeTag, U : c.WeakTypeTag](c: Context)(other: c.Expr[T])(ev: c.Expr[T =:= U]): c.Expr[Boolean] = {
      import c.universe._

      val value = c.Expr[T](Select(c.prefix.tree, newTermName("self")))
      reify {
        value.splice == other.splice
      }
    }

    def notEqualsImpl[T : c.WeakTypeTag, U : c.WeakTypeTag](c: Context)(other: c.Expr[T])(ev: c.Expr[T =:= U]): c.Expr[Boolean] = {
      import c.universe._

      val value = c.Expr[T](Select(c.prefix.tree, newTermName("self")))
      reify {
        value.splice != other.splice
      }
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
