/*
 * Copyright (c) 2014-2022 Monix Contributors.
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

package monix.execution.atomic.internal

import scala.reflect.macros.whitebox

/** Represents a boxed value, to be used in the testing
  * of [[InlineMacros]].
  */
private[atomic] final case class TestBox[A](value: A) {
  def map[B](f: A => B): TestBox[B] = macro TestBox.Macros.mapMacroImpl[A, B]
  def collect[B](f: PartialFunction[A, B]): TestBox[B] = macro TestBox.Macros.mapMacroImpl[A, B]
}

/** Represents a boxed value, to be used in the testing
  * of [[InlineMacros]].
  */
private[atomic] object TestBox {

  class Macros(override val c: whitebox.Context) extends InlineMacros with HygieneUtilMacros {
    import c.universe._

    def mapMacroImpl[A, B](f: c.Expr[A => B]): c.Expr[TestBox[B]] = {
      val selfExpr = c.Expr[TestBox[A]](c.prefix.tree)
      val tree =
        if (util.isClean(f)) {
          q"""
          TestBox($f($selfExpr.value))
          """
        } else {
          val fn = util.name("fn")
          q"""
          val $fn = $f
          TestBox($fn($selfExpr.value))
          """
        }
      inlineAndReset[TestBox[B]](tree)
    }
  }
}
