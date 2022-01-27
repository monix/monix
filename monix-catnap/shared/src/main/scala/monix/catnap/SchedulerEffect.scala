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

package monix.catnap

import cats.implicits._
import cats.effect._
import monix.execution.Scheduler
import monix.execution.internal.AttemptCallback.RunnableTick

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.{FiniteDuration, TimeUnit}
import cats.Applicative
import java.util.concurrent.TimeUnit.MILLISECONDS

object SchedulerEffect {

  /**
    * Derives a `cats.effect.Clock` from [[monix.execution.Scheduler Scheduler]] for any
    * data type that has a `cats.effect.LiftIO` implementation.
    */
  def clock[F[_]](source: Scheduler)(implicit F: Sync[F]): Clock[F] =
    new Clock[F] {
      def applicative: Applicative[F] = F.applicative
      def monotonic: F[FiniteDuration] = F.delay(source.clockMonotonic(MILLISECONDS)).map(FiniteDuration(_, MILLISECONDS))
      def realTime: F[FiniteDuration] = F.delay(source.clockRealTime(MILLISECONDS)).map(FiniteDuration(_, MILLISECONDS))
    }

  /**
    * Derives a `cats.effect.Timer` from [[monix.execution.Scheduler Scheduler]] for any
    * data type that has a `cats.effect.Concurrent` type class
    * instance.
    *
    * {{{
    *   import monix.execution.Scheduler
    *   import cats.effect._
    *   import scala.concurrent.duration._
    *
    *   // Needed for ContextShift[IO]
    *   implicit def shift: ContextShift[IO] =
    *     SchedulerEffect.contextShift[IO](Scheduler.global)(IO.ioEffect)
    *
    *   implicit val timer: Timer[IO] = SchedulerEffect.timer[IO](Scheduler.global)
    *
    *   IO.sleep(10.seconds).flatMap { _ =>
    *     IO(println("Delayed hello!"))
    *   }
    * }}}
    */
    // FIXME: Does it make sense? temporary perhaps?
  // def timer[F[_]](source: Scheduler)(implicit F: Concurrent[F]): Timer[F] =
  //   new Timer[F] {
  //     override def sleep(d: FiniteDuration): F[Unit] =
  //       F.cancelable { cb =>
  //         val token = source.scheduleOnce(d.length, d.unit, new RunnableTick(cb))
  //         F.delay(token.cancel())
  //       }
  //     override val clock: Clock[F] =
  //       SchedulerEffect.clock(source)
  //   }

  /**
    * Derives a `cats.effect.Timer` from [[monix.execution.Scheduler Scheduler]] for any
    * data type that has a `cats.effect.LiftIO` instance.
    *
    * This is the relaxed [[timer]] method, needing only `LiftIO`
    * to work, by piggybacking on `cats.effect.IO`.
    *
    * {{{
    *   import monix.execution.Scheduler
    *   import cats.effect._
    *   import scala.concurrent.duration._
    *
    *   implicit val timer: Timer[IO] = SchedulerEffect.timerLiftIO[IO](Scheduler.global)
    *
    *   IO.sleep(10.seconds).flatMap { _ =>
    *     IO(println("Delayed hello!"))
    *   }
    * }}}
    */
  // def timerLiftIO[F[_]](source: Scheduler)(implicit F: LiftIO[F]): Timer[F] =
  //   new Timer[F] {
  //     override def sleep(d: FiniteDuration): F[Unit] =
  //       F.liftIO(IO.cancelable { cb =>
  //         val token = source.scheduleOnce(d.length, d.unit, new RunnableTick(cb))
  //         IO(token.cancel())
  //       })
  //     override val clock: Clock[F] =
  //       new Clock[F] {
  //         def realTime(unit: TimeUnit): F[Long] =
  //           F.liftIO(IO(source.clockRealTime(unit)))
  //         def monotonic(unit: TimeUnit): F[Long] =
  //           F.liftIO(IO(source.clockMonotonic(unit)))
  //       }
  //   }

}
