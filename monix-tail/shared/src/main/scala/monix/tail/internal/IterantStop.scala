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
import monix.tail.internal.IterantUtils._
import monix.types.Monad
import monix.types.syntax._
import scala.util.control.NonFatal

private[tail] object IterantStop {
  /**
    * Implementation for `Iterant#doOnEarlyStop`
    */
  def doOnEarlyStop[F[_], A](source: Iterant[F, A], f: F[Unit])(implicit F: Monad[F]): Iterant[F,A] = {
    import F.functor
    source match {
      case Next(head, rest, stop) =>
        Next(head, rest.map(doOnEarlyStop[F, A](_, f)), stop.flatMap(_ => f))
      case NextSeq(items, rest, stop) =>
        NextSeq(items, rest.map(doOnEarlyStop[F, A](_, f)), stop.flatMap(_ => f))
      case Suspend(rest, stop) =>
        Suspend(rest.map(doOnEarlyStop[F, A](_, f)), stop.flatMap(_ => f))
      case NextGen(items, rest, stop) =>
        NextGen(items, rest.map(doOnEarlyStop[F, A](_, f)), stop.flatMap(_ => f))
      case ref @ (Halt(_) | Last(_)) =>
        ref // nothing to do
    }
  }

  /**
    * Implementation for `Iterant#doOnFinish`
    */
  def doOnFinish[F[_], A](source: Iterant[F, A], f: Option[Throwable] => F[Unit])(implicit F: Monad[F]): Iterant[F,A] = {
    import F.{functor => U}
    try source match {
      case Next(item, rest, stop) =>
        Next(item, rest.map(doOnFinish[F, A](_, f)), stop.flatMap(_ => f(None)))
      case NextSeq(items, rest, stop) =>
        NextSeq(items, rest.map(doOnFinish[F, A](_, f)), stop.flatMap(_ => f(None)))
      case NextGen(items, rest, stop) =>
        NextGen(items, rest.map(doOnFinish[F, A](_, f)), stop.flatMap(_ => f(None)))
      case Suspend(rest, stop) =>
        Suspend(rest.map(doOnFinish[F, A](_, f)), stop.flatMap(_ => f(None)))
      case last @ Last(_) =>
        val ref = f(None)
        Suspend[F,A](U.map(ref)(_ => last), ref)
      case halt @ Halt(ex) =>
        val ref = f(ex)
        Suspend[F,A](U.map(ref)(_ => halt), ref)
    }
    catch {
      case NonFatal(ex) => signalError(source, ex)
    }
  }
}
