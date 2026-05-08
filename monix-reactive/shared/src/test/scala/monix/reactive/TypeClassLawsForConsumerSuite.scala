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

package monix.reactive

import cats.laws.discipline.{ ContravariantTests, ProfunctorTests }

class TypeClassLawsForConsumerSuite extends BaseLawsTestSuite {

  checkAllAsync("Contravariant[Consumer]") { implicit ec =>
    ContravariantTests[Consumer[*, Int]].contravariant[Int, Int, Int]
  }

  checkAllAsync("Profunctor[Consumer]") { implicit ec =>
    ProfunctorTests[Consumer].profunctor[Int, Int, Int, Int, Int, Int]
  }
}
