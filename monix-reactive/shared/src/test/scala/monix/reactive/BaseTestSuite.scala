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

package monix.reactive

import cats.Eq
import minitest.{SimpleTestSuite, TestSuite}
import minitest.laws.Checkers
import monix.execution.internal.Platform
import monix.execution.schedulers.TestScheduler
import org.scalacheck.Test.Parameters
import org.scalacheck.{Arbitrary, Prop}
import org.typelevel.discipline.Laws
import scala.concurrent.duration._

trait BaseTestSuite extends TestSuite[TestScheduler] with Checkers with ArbitraryInstances {
  def setup(): TestScheduler = TestScheduler()
  def tearDown(env: TestScheduler): Unit = {
    assert(env.state.tasks.isEmpty, "should not have tasks left to execute")
  }
}

trait BaseLawsTestSuite extends SimpleTestSuite with Checkers with ArbitraryInstances {
  override lazy val checkConfig: Parameters =
    Parameters.default
      .withMinSuccessfulTests(if (Platform.isJVM) 100 else 10)
      .withMaxDiscardRatio(if (Platform.isJVM) 5.0f else 50.0f)
      .withMaxSize(10)

  def checkAllAsync(name: String, config: Parameters = checkConfig)
    (f: TestScheduler => Laws#RuleSet): Unit = {

    val s = TestScheduler()
    val ruleSet = f(s)

    for ((id, prop: Prop) ← ruleSet.all.properties)
      test(name + "." + id) {
        s.tick(1.day)
        check(prop)
      }
  }
}

trait ArbitraryInstances extends ArbitraryInstancesBase with monix.eval.ArbitraryInstances {
  implicit def equalityObservable[A](implicit A: Eq[A], ec: TestScheduler): Eq[Observable[A]] =
    new Eq[Observable[A]] {
      def eqv(lh: Observable[A], rh: Observable[A]): Boolean = {
        val eqList = implicitly[Eq[Option[List[A]]]]
        val fa = lh.foldLeftF(List.empty[A])((acc,e) => e :: acc).firstOptionL.runAsync
        val fb = rh.foldLeftF(List.empty[A])((acc,e) => e :: acc).firstOptionL.runAsync
        equalityFuture(eqList, ec).eqv(fa, fb)
      }
    }
}

trait ArbitraryInstancesBase extends monix.eval.ArbitraryInstancesBase {
  implicit def arbitraryObservable[A : Arbitrary]: Arbitrary[Observable[A]] =
    Arbitrary {
      implicitly[Arbitrary[List[A]]].arbitrary
        .map(Observable.fromIterable)
    }

  implicit lazy val arbitraryThrowable: Arbitrary[Throwable] =
    Arbitrary {
      implicitly[Arbitrary[Int]].arbitrary
        .map(number => new RuntimeException(number.toString))
    }
}
