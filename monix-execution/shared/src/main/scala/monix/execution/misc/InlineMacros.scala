/*
 * Copyright (c) 2014-2018 by The Monix Project Developers.
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

package monix.execution.misc

import monix.execution.misc.compat.setOrig
import scala.reflect.macros.whitebox

trait InlineMacros {
  val c: whitebox.Context

  import c.universe._

  def inlineAndReset[A](tree: Tree): c.Expr[A] = {
    c.Expr[A](inlineAndResetTree(tree))
  }

  def inlineAndResetTree(tree: Tree): Tree = {
    val inlined = inlineApplyRecursive(tree)
    val clean = c.untypecheck(inlined)
    stripUnApplyNodes().transform(clean)
  }

  def resetTree(tree: Tree): Tree = {
    val clean = c.untypecheck(tree)
    stripUnApplyNodes().transform(clean)
  }

  def inlineApplyRecursive(tree: Tree): Tree = {
    val ApplyName = TermName("apply")

    class InlineSymbol(symbol: TermName, value: Tree) extends Transformer {
      override def transform(tree: Tree): Tree = tree match {
        case i@Ident(_) if i.name == symbol =>
          value
        case tt: TypeTree if tt.original != null =>
          //super.transform(TypeTree().setOriginal(transform(tt.original)))
          super.transform(setOrig(c)(TypeTree(), transform(tt.original)))
        case _ =>
          super.transform(tree)
      }
    }

    object InlineApply extends Transformer {
      def inlineSymbol(symbol: TermName, body: Tree, arg: Tree): Tree =
        new InlineSymbol(symbol, arg).transform(body)

      override def transform(tree: Tree): Tree = tree match {
        case Apply(Select(Function(params, body), ApplyName), args) =>
          params.zip(args).foldLeft(body) { case (b, (param, arg)) =>
            inlineSymbol(param.name, b, arg)
          }

        case Apply(Function(params, body), args) =>
          params.zip(args).foldLeft(body) { case (b, (param, arg)) =>
            inlineSymbol(param.name, b, arg)
          }

        case _ =>
          super.transform(tree)
      }
    }

    InlineApply.transform(tree)
  }

  /** Creates a macro transformer than gets rid of implicit unapply
    * in case statements.
    *
    * Workaround for:
    * [[https://issues.scala-lang.org/browse/SI-5465]]
    */
  def stripUnApplyNodes(): Transformer =
    new Transformer {
      val global = c.universe.asInstanceOf[scala.tools.nsc.Global]
      import global.nme

      override def transform(tree: Tree): Tree = {
        super.transform {
          tree match {
            case UnApply(Apply(Select(qualifier, nme.unapply | nme.unapplySeq), List(Ident(nme.SELECTOR_DUMMY))), args) =>
              Apply(transform(qualifier), transformTrees(args))
            case UnApply(Apply(TypeApply(Select(qualifier, nme.unapply | nme.unapplySeq), _), List(Ident(nme.SELECTOR_DUMMY))), args) =>
              Apply(transform(qualifier), transformTrees(args))
            case t => t
          }
        }
      }
    }
}
