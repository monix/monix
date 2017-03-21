/*
 * Copyright (c) 2014-2017 by its authors. Some rights reserved.
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

import monix.types.tests.{MonadFilterLawsSuite, MonadRecLawsSuite, MonoidKLawsSuite}

object TypeClassLawsForIterantSuite extends BaseLawsSuite
  with MonadRecLawsSuite[Iterant, Int, Long, Short]
  with MonadFilterLawsSuite[Iterant, Int, Long, Short]
  with MonoidKLawsSuite[Iterant, Int] {

  override val F = Iterant.typeClassInstances
  override lazy val checkConfig = slowConfig

  // Actual tests ...
  monadCheck("Iterant[A]", includeSupertypes = true)
  monadRecCheck("Iterant[A]", includeSupertypes = false, stackSafetyCount = 10000)
  monadFilterCheck("Iterant[A]", includeSupertypes = false)
  monoidKCheck("Iterant[A]", includeSupertypes = true)
}
