/*
 * Copyright (c) 2014-2020 by The Monix Project Developers.
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
import cats.Monoid
import minitest.{SimpleTestSuite, TestSuite}
import minitest.laws.Checkers
import monix.eval.Task
import monix.execution.internal.Platform
import monix.execution.schedulers.TestScheduler
import monix.reactive.Notification.{OnComplete, OnError, OnNext}
import monix.reactive.observables.CombineObservable
import monix.reactive.subjects._
import org.scalacheck.Test.Parameters
import org.scalacheck.{Arbitrary, Cogen, Gen, Prop}
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

  def checkAllAsync(name: String, config: Parameters = checkConfig)(f: TestScheduler => Laws#RuleSet): Unit = {

    val s = TestScheduler()
    val ruleSet = f(s)

    for ((id, prop: Prop) <- ruleSet.all.properties)
      test(name + "." + id) {
        s.tick(1.day)
        check(prop)
      }
  }
}

trait ArbitraryInstances extends ArbitraryInstancesBase with monix.eval.ArbitraryInstances {
  implicit def equalityNotification[A](implicit A: Eq[A]): Eq[Notification[A]] =
    new Eq[Notification[A]] {
      def eqv(x: Notification[A], y: Notification[A]): Boolean = {
        x match {
          case OnNext(v1) =>
            y match {
              case OnNext(v2) => A.eqv(v1, v2)
              case _ => false
            }
          case OnError(ex1) =>
            y match {
              case OnError(ex2) => equalityThrowable.eqv(ex1, ex2)
              case _ => false
            }
          case OnComplete =>
            y == OnComplete
        }
      }
    }

  implicit def equalityObservable[A](implicit A: Eq[A], ec: TestScheduler): Eq[Observable[A]] =
    new Eq[Observable[A]] {
      def eqv(lh: Observable[A], rh: Observable[A]): Boolean = {
        val eqList = implicitly[Eq[List[Notification[A]]]]
        val fa = lh.materialize.toListL.runToFuture
        val fb = rh.materialize.toListL.runToFuture
        equalityFuture(eqList, ec).eqv(fa, fb)
      }
    }

  implicit def equalityCombineObservable[A](implicit A: Eq[A], ec: TestScheduler): Eq[CombineObservable.Type[A]] =
    new Eq[CombineObservable.Type[A]] {
      import CombineObservable.unwrap
      def eqv(lh: CombineObservable.Type[A], rh: CombineObservable.Type[A]): Boolean = {
        Eq[Observable[A]].eqv(unwrap(lh), unwrap(rh))
      }
    }

  implicit def equalitySubject[A: Arbitrary](implicit A: Eq[A], ec: TestScheduler): Eq[Subject[A, A]] =
    new Eq[Subject[A, A]] {
      def eqv(lh: Subject[A, A], rh: Subject[A, A]): Boolean = {
        val eqList = implicitly[Eq[List[Notification[A]]]]

        val arbList = implicitly[Arbitrary[List[A]]]
        val list = arbList.arbitrary.sample

        list.map(lh.feed)
        list.map(rh.feed)

        val fa = lh.materialize.toListL.runToFuture
        val fb = rh.materialize.toListL.runToFuture

        lh.size == rh.size && equalityFuture(eqList, ec).eqv(fa, fb)
      }
    }

  implicit def equalityConsumer[A: Arbitrary](implicit A: Eq[A], ec: TestScheduler): Eq[Consumer[A, A]] =
    new Eq[Consumer[A, A]] {
      override def eqv(lh: Consumer[A, A], rh: Consumer[A, A]): Boolean = {
        val eqList = implicitly[Eq[List[A]]]
        val arbObservable = implicitly[Arbitrary[Observable[A]]]

        val observable = arbObservable.arbitrary.sample

        val fa = Task.sequence(observable.map(_.consumeWith(lh)).toList).runToFuture
        val fb = Task.sequence(observable.map(_.consumeWith(rh)).toList).runToFuture

        equalityFuture(eqList, ec).eqv(fa, fb)
      }
    }
}

trait ArbitraryInstancesBase extends monix.eval.ArbitraryInstancesBase {
  implicit def arbitraryObservable[A: Arbitrary]: Arbitrary[Observable[A]] =
    Arbitrary {
      implicitly[Arbitrary[List[A]]].arbitrary
        .map(Observable.fromIterable)
    }

  implicit def arbitraryCombineObservable[A: Arbitrary]: Arbitrary[CombineObservable.Type[A]] = {
    import CombineObservable.{apply => wrap}
    Arbitrary {
      implicitly[Arbitrary[List[A]]].arbitrary
        .map(list => wrap(Observable.fromIterable(list)))
    }
  }

  implicit def arbitrarySubject[A](implicit arb: Arbitrary[A]): Arbitrary[Subject[A, A]] = Arbitrary {
    Gen.oneOf(
      Gen.const(AsyncSubject[A]()),
      Gen.const(PublishSubject[A]()),
      arb.arbitrary.map(BehaviorSubject(_)),
      implicitly[Arbitrary[List[A]]].arbitrary.map(ReplaySubject.create(_))
    )
  }

  implicit def arbitraryConsumer[A](implicit arb: Arbitrary[A], M: Monoid[A]): Arbitrary[Consumer[A, A]] =
    Arbitrary {
      Gen.oneOf(
        Gen.const(Consumer.foldLeft(M.empty)(M.combine)),
        Gen.const(Consumer.head[A])
      )
    }

  implicit def cogenForObservable[A]: Cogen[Observable[A]] =
    Cogen[Unit].contramap(_ => ())
}
