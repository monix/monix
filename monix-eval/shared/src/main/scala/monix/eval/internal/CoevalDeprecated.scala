/*
 * Copyright (c) 2014-2019 by The Monix Project Developers.
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
package internal

import cats.Eval
import cats.effect.IO

private[eval] object CoevalDeprecated {
  /**
    * Extension methods describing deprecated `Task` operations.
    */
  private[eval] trait Extensions[+A] extends Any {
    def self: Coeval[A]

    /**
      * DEPRECATED — Converts the source [[Coeval]] into a `cats.effect.IO`.
      *
      * Please switch to [[Coeval.toK]]:
      * {{{
      *   import cats.effect.IO
      *   import monix.eval._
      *
      *   val value = Coeval { 1 + 1 }
      *
      *   val result: IO[Int] = value.to[IO]
      * }}}
      */
    @deprecated("Use value.to[IO]", "3.0.0")
    final def toIO: IO[A] =
      self.to[IO]

    /**
      * DEPRECATED — Converts the source [[Coeval]] into a `cats.Eval`.
      *
      * Please switch to [[Coeval.toK]]:
      * {{{
      *   import cats.Eval
      *   import monix.eval._
      *
      *   val value = Coeval { 1 + 1 }
      *
      *   val result: Eval[Int] = value.to[Eval]
      * }}}
      */
    @deprecated("Use value.to[Eval]", "3.0.0")
    final def toEval: Eval[A] =
      self.to[Eval]

    /**
      * DEPRECATED — Converts the source [[Coeval]] into a [[Task]].
      *
      * Please switch to [[Coeval.toK]]:
      * {{{
      *   import monix.eval._
      *
      *   val value = Coeval { 1 + 1 }
      *
      *   val result: Task[Int] = value.to[Task]
      * }}}
      */
    @deprecated("Use value.to[Task]", "3.0.0")
    final def task: Task[A] =
      self.to[Task]
  }

  /**
    * Deprecated builders.
    */
  private[eval] abstract class Companion {
    /**
      * DEPRECATED — please switch to [[Coeval.from]].
      */
    @deprecated("Switch to Coeval.from", since = "3.0.0-RC3")
    def fromEval[A](a: Eval[A]): Coeval[A] =
      Coeval.from(a)
  }
}
