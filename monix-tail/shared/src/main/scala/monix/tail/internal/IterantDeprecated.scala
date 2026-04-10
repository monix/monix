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

package monix.tail
package internal

import cats.arrow.FunctionK
import cats.effect.Sync

private[tail] object IterantDeprecated {
  /**
    * Extension methods describing deprecated `Iterant` operations.
    */
  private[tail] trait Extensions[F[_], A] extends Any {
    def self: Iterant[F, A]

    /** DEPRECATED — please switch to [[Iterant.mapK]]. */
    @deprecated("Renamed to Iterant.mapK", since = "3.0.0-RC3")
    final def liftMap[G[_]](f: FunctionK[F, G])(implicit G: Sync[G]): Iterant[G, A] = {
      // $COVERAGE-OFF$
      self.mapK(f)
      // $COVERAGE-ON$
    }
  }
}
