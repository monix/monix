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

package monix.types
package syntax

import language.implicitConversions
import cats.Unapply

trait MonadConsUnapplySyntax {
  implicit def monadConsSyntaxU[FA](fa: FA)(implicit U: Unapply[MonadCons,FA]): MonadCons.Ops[U.M, U.A] =
    new MonadCons.Ops[U.M, U.A] {
      val self = U.subst(fa)
      val typeClassInstance = U.TC
    }
}

trait MonadConsSyntax extends MonadCons.ToMonadConsOps
  with MonadConsUnapplySyntax
