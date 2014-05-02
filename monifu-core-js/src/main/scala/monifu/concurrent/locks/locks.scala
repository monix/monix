package monifu.concurrent.locks

import scala.reflect.macros.Context
import language.experimental.macros
import scala.concurrent.duration.TimeUnit

object _Macros {
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
  def lock[T](cb: => T): T = macro _Macros.lockMacroImpl[T]

  def lockInterruptibly[T](cb: => T): T = macro _Macros.lockMacroImpl[T]

  def tryLock[T](cb: => T): Boolean = macro _Macros.tryLockMacro[T]

  def tryLock[T](time: Long, unit: TimeUnit, cb: => T): Boolean = macro _Macros.tryLockDurationMacro[T]
}

/**
 * Provided for Scala.js for source-level compatibility purposes.
 * Usage does not imply any overhead.
 */
object ReadWriteLockImpl {
  def readLock[T](cb: => T): T = macro _Macros.lockMacroImpl[T]

  def writeLock[T](cb: => T): T = macro _Macros.lockMacroImpl[T]

  def acquire[T](cb: => T): T = macro _Macros.lockMacroImpl[T]
}

