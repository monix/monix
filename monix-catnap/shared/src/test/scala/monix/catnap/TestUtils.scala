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

package monix.catnap

import scala.concurrent.duration.{Duration, FiniteDuration}

import cats.effect.IO
import cats.effect.unsafe.{IORuntime, Scheduler}
import monix.execution.schedulers.TestScheduler

import java.util.concurrent.TimeUnit


object TestUtils {
  implicit def ioRuntime(implicit sc: monix.execution.Scheduler): IORuntime =
    IORuntime(sc, sc, new Scheduler {
      override def sleep(delay: FiniteDuration, task: Runnable): Runnable =
        sc.scheduleOnce(delay.length, delay.unit, task).cancel _

      override def nowMillis(): Long = sc.clockRealTime(TimeUnit.MILLISECONDS)
      override def monotonicNanos(): Long = sc.clockMonotonic(TimeUnit.NANOSECONDS)
    }, () => {})

  implicit class IOExtensions[A](io: IO[A]) {
    def unsafeRunCancelableTick(tick: FiniteDuration = Duration.Zero)(cb: Either[Throwable, A] => Unit)(implicit tsc: TestScheduler): CancelToken[IO] =
      io.attempt.map(cb).start.unsafeRunSyncTick(tick).cancel

    def unsafeRunSyncTick(tick: FiniteDuration = Duration.Zero)(implicit tsc: TestScheduler) = {
      unsafeToFutureTick(tick).value.get.get
    }

    def unsafeToFutureTick(tick: FiniteDuration = Duration.Zero)(implicit tsc: TestScheduler) = {
      val f = io.unsafeToFuture()
      tsc.tick(tick)
      f
    }
  }
}
