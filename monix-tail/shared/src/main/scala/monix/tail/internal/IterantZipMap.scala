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

import cats.effect.Sync
import cats.syntax.all._
import cats.{Applicative, Parallel}
import monix.eval.instances.ParallelApplicative
import scala.util.control.NonFatal

import monix.tail.Iterant
import monix.tail.Iterant.{Scope, Halt, Last, Next, NextBatch, NextCursor, Suspend}
import monix.tail.batches.{Batch, BatchCursor}
import scala.collection.mutable.ArrayBuffer

// TODO: test for Scope
private[tail] object IterantZipMap {
  /**
    * Implementation for `Iterant#zipMap`
    */
  def seq[F[_], A, B, C](lh: Iterant[F, A], rh: Iterant[F, B], f: (A, B) => C)
    (implicit F: Sync[F]): Iterant[F, C] =
    apply(lh, rh, f)(F, F)

  /**
    * Implementation for `Iterant#parZipMap`
    */
  def par[F[_], G[_], A, B, C](lh: Iterant[F, A], rh: Iterant[F, B], f: (A, B) => C)
    (implicit F: Sync[F], P: Parallel[F, G]): Iterant[F, C] = {

    val A = ParallelApplicative(P)
    apply(lh, rh, f)(F, A)
  }

  private def apply[F[_], A, B, C](lh: Iterant[F, A], rh: Iterant[F, B], f: (A, B) => C)
    (implicit F: Sync[F], A: Applicative[F]): Iterant[F, C] = {

    def loop(lh: Iterant[F, A], rh: Iterant[F, B]): Iterant[F, C] = {
      def processPair(a: A, restA: F[Iterant[F, A]],b: B, restB: F[Iterant[F, B]]) = {
        val rest = A.map2(restA, restB)(loop)
        Next(f(a, b), rest)
      }

      def processOneASeqB(lh: Iterant[F, A], a: A, restA: F[Iterant[F, A]], refB: NextCursor[F, B]): Iterant[F, C] = {
        val NextCursor(itemsB, restB) = refB
        if (!itemsB.hasNext)
          Suspend(restB.map(loop(lh, _)))
        else
          processPair(a, restA, itemsB.next(), F.pure(refB))
      }

      def processSeqAOneB(refA: NextCursor[F, A], rh: Iterant[F, B], b: B, restB: F[Iterant[F, B]]): Iterant[F, C] = {
        val NextCursor(itemsA, restA) = refA
        if (!itemsA.hasNext)
          Suspend(restA.map(loop(_, rh)))
        else
          processPair(itemsA.next(), F.pure(refA), b, restB)
      }

      def processSeqASeqB(refA: NextCursor[F, A], refB: NextCursor[F, B]): Iterant[F, C] = {
        val NextCursor(itemsA, restA) = refA
        val NextCursor(itemsB, restB) = refB

        // Processing multiple batch at once, but only if the iterators
        // aren't infinite, otherwise we have to process them lazily
        val batchSize = math.min(itemsA.recommendedBatchSize, itemsB.recommendedBatchSize)
        if (batchSize > 1) {
          val buffer = ArrayBuffer.empty[C]
          var toFetch = batchSize

          while (toFetch > 0 && itemsA.hasNext() && itemsB.hasNext()) {
            buffer += f(itemsA.next(), itemsB.next())
            toFetch -= 1
          }

          val isEmptyItemsA = !itemsA.hasNext()
          val isEmptyItemsB = !itemsB.hasNext()
          val array = buffer.toArray[Any]

          if (isEmptyItemsA && isEmptyItemsB) {
            if (array.isEmpty)
              Suspend(A.map2(restA, restB)(loop))
            else
              NextBatch(
                Batch.fromArray(array).asInstanceOf[Batch[C]],
                A.map2(restA, restB)(loop))
          }
          else if (isEmptyItemsA) {
            if (array.isEmpty)
              Suspend(restA.map(loop(_, refB)))
            else
              NextBatch(
                Batch.fromArray(array).asInstanceOf[Batch[C]],
                restA.map(loop(_, refB)))
          }
          else if (isEmptyItemsB) {
            if (array.isEmpty)
              Suspend(restB.map(loop(refA, _)))
            else
              NextBatch(
                Batch.fromArray(array).asInstanceOf[Batch[C]],
                restB.map(loop(refA, _)))
          }
          else {
            // We are not done, continue loop
            NextBatch(Batch.fromArray(array).asInstanceOf[Batch[C]], F.delay(loop(refA, refB)))
          }
        }
        else if (!itemsA.hasNext)
          Suspend(restA.map(loop(_, refB)))
        else if (!itemsB.hasNext)
          Suspend(restB.map(loop(refA, _)))
        else {
          val a = itemsA.next()
          val b = itemsB.next()
          Next(f(a, b), F.delay(loop(refA, refB)))
        }
      }

      def processNextCursorA(lh: NextCursor[F, A], rh: Iterant[F, B]): Iterant[F, C] =
        rh match {
          case b @ Scope(_, _, _) =>
            b.runMap(processNextCursorA(lh, _))
          case Next(b, restB) =>
            processSeqAOneB(lh, rh, b, restB)
          case refB @ NextCursor(_, _) =>
            processSeqASeqB(lh, refB)
          case NextBatch(itemsB, restB) =>
            val seqB = NextCursor(itemsB.cursor(), restB)
            processSeqASeqB(lh, seqB)
          case Suspend(restB) =>
            Suspend(restB.map(loop(lh, _)))
          case Last(b) =>
            val NextCursor(itemsA, restA) = lh
            if (!itemsA.hasNext)
              Suspend(restA.map(loop(_, rh)))
            else {
              val a = itemsA.next()
              Last[F, C](f(a, b))
            }
          case halt @ Halt(_) =>
            halt.asInstanceOf[Iterant[F, C]]
        }

      def processLastASeqB(a: A, itemsB: BatchCursor[B], restB: F[Iterant[F, B]]): Iterant[F, C] = {
        if (!itemsB.hasNext())
          Suspend(restB.map(loop(lh, _)))
        else {
          Last[F,C](f(a, itemsB.next()))
        }
      }

      try lh match {
        case s @ Scope(_, _, _) =>
          s.runMap(loop(_, rh))
        case Next(a, restA) =>
          rh match {
            case b @ Scope(_, _, _) =>
              b.runMap(loop(lh, _))
            case Next(b, restB) =>
              processPair(a, restA, b, restB)
            case refB @ NextCursor(_, _) =>
              processOneASeqB(lh, a, restA, refB)
            case NextBatch(itemsB, restB) =>
              val seq = NextCursor(itemsB.cursor(), restB)
              processOneASeqB(lh, a, restA, seq)
            case Suspend(restB) =>
              Suspend(restB.map(loop(lh, _)))
            case Last(b) =>
              Last[F, C](f(a, b))
            case halt @ Halt(_) =>
             halt.asInstanceOf[Iterant[F,C]]
          }

        case refA @ NextCursor(_, _) =>
          processNextCursorA(refA, rh)

        case NextBatch(itemsA, restA) =>
          val seq = NextCursor(itemsA.cursor(), restA)
          processNextCursorA(seq, rh)

        case Suspend(restA) =>
          rh match {
            case halt @ Halt(_) =>
              halt.asInstanceOf[Iterant[F, C]]
            case Last(_) =>
              Suspend(restA.map(loop(_, rh)))
            case Suspend(restB) =>
              Suspend(A.map2(restA, restB)(loop))
            case _ =>
              Suspend(restA.map(loop(_, rh)))
          }

        case Last(a) =>
          rh match {
            case s @ Scope(_, _, _) =>
              s.runMap(loop(lh, _))
            case Next(b, _) =>
              Last[F, C](f(a, b))
            case NextCursor(itemsB, restB) =>
              processLastASeqB(a, itemsB, restB)
            case NextBatch(itemsB, restB) =>
              processLastASeqB(a, itemsB.cursor(), restB)
            case Suspend(restB) =>
              Suspend(restB.map(loop(lh, _)))
            case Last(b) =>
              Last(f(a, b))
            case halt @ Halt(_) =>
              halt.asInstanceOf[Iterant[F, C]]
          }

        case halt @ Halt(exA) =>
          rh match {
            case Halt(exB) =>
              Halt(exA.orElse(exB))
            case Last(_) =>
              halt.asInstanceOf[Iterant[F, C]]
            case _ =>
              halt.asInstanceOf[Iterant[F, C]]
          }
      } catch {
        case ex if NonFatal(ex) =>
          Halt(Some(ex))
      }
    }

    // Given function can be side-effecting, must suspend!
    Suspend(F.delay(loop(lh, rh)))
  }
}
