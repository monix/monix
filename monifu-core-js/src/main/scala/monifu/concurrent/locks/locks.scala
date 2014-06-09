package monifu.concurrent.locks

import scala.reflect.macros.blackbox.Context
import language.experimental.macros
import scala.concurrent.duration.TimeUnit

object Macros {
  /**
   * A macro that takes an expression and spits it out unaltered,
   * used for no-op, zero-overhead methods.
   */
  def lockMacroImpl[T : c.WeakTypeTag](c: Context)(cb: c.Expr[T]): c.Expr[T] = {
    import c.universe._
    reify(cb.splice)
  }

  /**
   * A macro that takes an expression and spits it out unaltered,
   * used for no-op, zero-overhead methods.
   */
  def tryLockMacro[T : c.WeakTypeTag](c: Context)(cb: c.Expr[T]): c.Expr[Boolean] = {
    import c.universe._
    reify {
      cb.splice
      true
    }
  }

  /**
   * A macro that takes an expression and spits it out unaltered,
   * used for no-op, zero-overhead methods.
   */
  def tryLockDurationMacro[T : c.WeakTypeTag](c: Context)(time: c.Expr[Long], unit: c.Expr[TimeUnit], cb: c.Expr[T]): c.Expr[Boolean] = {
    import c.universe._
    reify {
      cb.splice
      true
    }
  }
}

/**
 * Provided for Scala.js for source-level compatibility purposes.
 * Usage does not imply any overhead.
 */
object LockImpl {
  def enter[T](cb: => T): T = macro Macros.lockMacroImpl[T]

  def enterInterruptibly[T](cb: => T): T = macro Macros.lockMacroImpl[T]

  def tryEnter[T](cb: => T): Boolean = macro Macros.tryLockMacro[T]

  def tryEnter[T](time: Long, unit: TimeUnit, cb: => T): Boolean = macro Macros.tryLockDurationMacro[T]
}

