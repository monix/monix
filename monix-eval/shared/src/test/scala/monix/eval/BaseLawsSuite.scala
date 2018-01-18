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

import cats.Eq
import cats.effect.IO
import monix.execution.Cancelable
import monix.execution.schedulers.TestScheduler
import monix.execution.schedulers.TrampolineExecutionContext.immediate
import org.scalacheck.{Arbitrary, Cogen, Gen}

trait BaseLawsSuite extends monix.execution.BaseLawsSuite with ArbitraryInstances

trait ArbitraryInstances extends ArbitraryInstancesBase {
  implicit def equalityTask[A](implicit A: Eq[A], ec: TestScheduler): Eq[Task[A]] =
    new Eq[Task[A]] {
      def eqv(lh: Task[A], rh: Task[A]): Boolean =
        equalityFuture(A, ec).eqv(lh.runAsync, rh.runAsync)
    }

  implicit def equalityTaskPar[A](implicit A: Eq[A], ec: TestScheduler): Eq[Task.Par[A]] =
    new Eq[Task.Par[A]] {
      import Task.Par.unwrap
      def eqv(lh: Task.Par[A], rh: Task.Par[A]): Boolean =
        Eq[Task[A]].eqv(unwrap(lh), unwrap(rh))
    }

  implicit def equalityIO[A](implicit A: Eq[A], ec: TestScheduler): Eq[IO[A]] =
    new Eq[IO[A]] {
      def eqv(x: IO[A], y: IO[A]): Boolean =
        equalityFuture[A].eqv(x.unsafeToFuture(), y.unsafeToFuture())
    }
}

trait ArbitraryInstancesBase extends monix.execution.ArbitraryInstances {
  implicit def arbitraryCoeval[A](implicit A: Arbitrary[A]): Arbitrary[Coeval[A]] =
    Arbitrary {
      for {
        a <- A.arbitrary
        coeval <- Gen.oneOf(Coeval.now(a), Coeval.evalOnce(a), Coeval.eval(a))
      } yield coeval
    }

  implicit def arbitraryTask[A](implicit A: Arbitrary[A]): Arbitrary[Task[A]] =
    Arbitrary {
      for {
        a <- A.arbitrary
        task <- Gen.oneOf(
          Task.now(a), Task.evalOnce(a), Task.eval(a),
          Task.create[A] { (_, cb) =>
            cb.onSuccess(a)
            Cancelable.empty
          })
      } yield task
    }

  implicit def arbitraryTaskPar[A](implicit A: Arbitrary[A]): Arbitrary[Task.Par[A]] =
    Arbitrary {
      for {
        a <- A.arbitrary
        task <- Gen.oneOf(
          Task.now(a), Task.evalOnce(a), Task.eval(a),
          Task.create[A] { (_, cb) =>
            cb.onSuccess(a)
            Cancelable.empty
          })
      } yield Task.Par.apply(task)
    }

  implicit def arbitraryIO[A](implicit A: Arbitrary[A]): Arbitrary[IO[A]] =
    Arbitrary {
      for {
        a <- A.arbitrary
        io <- Gen.oneOf(
          IO.pure(a), IO(a),
          IO.async[A](f =>
            immediate.execute(new Runnable { def run() = f(Right(a)) })
          ))
      } yield io
    }

  implicit def arbitraryExToA[A](implicit A: Arbitrary[A]): Arbitrary[Throwable => A] =
    Arbitrary {
      val fun = implicitly[Arbitrary[Int => A]]
      for (f <- fun.arbitrary) yield (t: Throwable) => f(t.hashCode())
    }

  implicit def arbitraryPfExToA[A](implicit A: Arbitrary[A]): Arbitrary[PartialFunction[Throwable, A]] =
    Arbitrary {
      val fun = implicitly[Arbitrary[Int => A]]
      for (f <- fun.arbitrary) yield PartialFunction((t: Throwable) => f(t.hashCode()))
    }

  implicit def arbitraryCoevalToLong[A, B](implicit A: Arbitrary[A], B: Arbitrary[B]): Arbitrary[Coeval[A] => B] =
    Arbitrary {
      for (b <- B.arbitrary) yield (_: Coeval[A]) => b
    }

  implicit def arbitraryTaskToLong[A, B](implicit A: Arbitrary[A], B: Arbitrary[B]): Arbitrary[Task[A] => B] =
    Arbitrary {
      for (b <- B.arbitrary) yield (_: Task[A]) => b
    }

  implicit def arbitraryIOToLong[A, B](implicit A: Arbitrary[A], B: Arbitrary[B]): Arbitrary[IO[A] => B] =
    Arbitrary {
      for (b <- B.arbitrary) yield (_: IO[A]) => b
    }

  implicit def equalityCoeval[A](implicit A: Eq[A]): Eq[Coeval[A]] =
    new Eq[Coeval[A]] {
      def eqv(lh: Coeval[A], rh: Coeval[A]): Boolean = {
        val valueA = lh.runTry
        val valueB = rh.runTry

        (valueA.isFailure && valueB.isFailure) ||
          A.eqv(valueA.get, valueB.get)
      }
    }

  implicit def cogenForTask[A]: Cogen[Task[A]] =
    Cogen[Unit].contramap(_ => ())
  implicit def cogenForIO[A]: Cogen[IO[A]] =
    Cogen[Unit].contramap(_ => ())
  implicit def cogenForCoeval[A](implicit A: Numeric[A]): Cogen[Coeval[A]] =
    Cogen((x: Coeval[A]) => A.toLong(x.value))

//  implicit val isoTask: Isomorphisms[Task] =
//    Isomorphisms.invariant
//  implicit val isoCoeval: Isomorphisms[Coeval] =
//    Isomorphisms.invariant
}
