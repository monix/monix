/*
 * Copyright (c) 2014-2017 by The Monix Project Developers.
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

package monix.execution.schedulers

import monix.execution.Scheduler
import monix.execution.misc.{HygieneUtilMacros, InlineMacros}
import scala.reflect.macros.whitebox

/** Macros enabling extension methods for [[Scheduler]] meant for
  * executing runnables.
  *
  * See:
  *
  *  - [[Scheduler.Extensions.executeAsync]]
  *  - [[Scheduler.Extensions.executeAsyncBatch]]
  *  - [[Scheduler.Extensions.executeTrampolined]]
  *
  *  NOTE: these macros are only defined for Scala < 2.12, because in
  *  Scala 2.12 we simply rely on its SAM support.
  */
final class ExecuteMacros(override val c: whitebox.Context)
  extends InlineMacros with HygieneUtilMacros {

  import c.universe._

  def executeAsync(cb: Tree): Tree = {
    val selfExpr = sourceFromScheduler(c.prefix.tree)
    val RunnableSymbol = symbolOf[Runnable]

    inlineAndResetTree(
      q"""
      ($selfExpr).execute(new $RunnableSymbol {
        def run(): Unit = { $cb() }
      })
      """)
  }

  def executeTrampolined(cb: Tree): Tree = {
    val selfExpr = sourceFromScheduler(c.prefix.tree)
    val TrampolinedRunnableSymbol = symbolOf[TrampolinedRunnable]

    inlineAndResetTree(
      q"""
      ($selfExpr).execute(new $TrampolinedRunnableSymbol {
        def run(): Unit = { $cb() }
      })
      """)
  }

  def executeAsyncBatch(cb: Tree): Tree = {
    val self = util.name("scheduler")
    val runnable = util.name("runnable")
    val selfExpr = sourceFromScheduler(c.prefix.tree)
    val TrampolinedRunnableSymbol = symbolOf[TrampolinedRunnable]
    val StartAsyncBatchRunnableSymbol = symbolOf[StartAsyncBatchRunnable]

    inlineAndResetTree(
      q"""
      val $self = ($selfExpr)
      val $runnable = new $TrampolinedRunnableSymbol { def run(): Unit = { $cb() } }
      $self.execute(new $StartAsyncBatchRunnableSymbol($runnable, $self))
      """)
  }

  private def sourceFromScheduler(tree: Tree): c.Expr[Scheduler] = {
    val extensions = symbolOf[Scheduler.Extensions].name.toTermName

    tree match {
      case Apply(Select(_, `extensions`), List(expr)) =>
        c.Expr[Scheduler](expr)
      case _ =>
        c.warning(tree.pos, "Could not infer the implicit class source, please report a bug!")
        c.Expr[Scheduler](q"$tree.source")
    }
  }
}
