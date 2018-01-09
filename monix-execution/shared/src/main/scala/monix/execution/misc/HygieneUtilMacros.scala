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

import scala.reflect.macros.whitebox
import monix.execution.misc.compat.freshTermName

/** Utilities for macro-hygiene. */
trait HygieneUtilMacros {
  val c: whitebox.Context

  import c.universe._

  object util {
    /** Generates a new term name. Used for macro-hygiene. */
    def name(s: String) = freshTermName(c)(s + "$")
    /** Generates new term names. Used for macro-hygiene. */
    def names(bs: String*) = bs.toList.map(name)

    /** Returns true if the given expressions are either
      * stable symbols or clean functions, false otherwise.
      */
    def isClean(es: c.Expr[_]*): Boolean =
      es.forall {
        _.tree match {
          case t @ Ident(_: TermName) if t.symbol.asTerm.isStable => true
          case Function(_, _) => true
          case _ => false
        }
      }
  }
}
