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

package monix.eval

import cats.Applicative
import cats.laws.discipline.ApplicativeTests
import monix.eval.instances.{CatsParallelForTask, ParallelApplicative}

object TypeClassLawsForParallelApplicativeSuite extends BaseLawsSuite {
  implicit val ap: Applicative[Task] =
    new ParallelApplicative()(new CatsParallelForTask)

  test("instance is valid") {
    val ev = implicitly[Applicative[Task]]
    assertEquals(ev, ap)
  }

  test("default instance for Task") {
    val ev = ParallelApplicative[Task, Task]
    assertEquals(ev, CatsParallelForTask.applicative)
  }

  checkAllAsync("ParallelApplicative[Task]") { implicit ec =>
    ApplicativeTests[Task].applicative[Int,Int,Int]
  }
}
