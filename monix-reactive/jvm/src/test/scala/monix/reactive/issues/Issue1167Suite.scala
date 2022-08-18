/*
 * Copyright (c) 2014-2021 by The Monix Project Developers.
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

package monix.reactive.issues

import monix.eval.Task
import monix.execution.Scheduler
import scala.concurrent.duration._
import monix.reactive.{ BaseTestSuite, Observable }

object Issue1167Suite extends BaseTestSuite {

  private def testIssue1167(o: Observable[Any]): Unit = {
    implicit val s: Scheduler = Scheduler.global

    val received =
      Task.sequence(List.fill(500)(o.completedL)).void.timeout(5.second).attempt.runSyncUnsafe()

    assertEquals(received, Right(()))
  }

  test("zip2 of different sizes should terminate [issue #1167]") { _ =>
    val obs1 = Observable(1, 2, 3)
    val obs2 = Observable(1, 2, 3, 4)

    testIssue1167(Observable.zip2(obs1, obs2))
  }

  test("zip3 of different sizes should terminate [issue #1167]") { _ =>
    val obs1 = Observable(1, 2, 3)
    val obs2 = Observable(1, 2, 3, 4)
    val obs3 = Observable(1, 2, 3, 4)

    testIssue1167(Observable.zip3(obs1, obs2, obs3))
  }

  test("zip4 of different sizes should terminate [issue #1167]") { _ =>
    val obs1 = Observable(1, 2, 3)
    val obs2 = Observable(1, 2, 3, 4)
    val obs3 = Observable(1, 2, 3, 4)
    val obs4 = Observable(1, 2, 3, 4)

    testIssue1167(Observable.zip4(obs1, obs2, obs3, obs4))
  }

  test("zip5 of different sizes should terminate [issue #1167]") { _ =>
    val obs1 = Observable(1, 2, 3)
    val obs2 = Observable(1, 2, 3, 4)
    val obs3 = Observable(1, 2, 3, 4)
    val obs4 = Observable(1, 2, 3, 4)
    val obs5 = Observable(1, 2, 3, 4)

    testIssue1167(Observable.zip5(obs1, obs2, obs3, obs4, obs5))
  }

  test("zip6 of different sizes should terminate [issue #1167]") { _ =>
    val obs1 = Observable(1, 2, 3)
    val obs2 = Observable(1, 2, 3, 4)
    val obs3 = Observable(1, 2, 3, 4)
    val obs4 = Observable(1, 2, 3, 4)
    val obs5 = Observable(1, 2, 3, 4)
    val obs6 = Observable(1, 2, 3, 4)

    testIssue1167(Observable.zip6(obs1, obs2, obs3, obs4, obs5, obs6))
  }

  test("zipList of different sizes should terminate [issue #1167]") { _ =>
    val obs1 = Observable(1, 2, 3)
    val obs2 = Observable(1, 2, 3, 4)
    val obs3 = Observable(1, 2, 3, 4)
    val obs4 = Observable(1, 2, 3, 4)

    testIssue1167(Observable.zipList(obs1, obs2, obs3, obs4))
  }

  test("zip2 + prepend should work properly [issue #1164]") { _ =>
    implicit val s: Scheduler = Scheduler.global

    val obs1 = 0 +: Observable(1, 2, 3)
    val obs2 = Observable(0, 1, 2, 3)

    val received: List[(Int, Int)] = obs1.zip(obs2).toListL.runSyncUnsafe()

    assertEquals(received, List((0, 0), (1, 1), (2, 2), (3, 3)))
  }

  test("zip3 + prepend should work properly [issue #1164]") { _ =>
    implicit val s: Scheduler = Scheduler.global

    val obs1 = 0 +: Observable(1, 2, 3)
    val obs2 = Observable(0, 1, 2, 3)
    val obs3 = Observable(0, 1, 2, 3)

    val received: List[(Int, Int, Int)] = Observable.zip3(obs1, obs2, obs3).toListL.runSyncUnsafe()

    assertEquals(received, List((0, 0, 0), (1, 1, 1), (2, 2, 2), (3, 3, 3)))
  }

  test("zip4 + prepend should work properly [issue #1164]") { _ =>
    implicit val s: Scheduler = Scheduler.global

    val obs1 = 0 +: Observable(1, 2, 3)
    val obs2 = Observable(0, 1, 2, 3)
    val obs3 = Observable(0, 1, 2, 3)
    val obs4 = Observable(0, 1, 2, 3)

    val received: List[(Int, Int, Int, Int)] =
      Observable.zip4(obs1, obs2, obs3, obs4).toListL.runSyncUnsafe()

    assertEquals(received, List((0, 0, 0, 0), (1, 1, 1, 1), (2, 2, 2, 2), (3, 3, 3, 3)))
  }

  test("zip5 + prepend should work properly [issue #1164]") { _ =>
    implicit val s: Scheduler = Scheduler.global

    val obs1 = 0 +: Observable(1, 2, 3)
    val obs2 = Observable(0, 1, 2, 3)
    val obs3 = Observable(0, 1, 2, 3)
    val obs4 = Observable(0, 1, 2, 3)
    val obs5 = Observable(0, 1, 2, 3)

    val received: List[(Int, Int, Int, Int, Int)] =
      Observable.zip5(obs1, obs2, obs3, obs4, obs5).toListL.runSyncUnsafe()

    assertEquals(received, List((0, 0, 0, 0, 0), (1, 1, 1, 1, 1), (2, 2, 2, 2, 2), (3, 3, 3, 3, 3)))
  }

  test("zip6 + prepend should work properly [issue #1164]") { _ =>
    implicit val s: Scheduler = Scheduler.global

    val obs1 = 0 +: Observable(1, 2, 3)
    val obs2 = Observable(0, 1, 2, 3)
    val obs3 = Observable(0, 1, 2, 3)
    val obs4 = Observable(0, 1, 2, 3)
    val obs5 = Observable(0, 1, 2, 3)
    val obs6 = Observable(0, 1, 2, 3)

    val received: List[(Int, Int, Int, Int, Int, Int)] =
      Observable.zip6(obs1, obs2, obs3, obs4, obs5, obs6).toListL.runSyncUnsafe()

    assertEquals(received, List((0, 0, 0, 0, 0, 0), (1, 1, 1, 1, 1, 1), (2, 2, 2, 2, 2, 2), (3, 3, 3, 3, 3, 3)))
  }

  test("zipList + prepend should work properly [issue #1164]") { _ =>
    implicit val s: Scheduler = Scheduler.global

    val obs1 = 0 +: Observable(1, 2, 3)
    val obs2 = Observable(0, 1, 2, 3)
    val obs3 = Observable(0, 1, 2, 3)
    val obs4 = Observable(0, 1, 2, 3)
    val obs5 = Observable(0, 1, 2, 3)
    val obs6 = Observable(0, 1, 2, 3)

    val received: List[Seq[Int]] =
      Observable.zipList(obs1, obs2, obs3, obs4, obs5, obs6).toListL.runSyncUnsafe()

    assertEquals(
      received,
      List(List(0, 0, 0, 0, 0, 0), List(1, 1, 1, 1, 1, 1), List(2, 2, 2, 2, 2, 2), List(3, 3, 3, 3, 3, 3))
    )
  }
}
