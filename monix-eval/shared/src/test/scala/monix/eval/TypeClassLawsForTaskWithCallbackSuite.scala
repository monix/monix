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

import cats.effect.laws.discipline.{AsyncTests, EffectTests}
import cats.kernel.laws.discipline.MonoidTests
import cats.laws.discipline.{ApplicativeTests, CoflatMapTests, ParallelTests}
import cats.{Applicative, Eq}
import monix.eval.instances.CatsParallelForTask
import monix.execution.schedulers.TestScheduler

import scala.concurrent.Promise

/** Type class tests for Task that use an alternative `Eq`, making
  * use of Task's `runAsync(callback)`.
  */
object TypeClassLawsForTaskWithCallbackSuite extends BaseLawsSuite {
  implicit val ap: Applicative[Task.Par] = CatsParallelForTask.applicative

  override implicit def equalityTask[A](implicit A: Eq[A], ec: TestScheduler) =
    Eq.by { task =>
      val p = Promise[A]()
      task.runOnComplete(r => p.complete(r))
      p.future
    }

  override implicit def equalityTaskPar[A](implicit A: Eq[A], ec: TestScheduler): Eq[Task.Par[A]] = {
    import Task.Par.unwrap
    Eq.by { task =>
      val p = Promise[A]()
      unwrap(task).runOnComplete(r => p.complete(r))
      p.future
    }
  }

  checkAllAsync("CoflatMap[Task]") { implicit ec =>
    CoflatMapTests[Task].coflatMap[Int,Int,Int]
  }

  checkAllAsync("Async[Task]") { implicit ec =>
    AsyncTests[Task].async[Int,Int,Int]
  }

  checkAllAsync("Effect[Task]") { implicit ec =>
    EffectTests[Task].effect[Int,Int,Int]
  }

  checkAllAsync("Applicative[Task.Par]") { implicit ec =>
    ApplicativeTests[Task.Par].applicative[Int, Int, Int]
  }

  checkAllAsync("Parallel[Task, Task]") { implicit ec =>
    ParallelTests[Task, Task.Par].parallel[Int, Int]
  }

  checkAllAsync("Monoid[Task[Int]]") { implicit ec =>
    MonoidTests[Task[Int]].monoid
  }
}