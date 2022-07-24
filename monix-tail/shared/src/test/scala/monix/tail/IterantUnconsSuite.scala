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

import cats.Monoid
import cats.effect.Sync
import cats.implicits._
import cats.laws._
import cats.laws.discipline._
import monix.eval.Coeval

class IterantUnconsSuite extends BaseTestSuite {
  fixture.test("uncons is reversible with flatMap and concat") { implicit s =>
    check1 { (stream: Iterant[Coeval, Int]) =>
      stream <-> stream.uncons.flatMap {
        case (opt, rest) =>
          opt.map(Iterant[Coeval].pure).foldK ++ rest
      }
    }
  }

  test("uncons ignoring Option is equivalent to tail") {
    check1 { (stream: Iterant[Coeval, Int]) =>
      stream.tail <-> stream.uncons.flatMap { case (_, rest) => rest }
    }
  }

  test("uncons ignoring tail is equivalent to headOptionL") {
    check1 { (stream: Iterant[Coeval, Int]) =>
      Iterant[Coeval].liftF(stream.headOptionL) <-> stream.uncons.map { case (hd, _) => hd }
    }
  }

  test("any fold is expressible using unconsR") {
    def unconsFold[F[_]: Sync, A: Monoid](iterant: Iterant[F, A]): F[A] = {
      def go(iterant: Iterant[F, A], acc: A): Iterant[F, A] =
        iterant.uncons.flatMap {
          case (None, _) => Iterant.pure(acc)
          case (Some(a), rest) => go(rest, acc |+| a)
        }

      go(iterant, Monoid[A].empty).headOptionL.map(_.getOrElse(Monoid[A].empty))
    }

    check1 { (stream: Iterant[Coeval, Int]) =>
      stream.foldL <-> unconsFold(stream)
    }
  }
}
