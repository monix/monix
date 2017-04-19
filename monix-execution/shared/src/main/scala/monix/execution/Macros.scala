/*
 * Copyright (c) 2014-2016 by its authors. Some rights reserved.
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

package monix.execution

import monix.execution.Ack.{AckExtensions, Continue, Stop}
import monix.execution.misc.{HygieneUtilMacros, InlineMacros}
import monix.execution.schedulers.{StartAsyncBatchRunnable, TrampolinedRunnable}
import scala.concurrent.Future
import scala.reflect.macros.whitebox

/** Various implementations for
  * [[monix.execution.Ack.AckExtensions AckExtensions]] and
  * [[monix.execution.Scheduler Scheduler]].
  */
@macrocompat.bundle
class Macros(override val c: whitebox.Context) extends InlineMacros with HygieneUtilMacros {
  import c.universe._

  def isSynchronous[Self <: Future[Ack] : c.WeakTypeTag]: c.Expr[Boolean] = {
    val selfExpr = sourceFromAck[Self](c.prefix.tree)
    val self = util.name("source")
    val ContinueSymbol = symbolOf[Continue].companion
    val StopSymbol = symbolOf[Stop].companion

    val tree =
      if (util.isClean(selfExpr))
        q"""($selfExpr eq $ContinueSymbol) || ($selfExpr eq $StopSymbol)"""
      else
        q"""
          val $self = $selfExpr
          ($self eq $ContinueSymbol) || ($self eq $StopSymbol)
          """

    inlineAndReset[Boolean](tree)
  }

  def syncOnContinue[Self <: Future[Ack] : c.WeakTypeTag](callback: Tree)(s: Tree): Tree = {
    val selfExpr = sourceFromAck[Self](c.prefix.tree)
    val self = util.name("source")
    val scheduler = c.Expr[Scheduler](s)

    val execute = c.Expr[Unit](callback)
    val ContinueSymbol = symbolOf[Continue].companion
    val StopSymbol = symbolOf[Stop].companion
    val AckSymbol = symbolOf[Ack]
    val FutureSymbol = symbolOf[Future[_]]

    val tree =
      q"""
        val $self = $selfExpr
        if ($self eq $ContinueSymbol)
          try { $execute } catch {
            case ex: Throwable =>
              if (_root_.monix.execution.misc.NonFatal(ex))
                $scheduler.reportFailure(ex)
              else
                throw ex
          }
        else if (($self : $FutureSymbol[$AckSymbol]) != $StopSymbol) {
          $self.onComplete { result =>
            if (result.isSuccess && (result.get eq $ContinueSymbol)) { $execute }
          }($scheduler)
        }

        $self
        """

    inlineAndResetTree(tree)
  }

  def syncOnStopOrFailure[Self <: Future[Ack] : c.WeakTypeTag](callback: Tree)(s: Tree): Tree = {
    val selfExpr = sourceFromAck[Self](c.prefix.tree)
    val self = util.name("source")
    val scheduler = c.Expr[Scheduler](s)

    val execute = c.Expr[Option[Throwable] => Unit](callback)
    val ContinueSymbol = symbolOf[Continue].companion
    val StopSymbol = symbolOf[Stop].companion
    val AckSymbol = symbolOf[Ack]
    val FutureSymbol = symbolOf[Future[_]]

    val tree =
      q"""
        val $self = $selfExpr
        if ($self eq $StopSymbol)
          try { $execute(_root_.scala.None) } catch {
            case ex: _root_.scala.Throwable =>
              if (_root_.monix.execution.misc.NonFatal(ex))
                $scheduler.reportFailure(ex)
              else
                throw ex
          }
        else if (($self : $FutureSymbol[$AckSymbol]) != $ContinueSymbol) {
          $self.onComplete { result =>
            if (result.isFailure) {
              $execute(_root_.scala.Some(result.failed.get))
            }
            else if (result.get eq $StopSymbol) {
              $execute(_root_.scala.None)
            }
          }($scheduler)
        }

        $self
        """

    inlineAndResetTree(tree)
  }

  def syncMap[Self <: Future[Ack] : c.WeakTypeTag](f: c.Expr[Ack => Ack])(s: c.Expr[Scheduler]): c.Expr[Future[Ack]] = {
    val selfExpr = sourceFromAck[Self](c.prefix.tree)
    val schedulerExpr = s
    val self = util.name("source")
    val fn = util.name("fn")

    val ContinueSymbol = symbolOf[Continue].companion
    val StopSymbol = symbolOf[Stop].companion
    val AckSymbol = symbolOf[Ack]

    val tree =
      if (util.isClean(f)) {
        q"""
          val $self = $selfExpr

          if (($self eq $ContinueSymbol) || ($self eq $StopSymbol)) {
            try {
              $f($self.asInstanceOf[$AckSymbol]) : $AckSymbol
            } catch {
              case ex: _root_.java.lang.Throwable =>
                if (_root_.monix.execution.misc.NonFatal(ex)) {
                  $schedulerExpr.reportFailure(ex)
                  $StopSymbol
                } else {
                  throw ex
                }
            }
          } else {
            $self.map($f)
          }
          """
      } else {
        q"""
          val $self = $selfExpr
          val $fn: _root_.scala.Function1[$AckSymbol,$AckSymbol] = $f

          if (($self eq $ContinueSymbol) || ($self eq $StopSymbol))
            try {
              $fn($self.asInstanceOf[$AckSymbol]) : $AckSymbol
            } catch {
              case ex: Throwable =>
                if (_root_.monix.execution.misc.NonFatal(ex)) {
                  $schedulerExpr.reportFailure(ex)
                  $StopSymbol
                } else {
                  throw ex
                }
            }
          else {
            $self.map($fn)
          }
          """
      }

    inlineAndReset[Future[Ack]](tree)
  }

  def syncFlatMap[Self <: Future[Ack] : c.WeakTypeTag](f: c.Expr[Ack => Future[Ack]])(s: c.Expr[Scheduler]): c.Expr[Future[Ack]] = {
    val selfExpr = sourceFromAck[Self](c.prefix.tree)
    val schedulerExpr = s
    val self = util.name("source")

    val ContinueSymbol = symbolOf[Continue].companion
    val StopSymbol = symbolOf[Stop].companion
    val AckSymbol = symbolOf[Ack]
    val FutureSymbol = symbolOf[Future[_]]

    val tree =
      if (util.isClean(f))
        q"""
          val $self = $selfExpr

          if (($self eq $ContinueSymbol) || ($self eq $StopSymbol))
            try {
              $f($self.asInstanceOf[$AckSymbol]) : $FutureSymbol[$AckSymbol]
            } catch {
              case ex: Throwable =>
                if (_root_.monix.execution.misc.NonFatal(ex)) {
                  $schedulerExpr.reportFailure(ex)
                  $StopSymbol
                } else {
                  throw ex
                }
            }
          else {
            $self.flatMap($f)
          }
          """
      else {
        val fn = util.name("fn")
        q"""
          val $self = $selfExpr
          val $fn: _root_.scala.Function1[$AckSymbol,$AckSymbol] = $f

          if (($self eq $ContinueSymbol) || ($self eq $StopSymbol))
            try {
              $fn($self.asInstanceOf[$AckSymbol]) : $FutureSymbol[$AckSymbol]
            } catch {
              case ex: Throwable =>
                if (_root_.monix.execution.misc.NonFatal(ex)) {
                  $schedulerExpr.reportFailure(ex)
                  $StopSymbol
                } else {
                  throw ex
                }
            }
          else {
            $self.flatMap($fn)
          }
          """
      }

    inlineAndReset[Future[Ack]](tree)
  }

  def syncOnComplete[Self <: Future[Ack] : c.WeakTypeTag](f: c.Expr[scala.util.Try[Ack] => Unit])
    (s: c.Expr[Scheduler]): c.Expr[Unit] = {

    val selfExpr = sourceFromAck[Self](c.prefix.tree)
    val schedulerExpr = s
    val self = util.name("source")

    val SuccessObject = symbolOf[scala.util.Success[_]].companion
    val ContinueObject = symbolOf[Continue].companion
    val StopObject = symbolOf[Stop].companion
    val AckSymbol = symbolOf[Ack]
    val TrySymbol = symbolOf[scala.util.Try[_]]
    val UnitSymbol = symbolOf[Unit]
    val FutureSymbol = symbolOf[Future[_]]

    val tree =
      if (util.isClean(f))
        q"""
          val $self: $FutureSymbol[$AckSymbol] = $selfExpr

          if (($self eq $ContinueObject) || ($self eq $StopObject))
            try {
              $f($SuccessObject($self.asInstanceOf[$AckSymbol]) : $TrySymbol[$AckSymbol])
              ()
            } catch {
              case ex: Throwable =>
                if (_root_.monix.execution.misc.NonFatal(ex)) {
                  $schedulerExpr.reportFailure(ex)
                } else {
                  throw ex
                }
            }
          else {
            $self.onComplete($f)
          }
          """
      else {
        val fn = util.name("fn")
        q"""
          val $self: $FutureSymbol[$AckSymbol]  = $selfExpr
          val $fn: _root_.scala.Function1[$TrySymbol[$AckSymbol],$UnitSymbol] = $f

          if (($self eq $ContinueObject) || ($self eq $StopObject))
            try {
              $fn($SuccessObject($self.asInstanceOf[$AckSymbol]))
              ()
            } catch {
              case ex: Throwable =>
                if (_root_.monix.execution.misc.NonFatal(ex)) {
                  $schedulerExpr.reportFailure(ex)
                } else {
                  throw ex
                }
            }
          else {
            $self.onComplete($fn)
          }
          """
      }

    inlineAndReset[Unit](tree)
  }

  def executeAsync(cb: Tree): Tree = {
    val selfExpr = sourceFromScheduler(c.prefix.tree)
    val RunnableSymbol = symbolOf[Runnable]

    resetTree(
      q"""
      ($selfExpr).execute(new $RunnableSymbol {
        def run(): Unit = { $cb }
      })
      """)
  }

  def executeTrampolined(cb: Tree): Tree = {
    val selfExpr = sourceFromScheduler(c.prefix.tree)
    val TrampolinedRunnableSymbol = symbolOf[TrampolinedRunnable]

    resetTree(
      q"""
      ($selfExpr).execute(new $TrampolinedRunnableSymbol {
        def run(): Unit = { $cb }
      })
      """)
  }

  def executeAsyncBatch(cb: Tree): Tree = {
    val self = util.name("scheduler")
    val runnable = util.name("runnable")
    val selfExpr = sourceFromScheduler(c.prefix.tree)
    val TrampolinedRunnableSymbol = symbolOf[TrampolinedRunnable]
    val StartAsyncBatchRunnableSymbol = symbolOf[StartAsyncBatchRunnable]

    resetTree(
      q"""
      val $self = ($selfExpr)
      val $runnable = new $TrampolinedRunnableSymbol { def run(): Unit = { $cb } }
      $self.execute(new $StartAsyncBatchRunnableSymbol($runnable, $self))
      """)
  }

  private[monix] def sourceFromScheduler(tree: Tree): c.Expr[Scheduler] = {
    val extensions = symbolOf[Scheduler.Extensions].name.toTermName

    tree match {
      case Apply(Select(_, `extensions`), List(expr)) =>
        c.Expr[Scheduler](expr)
      case _ =>
        c.warning(tree.pos, "Could not infer the implicit class source, please report a bug!")
        c.Expr[Scheduler](q"$tree.source")
    }
  }

  private[monix] def sourceFromAck[Source : c.WeakTypeTag](tree: Tree): c.Expr[Source] = {
    val ackExtensions = symbolOf[AckExtensions[_]].name.toTermName
    tree match {
      case Apply(TypeApply(Select(_, `ackExtensions`), _), List(expr)) =>
        c.Expr[Source](expr)
      case _ =>
        c.warning(tree.pos, "Could not infer the implicit class source, please report a bug!")
        c.Expr[Source](q"$tree.source")
    }
  }
}