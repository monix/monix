package monifu

import language.experimental.macros
import scala.reflect.macros.Context
import scala.language.reflectiveCalls

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

  /**
   * Implementation for a `foreach` extension method meant for closeable resources,
   * like input streams, for safe disposal of resources.
   *
   * This example: {{{
   *   import monifu.syntax.ForeachCloseable
   *   import java.io.{FileReader, BufferedReader}
   *
   *   val firstLine =
   *     for (in <- new BufferedReader(new FileReader("file.txt"))) {
   *        in.readLine()
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
   *   import monifu.syntax.ForeachCloseable
   *   class Foo { def close() = println("CLOSING") }
   *
   *   for (f <- new Foo) {
   *     println("Yay!")
   *   }
   * }}}
   *
   * Nesting works too: {{{
   *   for (in <- new BufferedReader(new FileReader("file.txt")); out <- new BufferedWriter(new FileWriter("out.txt"))) {
   *     out.write(in.readLine())
   *   }
   * }}}
   */
  implicit class ForeachCloseable[T <: { def close(): Unit }](val self: T) extends AnyVal {
    def foreach[R](f: T => R): R = macro ForeachCloseable.foreachImpl[T,R]
  }

  object ForeachCloseable {
    def foreachImpl[T <: { def close(): Unit } : c.WeakTypeTag, R : c.WeakTypeTag](c: Context)(f: c.Expr[T => R]): c.Expr[R] = {
      import c.universe._

      val self = c.Expr[T](Select(c.prefix.tree, newTermName("self")))
      reify {
        val closeable = self.splice
        try { f.splice(closeable) } finally { closeable.close() }
      }
    }
  }
}
