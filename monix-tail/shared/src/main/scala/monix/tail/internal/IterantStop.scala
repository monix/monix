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

import monix.tail.Iterant
import monix.tail.Iterant.{Halt, Last, Next, NextBatch, NextCursor, Suspend}
import monix.tail.internal.IterantUtils._
import cats.syntax.all._
import cats.effect.Sync
import monix.execution.misc.NonFatal

private[tail] object IterantStop {
  /**
    * Implementation for `Iterant#doOnEarlyStop`
    */
  def doOnEarlyStop[F[_], A](source: Iterant[F, A], f: F[Unit])
    (implicit F: Sync[F]): Iterant[F,A] = {

    source match {
      case Next(head, rest, stop) =>
        Next(head, rest.map(doOnEarlyStop[F, A](_, f)), stop.flatMap(_ => f))
      case NextCursor(items, rest, stop) =>
        NextCursor(items, rest.map(doOnEarlyStop[F, A](_, f)), stop.flatMap(_ => f))
      case Suspend(rest, stop) =>
        Suspend(rest.map(doOnEarlyStop[F, A](_, f)), stop.flatMap(_ => f))
      case NextBatch(items, rest, stop) =>
        NextBatch(items, rest.map(doOnEarlyStop[F, A](_, f)), stop.flatMap(_ => f))
      case ref @ (Halt(_) | Last(_)) =>
        ref // nothing to do
    }
  }

  /**
    * Implementation for `Iterant#doOnFinish`
    */
  def doOnFinish[F[_], A](source: Iterant[F, A], f: Option[Throwable] => F[Unit])
    (implicit F: Sync[F]): Iterant[F,A] = {

    try source match {
      case Next(item, rest, stop) =>
        Next(item, rest.map(doOnFinish[F, A](_, f)), stop.flatMap(_ => f(None)))
      case NextCursor(items, rest, stop) =>
        NextCursor(items, rest.map(doOnFinish[F, A](_, f)), stop.flatMap(_ => f(None)))
      case NextBatch(items, rest, stop) =>
        NextBatch(items, rest.map(doOnFinish[F, A](_, f)), stop.flatMap(_ => f(None)))
      case Suspend(rest, stop) =>
        Suspend(rest.map(doOnFinish[F, A](_, f)), stop.flatMap(_ => f(None)))
      case last @ Last(_) =>
        val ref = f(None)
        Suspend[F,A](F.map(ref)(_ => last), ref)
      case halt @ Halt(ex) =>
        val ref = f(ex)
        Suspend[F,A](F.map(ref)(_ => halt), ref)
    } catch {
      case ex if NonFatal(ex) => signalError(source, ex)
    }
  }
}
