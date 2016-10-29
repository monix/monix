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

package monix.types.utils

import scala.reflect.macros.whitebox

/** Macro implementations for the [[monix.types.syntax syntax]]
  * exposed for the various type-classes, used in order to
  * reduce overhead.
  */
@macrocompat.bundle class Macros(val c: whitebox.Context) {
  import c.universe._

  /** Implementation for [[monix.types.Functor.map Functor.map]] */
  def functorMap(f: Tree): Tree =
    reset(getImplicitCallParams("Functor.Syntax", "functorOps", c.prefix.tree) match {
      case Some((valueExpr, functorExpr)) =>
        q"($functorExpr).map($valueExpr)(x => ($f)(x))"
      case None =>
        val prefix = c.prefix.tree
        val ops = TermName(c.freshName("ops$"))
        q"""
        val $ops = $prefix
        $ops.F.map(($ops).self)(x => $f(x))
        """
    })

  /** Implementation for
    * [[monix.types.Applicative.ap Applicative.ap]]
    */
  def applicativeAP(ff: Tree): Tree =
    reset(getImplicitCallParams("Applicative.Syntax", "applicativeOps", c.prefix.tree) match {
      case Some((valueExpr, monadExpr)) =>
        q"($monadExpr).ap($valueExpr)($ff)"
      case None =>
        val prefix = c.prefix.tree
        val ops = TermName(c.freshName("ops$"))
        q"""
        val $ops = $prefix
        $ops.F.ap(($ops).self)($ff)
        """
    })

  /** Implementation for
    * [[monix.types.Monad.flatMap Monad.flatMap]]
    */
  def monadFlatMap(f: Tree): Tree =
    reset(getImplicitCallParams("Monad.Syntax", "monadOps", c.prefix.tree) match {
      case Some((valueExpr, monadExpr)) =>
        q"($monadExpr).flatMap($valueExpr)(x => ($f)(x))"
      case None =>
        val prefix = c.prefix.tree
        val ops = TermName(c.freshName("ops$"))
        q"""
        val $ops = $prefix
        $ops.F.flatMap(($ops).self)(x => $f(x))
        """
    })

  /** Implementation for [
    * [monix.types.Monad.flatten Monad.flatten]]
    */
  def monadFlatten(ev: Tree): Tree =
    reset(getImplicitCallParams("Monad.Syntax", "monadOps", c.prefix.tree) match {
      case Some((valueExpr, monadExpr)) =>
        q"($monadExpr).flatten($valueExpr)"
      case None =>
        val prefix = c.prefix.tree
        val ops = TermName(c.freshName("ops$"))
        q"""
        val $ops = $prefix
        $ops.F.flatten(($ops).self)
        """
    })

  /** Implementation for
    * [[monix.types.MonadError.onErrorHandleWith MonadError.onErrorHandleWith]]
    */
  def monadErrorHandleWith(f: Tree): Tree =
    reset(getImplicitCallParams("MonadError.Syntax", "monadErrorOps", c.prefix.tree) match {
      case Some((valueExpr, monadErrorExpr)) =>
        q"($monadErrorExpr).onErrorHandleWith($valueExpr)(ex => ($f)(ex))"
      case None =>
        val prefix = c.prefix.tree
        val ops = TermName(c.freshName("ops$"))
        q"""
        val $ops = $prefix
        $ops.F.onErrorHandleWith(($ops).self)(ex => ($f)(ex))
        """
    })

  /** Implementation for
    * [[monix.types.MonadError.onErrorHandle MonadError.onErrorHandle]]
    */
  def monadErrorHandle(f: Tree): Tree =
    reset(getImplicitCallParams("MonadError.Syntax", "monadErrorOps", c.prefix.tree) match {
      case Some((valueExpr, monadErrorExpr)) =>
        q"($monadErrorExpr).onErrorHandle($valueExpr)(ex => ($f)(ex))"
      case None =>
        val prefix = c.prefix.tree
        val ops = TermName(c.freshName("ops$"))
        q"""
        val $ops = $prefix
        $ops.F.onErrorHandle(($ops).self)(ex => ($f)(ex))
        """
    })

  /** Implementation for
    * [[monix.types.MonadError.onErrorRecoverWith MonadError.onErrorRecoverWith]]
    */
  def monadErrorRecoverWith(pf: Tree): Tree =
    reset(getImplicitCallParams("MonadError.Syntax", "monadErrorOps", c.prefix.tree) match {
      case Some((valueExpr, monadErrorExpr)) =>
        q"($monadErrorExpr).onErrorRecoverWith($valueExpr)(ex => ($pf)(ex))"
      case None =>
        val prefix = c.prefix.tree
        val ops = TermName(c.freshName("ops$"))
        q"""
        val $ops = $prefix
        $ops.F.onErrorRecoverWith(($ops).self)(ex => ($pf)(ex))
        """
    })

  /** Implementation for
    * [[monix.types.MonadError.onErrorRecover MonadError.onErrorRecover]]
    */
  def monadErrorRecover(pf: Tree): Tree =
    reset(getImplicitCallParams("MonadError.Syntax", "monadErrorOps", c.prefix.tree) match {
      case Some((valueExpr, monadErrorExpr)) =>
        q"($monadErrorExpr).onErrorRecover($valueExpr)(ex => ($pf)(ex))"
      case None =>
        val prefix = c.prefix.tree
        val ops = TermName(c.freshName("ops$"))
        q"""
        val $ops = $prefix
        $ops.F.onErrorRecover(($ops).self)(ex => ($pf)(ex))
        """
    })

  /** Implementation for
    * [[monix.types.MonadFilter.filter MonadFilter.filter]]
    */
  def monadFilter(f: Tree): Tree =
    reset(getImplicitCallParams("MonadFilter.Syntax", "monadFilterOps", c.prefix.tree) match {
      case Some((valueExpr, monadFilterExpr)) =>
        q"($monadFilterExpr).filter($valueExpr)(x => ($f)(x))"
      case None =>
        val prefix = c.prefix.tree
        val ops = TermName(c.freshName("ops$"))
        q"""
          val $ops = $prefix
          $ops.F.filter(($ops).self)(x => $f(x))
          """
    })

  private def getImplicitCallParams(debugPath: String, implicitFName: String, tree: Tree): Option[(Tree, Tree)] =
    tree match {
      case Apply(Apply(TypeApply(Select(_, TermName(`implicitFName`)), _), List(valueExpr)), List(functorExpr)) =>
        Some((valueExpr, functorExpr))
      case _ =>
        c.warning(tree.pos,
          s"Could not interpret the $debugPath.$implicitFName call in macro, " +
          s"use $debugPath instead of the Ops class directly!")
        None
    }

  private def reset(tree: Tree): Tree = {
    // Workaround for https://issues.scala-lang.org/browse/SI-5465
    class StripUnApplyNodes extends Transformer {
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

    val clean = c.untypecheck(tree)
    new StripUnApplyNodes().transform(clean)
  }
}