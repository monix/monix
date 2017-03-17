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
import monix.tail.Iterant.{Halt, Last, Next, NextGen, NextSeq, Suspend}
import monix.tail.internal.IterantUtils.signalError
import monix.types.syntax._
import monix.types.{Applicative, Monad}
import scala.util.control.NonFatal

private[tail] object IterantConcat {
  /**
    * Implementation for `Iterant#flatMap`
    */
  def flatMap[F[_], A, B](source: Iterant[F, A], f: A => Iterant[F, B])
    (implicit F: Monad[F]): Iterant[F, B] = {
    import F.{applicative => A}

    source match {
      case Suspend(_, _) | Halt(_) => unsafeFlatMap(source)(f)
      case _ =>
        // Given function can be side-effecting,
        // so we must suspend the execution
        Suspend(A.eval(unsafeFlatMap(source)(f)), source.earlyStop)
    }
  }

  /**
    * Implementation for `Iterant#unsafeFlatMap`
    */
  def unsafeFlatMap[F[_], A, B](source: Iterant[F, A])(f: A => Iterant[F, B])
    (implicit F: Monad[F]): Iterant[F, B] = {

    import F.{functor, applicative => A}
    
    @inline def generate(item: A, rest: F[Iterant[F, B]], stop: F[Unit]): Iterant[F, B] =
      f(item) match {
        case next @ (Next(_,_,_) | NextSeq(_,_,_) | NextGen(_,_,_) | Suspend(_,_)) =>
          concat(next.doOnEarlyStop(stop), Suspend(rest, stop))
        case Last(value) =>
          Next(value, rest, stop)
        case Halt(None) =>
          Suspend(rest, stop)
        case Halt(Some(ex)) =>
          signalError(source, ex)
      }

    @inline def evalNextSeq(ref: NextSeq[F, A], cursor: Iterator[A], rest: F[Iterant[F, A]], stop: F[Unit]) = {
      if (!cursor.hasNext) {
        Suspend(rest.map(unsafeFlatMap(_)(f)), stop)
      }
      else {
        val item = cursor.next()
        // If iterator is empty then we can skip a beat
        val tail = if (cursor.hasNext) A.eval(flatMap(ref, f)) else rest.map(unsafeFlatMap(_)(f))
        generate(item, tail, stop)
      }
    }

    try source match {
      case Next(item, rest, stop) =>
        generate(item, rest.map(unsafeFlatMap(_)(f)), stop)

      case ref @ NextSeq(cursor, rest, stop) =>
        evalNextSeq(ref, cursor, rest, stop)

      case Suspend(rest, stop) =>
        Suspend(rest.map(unsafeFlatMap(_)(f)), stop)

      case NextGen(gen, rest, stop) =>
        val cursor = gen.iterator
        val ref = NextSeq(cursor, rest, stop)
        evalNextSeq(ref, cursor, rest, stop)

      case Last(item) =>
        f(item)

      case empty @ Halt(_) =>
        empty.asInstanceOf[Iterant[F, B]]
    }
    catch {
      case NonFatal(ex) => signalError(source, ex)
    }
  }

  /**
    * Implementation for `Iterant#++`
    */
  def concat[F[_], A](lhs: Iterant[F, A], rhs: Iterant[F, A])
    (implicit F: Applicative[F]): Iterant[F, A] = {

    import F.functor

    lhs match {
      case Next(a, lt, stop) =>
        Next(a, lt.map(concat(_, rhs)), stop)
      case NextSeq(seq, lt, stop) =>
        NextSeq(seq, lt.map(concat(_, rhs)), stop)
      case NextGen(gen, rest, stop) =>
        NextGen(gen, rest.map(concat(_, rhs)), stop)
      case Suspend(lt, stop) =>
        Suspend(lt.map(concat(_, rhs)), stop)
      case Last(item) =>
        Next(item, F.pure(rhs), rhs.earlyStop)
      case Halt(None) =>
        rhs
      case error @ Halt(Some(_)) =>
        error
    }
  }

  /**
    * Implementation for `Iterant.tailRecM`
    */
  def tailRecM[F[_], A, B](a: A)(f: A => Iterant[F, Either[A, B]])
    (implicit F: Monad[F]): Iterant[F, B] = {

    import F.{applicative => A}

    def loop(a: A): Iterant[F, B] =
      unsafeFlatMap(f(a)) {
        case Right(b) =>
          Last(b)
        case Left(nextA) =>
          Suspend(A.eval(loop(nextA)), A.unit)
      }

    // Function `f` may be side-effecting, or it might trigger
    // side-effects, so we must suspend it
    Suspend(A.eval(loop(a)), A.unit)
  }
}
