/*
 * Copyright (c) 2014-2017 by The Monix Project Developers.
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

package monix.scalaz.tests

import minitest.SimpleTestSuite
import monix.eval.Coeval
import monix.execution.schedulers.TestScheduler
import monix.reactive.Observable
import monix.scalaz._

import scala.util.Success
import scalaz._
import scalaz.Scalaz._

object MonixToScalazSuite extends SimpleTestSuite {
  test("monix to scalaz semigroup") {
    def apply[F[_]](fa: F[Int], fb: F[Int])(implicit F: Semigroup[F[Int]]): F[Int] =
      F.append(fa, fb)

    val fa = apply(Coeval(10), Coeval(20))
    assertEquals(fa.value, 30)
  }

  test("monix to scalaz monoid") {
    def apply[F[_]](fa: F[Int], fb: F[Int])(implicit F: Monoid[F[Int]]): F[Int] =
      F.append(F.append(fa, fb), F.zero)

    val fa = apply(Coeval(10), Coeval(20))
    assertEquals(fa.value, 30)
  }


  test("monix to scalaz functor") {
    def apply[F[_]](fa: F[Int])(implicit F: Functor[F]): F[Int] =
      F.map(fa)(_ + 1)

    val fa = apply(Coeval(1))
    assertEquals(fa.value, 2)
  }

  test("monix to scalaz applicative") {
    def apply[F[_]](fa: F[Int])(implicit F: Applicative[F]): F[Int] = {
      val ff = F.pure((a: Int) => a + 10)
      val fa2 = F.ap(fa)(ff)
      val fa3 = F.apply2(fa, fa2)((a,b) => a + b)
      F.map(fa3)(_ + 1)
    }

    val fa = apply(Coeval(10))
    assertEquals(fa.value, 31)
  }

  test("monix to scalaz monad") {
    def apply[F[_]](fa: F[Int])(implicit F: Monad[F]): F[Int] = {
      val ff = F.pure((a: Int) => a + 10)
      val fa2 = F.ap(fa)(ff)
      val fa3 = F.apply2(fa, fa2)((a,b) => a + b)
      val fa4 = F.map(fa3)(_ + 1)
      F.bind(fa4)(a => F.pure(a + 10))
    }

    val fa = apply(Coeval(10))
    assertEquals(fa.value, 41)
  }

  test("monix to scalaz monad-error") {
    def apply[F[_]](fa: F[Int])(implicit F: MonadError[F,Throwable]): F[Int] = {
      val ff = F.pure((a: Int) => a + 10)
      val fa2 = F.ap(fa)(ff)
      val fa3 = F.apply2(fa, fa2)((a,b) => a + b)
      val fa4 = F.map(fa3)(_ + 1)

      val dummy = new RuntimeException("dummy")
      val fb = F.handleError(F.raiseError[Int](dummy))(ex => F.pure(10))
      F.bind(fa4)(a4 => F.map(fb)(b => a4 + b))
    }

    val fa = apply(Coeval(10))
    assertEquals(fa.value, 41)
  }

  test("monix to scalaz cobind") {
    def apply[F[_]](fa: F[Int])(implicit F: Cobind[F]): F[Int] = {
      val fa2 = F.map(fa)(_ + 1)
      F.cobind(fa2)(_ => 30)
    }

    val fa = apply(Coeval(10))
    assertEquals(fa.value, 30)
  }

  test("monix to scalaz comonad") {
    def apply[F[_]](fa: F[Int])(implicit F: Comonad[F]): F[Int] = {
      val fa2 = F.map(fa)(_ + 1)
      F.cobind(fa2)(fa => F.copoint(fa) + 10)
    }

    val fa = apply(Coeval(10))
    assertEquals(fa.value, 21)
  }

  test("monix to scalaz plus") {
    def apply[F[_]](fa: F[Int], fb: F[Int])(implicit F: Plus[F]): F[Int] =
      F.plus(fa, fb)

    implicit val s = TestScheduler()
    val fa = apply(Observable(10), Observable(20)).sumL.runAsync

    s.tick()
    assertEquals(fa.value, Some(Success(30)))
  }

  test("monix to scalaz plus-empty") {
    def apply[F[_]](fa: F[Int], fb: F[Int])(implicit F: PlusEmpty[F]): F[Int] =
      F.plus(F.plus(fa, fb), F.empty)

    implicit val s = TestScheduler()
    val fa = apply(Observable(10), Observable(20)).sumL.runAsync

    s.tick()
    assertEquals(fa.value, Some(Success(30)))
  }

  test("monix to scalaz monad-empty") {
    def apply[F[_]](fa: F[Int], fb: F[Int])(implicit F: MonadPlus[F]): F[Int] = {
      val fa1 = F.plus(F.plus(fa, fb), F.empty)
      val fa2 = F.plus(F.plus(fa1, F.pure(10)), F.pure(11))
      F.filter(fa2)(_ % 2 == 0)
    }

    implicit val s = TestScheduler()
    val fa = apply(Observable(10), Observable(20)).sumL.runAsync

    s.tick()
    assertEquals(fa.value, Some(Success(40)))
  }

  test("monix to scalaz bind-rec") {
    val F = BindRec[Coeval]
    val coeval = F.tailrecM[Int,Int](
      { x =>
        if (x < 10)
          Coeval.eval(-\/(x + 1))
        else
          Coeval.eval(\/-(x))
      })(0)

    val result = coeval.value
    assertEquals(result, 10)
  }

  test("monix to scalaz catchable") {
    def apply[F[_]](fa: F[Int])(implicit F: Catchable[F], A: Functor[F]): F[Int] =
      A.map(F.attempt(fa)) {
        case -\/(err) => 0
        case \/-(a) => a + 1
      }

    val fa = apply(Coeval(1))
    assertEquals(fa.value, 2)

    val fb = apply(Coeval.raiseError[Int](new RuntimeException))
    assertEquals(fb.value, 0)

    val fc = apply(implicitly[Catchable[Coeval]].fail[Int](new RuntimeException))
    assertEquals(fc.value, 0)
  }
}
