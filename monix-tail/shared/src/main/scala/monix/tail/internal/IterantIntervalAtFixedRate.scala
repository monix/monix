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

package monix.tail.internal

import cats.effect.Async
import monix.tail.Iterant
import monix.tail.Iterant.Suspend
import monix.tail.util.Timer
import scala.concurrent.duration._

private[tail] object IterantIntervalAtFixedRate {
  /**
    * Implementation for `Iterant.intervalAtFixedRate`
    */
  def apply[F[_]](initialDelay: FiniteDuration, interval: FiniteDuration)
    (implicit F: Async[F], timer: Timer[F]): Iterant[F, Long] = {

    def loop(index: Long): F[Iterant[F, Long]] = {
      timer.suspendTimed { startTime =>
        val rest = timer.suspendTimed { endTime =>
          val elapsed = (endTime - startTime).millis
          val timespan = interval - elapsed

          if (timespan > Duration.Zero) {
            F.flatMap(timer.sleep(timespan))(_ => loop(index + 1))
          } else {
            loop(index + 1)
          }
        }

        F.pure(Iterant.nextS[F, Long](index, rest, F.unit))
      }
    }

    initialDelay match {
      case Duration.Zero =>
        Suspend(loop(0), F.unit)
      case _ =>
        Suspend(F.flatMap(timer.sleep(initialDelay))(_ => loop(0)), F.unit)
    }
  }
}
