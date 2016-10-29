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

package monix.eval

import monix.types.tests._

object TypeClassLawsForCoevalSuite extends BaseLawsSuite
  with MemoizableLawsSuite[Coeval,Int,Long,Short]
  with SuspendableLawsSuite[Coeval,Int,Long,Short]
  with MonadErrorLawsSuite[Coeval,Int,Long,Short,Throwable]
  with CobindLawsSuite[Coeval,Int,Long,Short]
  with MonadRecLawsSuite[Coeval,Int,Long,Short]
  with ComonadLawsSuite[Coeval,Int,Long,Short] {

  override def F: Coeval.TypeClassInstances =
    Coeval.typeClassInstances

  // Actual tests ...

  monadEvalErrorCheck("Coeval")
  memoizableCheck("Coeval", includeSupertypes = true)
  monadErrorCheck("Coeval", includeSupertypes = false)
  monadRecCheck("Coeval", includeSupertypes = false)
  cobindCheck("Coeval", includeSupertypes = false)
  comonadCheck("Coeval", includeSupertypes = false)
}
