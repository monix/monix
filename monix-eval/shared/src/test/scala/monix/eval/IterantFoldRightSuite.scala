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

import monix.execution.exceptions.DummyException

import scala.util.Failure

object IterantFoldRightSuite extends BaseTestSuite {
  test("Iterant.foldRightL can express Iterant.exist") { implicit s =>
    def exists(iter: Iterant[Int])(f: Int => Boolean): Task[Boolean] =
      iter.foldRightL(Task.now(false)) { (elem, lb) =>
        if (f(elem)) Task.now(true) else lb
      }

    check2 { (iter: Iterant[Int], p: Int => Boolean) =>
      exists(iter)(p) === iter.toListL[Int].map(_.exists(p))
    }
  }

  test("Iterant.foldRightL can express Iterant.forAll") { implicit s =>
    def forAll(iter: Iterant[Int])(f: Int => Boolean): Task[Boolean] =
      iter.foldRightL(Task.now(true)) { (elem, lb) =>
        if (!f(elem)) Task.now(false) else lb
      }

    check2 { (iter: Iterant[Int], p: Int => Boolean) =>
      forAll(iter)(p) === iter.toListL[Int].map(_.forall(p))
    }
  }

  test("Iterant.foldRightL protects against user error") { implicit s =>
    check1 { (prefix: Iterant[Int]) =>
      var effect = 0
      val iter = (prefix ++ Iterant.fromList(List(1,2)))
        .doOnEarlyStop(Task.eval(effect += 1))

      val dummy = DummyException("dummy")
      val t = iter.foldRightL[Boolean](Task.now(false)) { (e, lb) => throw dummy }
      val f = t.runAsync; s.tick()

      f.value.contains(Failure(dummy)) && effect == 1
    }
  }

  test("Iterant.foldRightL protects against async user error") { implicit s =>
    check1 { (prefix: Iterant[Int]) =>
      var effect = 0
      val iter = (prefix ++ Iterant.fromList(List(1,2)))
        .doOnEarlyStop(Task.eval(effect += 1))

      val dummy = DummyException("dummy")
      val t = iter.foldRightL[Boolean](Task.now(false)) { (e, lb) => Task.raiseError(dummy) }
      val f = t.runAsync; s.tick()

      f.value.contains(Failure(dummy)) && effect == 1
    }
  }
}
