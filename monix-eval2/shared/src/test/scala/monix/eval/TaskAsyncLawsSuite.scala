/*
 * Copyright (c) 2014-2022 Monix Contributors.
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

import cats.effect.kernel.{ Async, Outcome }
import cats.effect.kernel.testkit.{ AsyncGenerators, GenK }
import cats.effect.kernel.testkit.OutcomeGenerators._
import cats.effect.kernel.testkit.SyncTypeGenerators._
import cats.effect.laws.AsyncTests
import cats.kernel.{ Eq, Group, Order }
import cats.laws.discipline.SemigroupalTests
import monix.execution.{ BaseLawsSuite, Callback }
import monix.execution.schedulers.TestScheduler
import org.scalacheck.{ Arbitrary, Cogen, Gen, Prop }

import java.util.concurrent.TimeUnit
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

object TaskAsyncLawsSuite extends BaseLawsSuite {
  private implicit val scheduler: TestScheduler = TestScheduler()

  implicit lazy val arbitraryFiniteDuration: Arbitrary[FiniteDuration] =
    Arbitrary {
      Gen.oneOf(
        TimeUnit.NANOSECONDS,
        TimeUnit.MICROSECONDS,
        TimeUnit.MILLISECONDS,
        TimeUnit.SECONDS,
        TimeUnit.MINUTES,
        TimeUnit.HOURS
      ).flatMap(unit => Gen.choose[Long](0L, 48L).map(FiniteDuration(_, unit)))
    }

  implicit val arbitraryExecutionContext: Arbitrary[ExecutionContext] =
    Arbitrary(Gen.const(scheduler))

  implicit def cogenIO[A]: Cogen[Task[A]] =
    Cogen[Unit].contramap(_ => ())

  private val asyncGenerators = new AsyncGenerators[Task] {
    override implicit val F: Async[Task] = Task.catsEffectAsyncForTask
    override implicit val arbitraryE: Arbitrary[Throwable] = arbitraryThrowable
    override implicit val cogenE: Cogen[Throwable] = cogenForThrowable
    override protected implicit val arbitraryFD: Arbitrary[FiniteDuration] = arbitraryFiniteDuration
    override protected implicit val arbitraryEC: Arbitrary[ExecutionContext] = arbitraryExecutionContext
    override protected implicit val cogenFU: Cogen[Task[Unit]] = cogenIO[Unit]

    override def recursiveGen[B: Arbitrary: Cogen](deeper: GenK[Task]) =
      super.recursiveGen[B](deeper).filterNot(_._1 == "racePair")
  }

  implicit def arbitraryIO[A: Arbitrary: Cogen]: Arbitrary[Task[A]] =
    Arbitrary(asyncGenerators.generators[A])

  implicit val eqExecutionContext: Eq[ExecutionContext] =
    Eq.fromUniversalEquals

  implicit def eqOutcome[A: Eq]: Eq[Outcome[Task, Throwable, A]] =
    Eq.instance {
      case (Outcome.Succeeded(left), Outcome.Succeeded(right)) => Eq[Task[A]].eqv(left, right)
      case (Outcome.Errored(left), Outcome.Errored(right)) => Eq[Throwable].eqv(left, right)
      case (left, right) => left.isCanceled && right.isCanceled
    }

  implicit def eqIO[A: Eq]: Eq[Task[A]] =
    Eq.instance { (left, right) =>
      unsafeRunPair(left, right) match {
        case (Some(Right(a)), Some(Right(b))) => Eq[A].eqv(a, b)
        case (Some(Left(a)), Some(Left(b))) => Eq[Throwable].eqv(a, b)
        case (None, None) => true
        case _ => false
      }
    }

  implicit val orderTaskFiniteDuration: Order[Task[FiniteDuration]] =
    new Order[Task[FiniteDuration]] {
      override def compare(left: Task[FiniteDuration], right: Task[FiniteDuration]): Int =
        unsafeRunPair(left, right) match {
          case (Some(Right(a)), Some(Right(b))) => a.compare(b)
          case (Some(Right(_)), _) => 1
          case (_, Some(Right(_))) => -1
          case (Some(Left(_)), None) => 1
          case (None, Some(Left(_))) => -1
          case _ => 0
        }
    }

  implicit val finiteDurationGroup: Group[FiniteDuration] =
    new Group[FiniteDuration] {
      override def empty: FiniteDuration = Duration.Zero
      override def combine(left: FiniteDuration, right: FiniteDuration): FiniteDuration = left + right
      override def inverse(value: FiniteDuration): FiniteDuration = -value
    }

  implicit val isomorphisms: SemigroupalTests.Isomorphisms[Task] =
    SemigroupalTests.Isomorphisms.invariant[Task]

  implicit val executeBoolean: Task[Boolean] => Prop =
    value => Prop(unsafeRun(value).contains(Right(true)))

  private val asyncRuleSet = AsyncTests[Task].async[Int, Int, Int](10.millis)

  test("Async[IO] law set is non-empty") {
    assert(asyncRuleSet.all.properties.nonEmpty)
  }

  checkAll("Async[IO]", asyncRuleSet)

  private def unsafeRun[A](source: Task[A]): Option[Either[Throwable, A]] = {
    var result = Option.empty[Either[Throwable, A]]
    source.unsafeRunAsync(new Callback[Throwable, A] {
      override def onSuccess(value: A): Unit = result = Some(Right(value))
      override def onError(error: Throwable): Unit = result = Some(Left(error))
    })
    drainScheduler()
    result
  }

  private def unsafeRunPair[A](
    left: Task[A],
    right: Task[A]
  ): (Option[Either[Throwable, A]], Option[Either[Throwable, A]]) = {
    var leftResult = Option.empty[Either[Throwable, A]]
    var rightResult = Option.empty[Either[Throwable, A]]

    left.unsafeRunAsync(new Callback[Throwable, A] {
      override def onSuccess(value: A): Unit = leftResult = Some(Right(value))
      override def onError(error: Throwable): Unit = leftResult = Some(Left(error))
    })
    right.unsafeRunAsync(new Callback[Throwable, A] {
      override def onSuccess(value: A): Unit = rightResult = Some(Right(value))
      override def onError(error: Throwable): Unit = rightResult = Some(Left(error))
    })

    drainScheduler()
    (leftResult, rightResult)
  }

  private def drainScheduler(): Unit = {
    var iterations = 0
    while (scheduler.state.tasks.nonEmpty && iterations < 100) {
      val state = scheduler.state
      scheduler.tick(state.tasks.head.runsAt - state.clock, maxImmediateTasks = Some(10000))
      iterations += 1
    }
  }
}
