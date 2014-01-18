package monifu.concurrent

import language.experimental.macros 
import reflect.macros.Context
import java.util.concurrent.TimeoutException

private[concurrent] object Macros {
  def interruptedCheck(): Unit = macro interruptedCheckImpl

  def interruptedCheckImpl(c: Context)(): c.Expr[Unit] = {
    import c.universe._
    reify {
      if (Thread.interrupted)
        throw new InterruptedException()
    }
  }

  def timeoutCheck(endsAtNanos: Long): Unit = macro timeoutCheckImpl

  def timeoutCheckImpl(c: Context)(endsAtNanos: c.Expr[Long]): c.Expr[Unit] = {
    import c.universe._
    reify {
      if (System.nanoTime >= endsAtNanos.splice)
        throw new TimeoutException()
    }
  }
}