package monifu.concurrent.locks

import scala.reflect.macros.Context
import language.experimental.macros

object Macro {
  /**
   * A macro that takes an expression and spits it out unaltered,
   * used for no-op, zero-overhead methods.
   */
  def noOperation[T : c.WeakTypeTag](c: Context)(cb: c.Expr[T]): c.Expr[T] = {
    import c.universe._
    reify(cb.splice)
  }
}

/**
 * Provided for Scala.js for source-level compatibility purposes.
 * Usage does not imply any overhead.
 */
object LockImpl {
  def acquire[T](cb: => T): T = macro Macro.noOperation[T]
}

/**
 * Provided for Scala.js for source-level compatibility purposes.
 * Usage does not imply any overhead.
 */
object ReadWriteLockImpl {
  def readLock[T](cb: => T): T = macro Macro.noOperation[T]

  def writeLock[T](cb: => T): T = macro Macro.noOperation[T]

  def acquire[T](cb: => T): T = macro Macro.noOperation[T]
}

