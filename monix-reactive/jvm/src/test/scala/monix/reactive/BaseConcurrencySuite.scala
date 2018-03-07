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

package monix.reactive

import cats.Eq
import minitest.TestSuite
import minitest.laws.Checkers
import monix.eval.Task
import monix.execution.Scheduler
import monix.execution.schedulers.SchedulerService

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success}

trait BaseConcurrencySuite extends TestSuite[SchedulerService]
  with Checkers with ArbitraryInstancesBase {

  def setup(): SchedulerService = {
    Scheduler.computation(
      parallelism = 4,
      name = "concurrency-tests",
      daemonic = true)
  }

  def tearDown(env: SchedulerService): Unit = {
    env.shutdown()
    assert(env.awaitTermination(1.minute), "scheduler.awaitTermination")
  }

  implicit def equalityObservable[A](implicit A: Eq[A], ec: Scheduler): Eq[Observable[A]] =
    new Eq[Observable[A]] {
      def eqv(lh: Observable[A], rh: Observable[A]): Boolean = {
        val eqList = implicitly[Eq[Option[List[A]]]]
        val fa = lh.foldLeftF(List.empty[A])((acc,e) => e :: acc).firstOptionL.runAsync
        val fb = rh.foldLeftF(List.empty[A])((acc,e) => e :: acc).firstOptionL.runAsync
        equalityFuture(eqList, ec).eqv(fa, fb)
      }
    }

  implicit def equalityTask[A](implicit A: Eq[A], ec: Scheduler): Eq[Task[A]] =
    new Eq[Task[A]] {
      def eqv(lh: Task[A], rh: Task[A]): Boolean =
        equalityFuture(A, ec).eqv(lh.runAsync, rh.runAsync)
    }

  implicit def equalityFuture[A](implicit A: Eq[A], ec: Scheduler): Eq[Future[A]] =
    new Eq[Future[A]] {
      def eqv(x: Future[A], y: Future[A]): Boolean = {
        Await.ready(for (_ <- x; _ <- y) yield (), 5.minutes)

        x.value match {
          case None =>
            y.value.isEmpty
          case Some(Success(a)) =>
            y.value match {
              case Some(Success(b)) => A.eqv(a, b)
              case _ => false
            }
          case Some(Failure(ex1)) =>
            y.value match {
              case Some(Failure(ex2)) =>
                equalityThrowable.eqv(ex1, ex2)
              case _ =>
                false
            }
        }
      }
    }
}
