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
import org.scalacheck.Arbitrary

object TypeClassLawsForCoevalSuite extends MemoizableLawsSuite[Coeval,Int,Long,Short]
  with SuspendableLawsSuite[Coeval,Int,Long,Short]
  with MonadErrorLawsSuite[Coeval,Int,Long,Short,Throwable]
  with CobindLawsSuite[Coeval,Int,Long,Short]
  with MonadRecLawsSuite[Coeval,Int,Long,Short]
  with ComonadLawsSuite[Coeval,Int,Long,Short] {

  override def F: Coeval.TypeClassInstances =
    Coeval.typeClassInstances

  implicit def arbitraryCoeval[A](implicit A: Arbitrary[A]): Arbitrary[Coeval[A]] =
    Arbitrary {
      val intGen = implicitly[Arbitrary[Int]]
      for (a <- A.arbitrary; int <- intGen.arbitrary) yield {
        if (int % 3 == 0) Coeval.now(a)
        else if (int % 3 == 1) Coeval.evalOnce(a)
        else Coeval.eval(a)
      }
    }

  implicit def isEqCoeval[A](implicit A: Eq[A]): Eq[Coeval[A]] =
    new Eq[Coeval[A]] {
      def apply(lh: Coeval[A], rh: Coeval[A]): Boolean = {
        val valueA = lh.runTry
        val valueB = rh.runTry

        (valueA.isFailure && valueB.isFailure) ||
          A(valueA.get, valueB.get)
      }
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

  implicit def arbitraryCoevalToLong[A,B](implicit A: Arbitrary[A], B: Arbitrary[B]): Arbitrary[Coeval[A] => B] =
    Arbitrary {
      for (b <- B.arbitrary) yield (t: Coeval[A]) => b
    }

  // Actual tests ...

  monadEvalErrorCheck("Coeval")
  memoizableCheck("Coeval", includeSupertypes = true)
  monadErrorCheck("Coeval", includeSupertypes = false)
  monadRecCheck("Coeval", includeSupertypes = false)
  cobindCheck("Coeval", includeSupertypes = false)
  comonadCheck("Coeval", includeSupertypes = false)
}
