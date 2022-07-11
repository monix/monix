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

package monix.tail.internal

import cats.effect.Sync
import monix.tail.Iterant
import monix.tail.Iterant.{ Last, Next, Suspend }

private[tail] object IterantTail {
  /**
    * Implementation for `Iterant#tail`
    */
  def apply[F[_], A](source: Iterant[F, A])(implicit F: Sync[F]): Iterant[F, A] = {

    source match {
      case Next(_, rest) => Suspend(rest)
      case Last(_) => Iterant.empty
      case _ => source.drop(1)
    }
  }
}
