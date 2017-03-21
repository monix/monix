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

object IterantFoldWhileSuite extends BaseTestSuite {
  test("Iterant.foldWhileL consistent with Iterant.foldLeftL") { implicit s =>
    check3 { (iter: Iterant[Int], seed: Long, f: (Long, Int) => Long) =>
      val received = iter.foldWhileL(seed)((s, e) => Left(f(s, e)))
      val expected = iter.foldLeftL(seed)(f)
      received === expected
    }
  }
  
  test("Iterant.findL is equivalent with list.find") { implicit s =>
    check2 { (iter: Iterant[Int], p: Int => Boolean) =>
      val received = iter.findL(p)
      val expected = iter.toListL[Int].map(_.find(p))
      received === expected
    }
  }

  test("Iterant.findL protects against user error") { implicit s =>
    check1 { (prefix: Iterant[Int]) =>
      var effect = 0
      val iter = (prefix ++ Iterant.fromList(List(1,2)))
        .doOnEarlyStop(Task.eval(effect += 1))

      val dummy = DummyException("dummy")
      val t = iter.findL { e => throw dummy }
      val f = t.runAsync; s.tick()

      f.value == Some(Failure(dummy)) && effect == 1
    }
  }

  test("Iterant.existsL is equivalent with list.exists") { implicit s =>
    check2 { (iter: Iterant[Int], p: Int => Boolean) =>
      val received = iter.existsL(p)
      val expected = iter.toListL[Int].map(_.exists(p))
      received === expected
    }
  }

  test("Iterant.existsL protects against user error") { implicit s =>
    check1 { (prefix: Iterant[Int]) =>
      var effect = 0
      val iter = (prefix ++ Iterant.fromList(List(1,2)))
        .doOnEarlyStop(Task.eval(effect += 1))

      val dummy = DummyException("dummy")
      val t = iter.existsL { e => throw dummy }
      val f = t.runAsync; s.tick()

      f.value == Some(Failure(dummy)) && effect == 1
    }
  }

  test("Iterant.forallL is equivalent with list.forall") { implicit s =>
    check2 { (iter: Iterant[Int], p: Int => Boolean) =>
      val received = iter.forallL(p)
      val expected = iter.toListL[Int].map(_.forall(p))
      received === expected
    }
  }

  test("Iterant.forallL protects against user error") { implicit s =>
    check1 { (prefix: Iterant[Int]) =>
      var effect = 0
      val iter = (prefix ++ Iterant.fromList(List(1,2)))
        .doOnEarlyStop(Task.eval(effect += 1))

      val dummy = DummyException("dummy")
      val t = iter.forallL { e => throw dummy }
      val f = t.runAsync; s.tick()

      f.value == Some(Failure(dummy)) && effect == 1
    }
  }

  test("Iterant.isEmptyL is equivalent with list.isEmpty") { implicit s =>
    check1 { (iter: Iterant[Int]) =>
      val received = iter.isEmptyL
      val expected = iter.toListL[Int].map(_.isEmpty)
      received === expected
    }
  }

  test("Iterant.nonEmptyL is equivalent with list.nonEmpty") { implicit s =>
    check1 { (iter: Iterant[Int]) =>
      val received = iter.nonEmptyL
      val expected = iter.toListL[Int].map(_.nonEmpty)
      received === expected
    }
  }
}
