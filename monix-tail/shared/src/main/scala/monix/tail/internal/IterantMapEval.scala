/*
 * Copyright (c) 2014-2017 by its authors. Some rights reserved.
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

import monix.tail.Iterant
import monix.tail.Iterant._
import monix.tail.internal.IterantUtils._
import monix.types.Applicative
import monix.types.syntax._
import scala.util.control.NonFatal

private[tail] object IterantMapEval {
  /**
    * Implementation for `Iterant#mapEval`
    */
  def apply[F[_], A, B](source: Iterant[F, A], f: A => F[B])(implicit A: Applicative[F]): Iterant[F, B] = {
    import A.{functor => F}

    @inline def evalNextSeq(ref: NextSeq[F, A], cursor: Iterator[A], rest: F[Iterant[F, A]], stop: F[Unit]) = {
      if (!cursor.hasNext)
        Suspend[F, B](rest.map(loop), stop)
      else {
        val head = cursor.next()
        val fa = f(head)
        // If the iterator is empty, then we can skip a beat
        val tail = if (cursor.hasNext) A.pure(ref: Iterant[F, A]) else rest
        val suspended = fa.map(h => nextS(h, tail.map(loop), stop))
        Suspend[F, B](suspended, stop)
      }
    }

    def loop(source: Iterant[F, A]): Iterant[F, B] =
      try source match {
        case Next(head, tail, stop) =>
          val fa = f(head)
          val rest = fa.map(h => nextS(h, tail.map(loop), stop))
          Suspend(rest, stop)

        case ref @ NextSeq(cursor, rest, stop) =>
          evalNextSeq(ref, cursor, rest, stop)

        case NextGen(gen, rest, stop) =>
          val cursor = gen.iterator
          val ref = NextSeq(cursor, rest, stop)
          evalNextSeq(ref, cursor, rest, stop)

        case Suspend(rest, stop) =>
          Suspend[F,B](rest.map(loop), stop)

        case Last(item) =>
          val fa = f(item)
          Suspend(fa.map(h => lastS[F,B](h)), A.unit)

        case halt @ Halt(_) =>
          halt.asInstanceOf[Iterant[F, B]]
      }
      catch {
        case NonFatal(ex) => signalError(source, ex)
      }

    source match {
      case Suspend(_, _) | Halt(_) => loop(source)
      case _ =>
        // Given function can be side-effecting,
        // so we must suspend the execution
        Suspend(A.eval(loop(source)), source.earlyStop)
    }
  }
}
