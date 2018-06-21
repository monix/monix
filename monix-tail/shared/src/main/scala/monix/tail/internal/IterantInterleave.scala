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

import cats.Applicative
import cats.effect.Sync
import cats.syntax.all._
import monix.execution.internal.Platform
import monix.execution.internal.collection.ArrayStack
import monix.tail.Iterant
import monix.tail.Iterant.{Concat, Halt, Last, Next, NextBatch, NextCursor, Scope, Suspend}
import monix.tail.batches.{Batch, BatchCursor}
import scala.collection.mutable.ArrayBuffer


private[tail] object IterantInterleave {
  def apply[F[_], A](l: Iterant[F, A], r: Iterant[F, A]) (implicit F: Sync[F]): Iterant[F, A] =
    Suspend(F.delay(new Loop().apply(l, r)))

  private final class Loop[F[_], A](implicit F: Sync[F], A: Applicative[F])
    extends ((Iterant[F, A], Iterant[F, A]) => Iterant[F, A]) { loop =>

    def apply(lh: Iterant[F, A], rh: Iterant[F, A]): Iterant[F, A] =
      lhLoop.visit(lh, rh)

    //-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=
    // Used by Concat:

    private[this] var _lhStack: ArrayStack[F[Iterant[F, A]]] = _
    private[this] var _rhStack: ArrayStack[F[Iterant[F, A]]] = _

    private def lhStackPush(ref: F[Iterant[F, A]]): Unit = {
      if (_lhStack == null) _lhStack = new ArrayStack()
      _lhStack.push(ref)
    }

    private def lhStackPop(): F[Iterant[F, A]] =
      if (_lhStack == null) null.asInstanceOf[F[Iterant[F, A]]]
      else _lhStack.pop()

    private def rhStackPush(ref: F[Iterant[F, A]]): Unit = {
      if (_rhStack == null) _rhStack = new ArrayStack()
      _rhStack.push(ref)
    }

    private def rhStackPop(): F[Iterant[F, A]] =
      if (_rhStack == null) null.asInstanceOf[F[Iterant[F, A]]]
      else _rhStack.pop()

    //-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=

    private[this] val lhLoop = new LHLoop

    private[this] var _rhNextLoop: RHNextLoop = _
    private def rhNextLoop = {
      if (_rhNextLoop == null) _rhNextLoop = new RHNextLoop
      _rhNextLoop
    }

    private[this] var _rhNextCursorLoop: RHNextCursorLoop = _
    private def rhNextCursorLoop = {
      if (_rhNextCursorLoop == null) _rhNextCursorLoop = new RHNextCursorLoop
      _rhNextCursorLoop
    }

    private[this] var _rhSuspendLoop: RHSuspendLoop = _
    private def rhSuspendLoop = {
      if (_rhSuspendLoop == null) _rhSuspendLoop = new RHSuspendLoop
      _rhSuspendLoop
    }

    private[this] var _rhLastLoop: RHLastLoop = _
    private def rhLastLoop = {
      if (_rhLastLoop == null) _rhLastLoop = new RHLastLoop
      _rhLastLoop
    }

    //-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=

    private final class LHLoop extends Iterant.Visitor[F, A, Iterant[F, A]] {
      protected var rhRef: Iterant[F, A] = _

      def withRh(ref: Iterant[F, A]): LHLoop = {
        rhRef = ref
        this
      }

      def visit(lh: Iterant[F, A], rh: Iterant[F, A]): Iterant[F, A] = {
        rhRef = rh
        apply(lh)
      }

      def visit(ref: Next[F, A]): Iterant[F, A] =
        rhNextLoop.visit(ref, rhRef)

      def visit(ref: NextBatch[F, A]): Iterant[F, A] =
        rhNextCursorLoop.visit(ref.toNextCursor(), rhRef)

      def visit(ref: NextCursor[F, A]): Iterant[F, A] =
        rhNextCursorLoop.visit(ref, rhRef)

      def visit(ref: Suspend[F, A]): Iterant[F, A] =
        rhSuspendLoop.visit(ref, rhRef)

      def visit(ref: Concat[F, A]): Iterant[F, A] = {
        lhStackPush(ref.rh)
        rhSuspendLoop.visit(Suspend(ref.lh), rhRef)
      }

      def visit[S](ref: Scope[F, S, A]): Iterant[F, A] =
        ref.runMap(this)

      def visit(ref: Last[F, A]): Iterant[F, A] =
        lhStackPop() match {
          case null =>
            rhLastLoop.visit(ref, rhRef)
          case rest =>
            rhNextLoop.visit(Next(ref.item, rest), rhRef)
        }

      def visit(ref: Halt[F, A]): Iterant[F, A] =
        lhStackPop() match {
          case null =>
            rhRef match {
              case haltB @ Halt(eb) =>
                ref.e match {
                  case None => haltB.asInstanceOf[Iterant[F, A]]
                  case Some(ea) =>
                    Halt(Some(eb.fold(ea)(Platform.composeErrors(ea, _))))
                }
              case _ =>
                ref.asInstanceOf[Iterant[F, A]]
            }
          case xs =>
            rhSuspendLoop.visit(Suspend(xs), rhRef)
        }

      def fail(e: Throwable): Iterant[F, A] =
        Iterant.raiseError(e)
    }

    private abstract class RHBaseLoop[LH <: Iterant[F, A]]
      extends Iterant.Visitor[F, A, Iterant[F, A]] {

      protected var lhRef: LH = _

      def visit(lh: LH, rh: Iterant[F, A]): Iterant[F, A] = {
        lhRef = lh
        apply(rh)
      }
    }

    private final class RHNextLoop extends RHBaseLoop[Next[F, A]] {
      def visit(ref: Next[F, A]): Iterant[F, A] =
        processPair(lhRef.item, lhRef.rest, ref.item, ref.rest)

      def visit(ref: NextBatch[F, A]): Iterant[F, A] =
        processOneASeqB(lhRef, lhRef.item, lhRef.rest, ref.toNextCursor(), this)

      def visit(ref: NextCursor[F, A]): Iterant[F, A] =
        processOneASeqB(lhRef, lhRef.item, lhRef.rest, ref, this)

      def visit(ref: Suspend[F, A]): Iterant[F, A] =
        Suspend(ref.rest.map(this))

      def visit(ref: Concat[F, A]): Iterant[F, A] = {
        rhStackPush(ref.rh)
        Suspend(ref.lh.map(this))
      }

      def visit[S](ref: Scope[F, S, A]): Iterant[F, A] =
        ref.runMap(this)

      def visit(ref: Last[F, A]): Iterant[F, A] =
        rhStackPop() match {
          case null =>
            Next(lhRef.item, F.pure(ref))
          case rest =>
            processPair(lhRef.item, lhRef.rest, ref.item, rest)
        }

      def visit(ref: Halt[F, A]): Iterant[F, A] =
        rhStackPop() match {
          case null => ref.asInstanceOf[Iterant[F, A]]
          case rest => Suspend(rest.map(this))
        }

      def fail(e: Throwable): Iterant[F, A] =
        Iterant.raiseError(e)
    }

    private final class RHNextCursorLoop extends RHBaseLoop[NextCursor[F, A]] {
      def visit(ref: Next[F, A]): Iterant[F, A] =
        processSeqAOneB(lhRef, ref, ref.item, ref.rest)

      def visit(ref: NextBatch[F, A]): Iterant[F, A] =
        visit(ref.toNextCursor())

      def visit(ref: NextCursor[F, A]): Iterant[F, A] =
        processSeqASeqB(lhRef, ref)

      def visit(ref: Suspend[F, A]): Iterant[F, A] =
        Suspend(ref.rest.map(this))

      def visit(ref: Concat[F, A]): Iterant[F, A] = {
        rhStackPush(ref.rh)
        Suspend(ref.lh.map(this))
      }

      def visit[S](ref: Scope[F, S, A]): Iterant[F, A] =
        ref.runMap(this)

      def visit(ref: Last[F, A]): Iterant[F, A] = {
        rhStackPop() match {
          case null =>
            val NextCursor(itemsA, restA) = lhRef
            if (!itemsA.hasNext)
              Suspend(restA.map(lhLoop))
            else {
              val a = itemsA.next()
              Next(a, F.pure(ref))
            }
          case xs =>
            visit(Next(ref.item, xs))
        }
      }

      def visit(ref: Halt[F, A]): Iterant[F, A] =
        ref.e match {
          case None =>
            rhStackPop() match {
              case null => ref.asInstanceOf[Iterant[F, A]]
              case rest => Suspend(rest.map(this))
            }
          case _ =>
            ref.asInstanceOf[Iterant[F, A]]
        }

      def fail(e: Throwable): Iterant[F, A] =
        Iterant.raiseError(e)
    }

    private final class RHSuspendLoop extends RHBaseLoop[Suspend[F, A]] {
      def visit(ref: Next[F, A]): Iterant[F, A] =
        Suspend(lhRef.rest.map(lhLoop))

      def visit(ref: NextBatch[F, A]): Iterant[F, A] =
        Suspend(lhRef.rest.map(lhLoop))

      def visit(ref: NextCursor[F, A]): Iterant[F, A] =
        Suspend(lhRef.rest.map(lhLoop))

      def visit(ref: Suspend[F, A]): Iterant[F, A] =
        Suspend(A.map2(lhRef.rest, ref.rest)(loop))

      def visit(ref: Concat[F, A]): Iterant[F, A] = {
        rhStackPush(ref.rh)
        Suspend(A.map2(lhRef.rest, ref.lh)(loop))
      }

      def visit[S](ref: Scope[F, S, A]): Iterant[F, A] =
        ref.runMap(this)

      def visit(ref: Last[F, A]): Iterant[F, A] =
        Suspend(lhRef.rest.map(lhLoop))

      def visit(ref: Halt[F, A]): Iterant[F, A] =
        ref.e match {
          case None =>
            rhStackPop() match {
              case null => ref.asInstanceOf[Iterant[F, A]]
              case xs => Suspend(A.map2(lhRef.rest, xs)(loop))
            }
          case _ =>
            ref.asInstanceOf[Iterant[F, A]]
        }

      def fail(e: Throwable): Iterant[F, A] =
        Iterant.raiseError(e)
    }

    private final class RHLastLoop extends RHBaseLoop[Last[F, A]] {
      def visit(ref: Next[F, A]): Iterant[F, A] =
        Next(lhRef.item, F.pure(Last(ref.item)))

      def visit(ref: NextBatch[F, A]): Iterant[F, A] =
        processLastASeqB(lhRef, ref.batch.cursor(), ref.rest, this)

      def visit(ref: NextCursor[F, A]): Iterant[F, A] =
        processLastASeqB(lhRef, ref.cursor, ref.rest, this)

      def visit(ref: Suspend[F, A]): Iterant[F, A] =
        Suspend(ref.rest.map(this))

      def visit(ref: Concat[F, A]): Iterant[F, A] = {
        rhStackPush(ref.rh)
        Suspend(ref.lh.map(this))
      }

      def visit[S](ref: Scope[F, S, A]): Iterant[F, A] =
        ref.runMap(this)

      def visit(ref: Last[F, A]): Iterant[F, A] =
        Next(lhRef.item, F.pure(ref))

      def visit(ref: Halt[F, A]): Iterant[F, A] =
        ref.e match {
          case None =>
            rhStackPop() match {
              case null => ref.asInstanceOf[Iterant[F, A]]
              case xs => Suspend(xs.map(this))
            }
          case _ =>
            ref.asInstanceOf[Iterant[F, A]]
        }

      def fail(e: Throwable): Iterant[F, A] =
        Iterant.raiseError(e)
    }

    //-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=

    def processPair(a: A, restA: F[Iterant[F, A]], b: A, restB: F[Iterant[F, A]]) = {
      val rest = A.map2(restA, restB)(loop)
      NextBatch(Batch(a, b), rest)
    }

    def processOneASeqB(
      lh: Iterant[F, A],
      a: A,
      restA: F[Iterant[F, A]],
      refB: NextCursor[F, A],
      loop: Iterant.Visitor[F, A, Iterant[F, A]]): Iterant[F, A] = {

      val NextCursor(itemsB, restB) = refB
      if (!itemsB.hasNext)
        Suspend(restB.map(loop))
      else
        processPair(a, restA, itemsB.next(), F.pure(refB))
    }

    def processSeqAOneB(
      refA: NextCursor[F, A],
      rh: Iterant[F, A],
      b: A,
      restB: F[Iterant[F, A]]): Iterant[F, A] = {

      val NextCursor(itemsA, restA) = refA
      if (!itemsA.hasNext)
        Suspend(restA.map(lhLoop.withRh(rh)))
      else
        processPair(itemsA.next(), F.pure(refA), b, restB)
    }

    def processLastASeqB(
      refA: Last[F, A],
      itemsB: BatchCursor[A],
      restB: F[Iterant[F, A]],
      loop: Iterant.Visitor[F, A, Iterant[F, A]]): Iterant[F, A] = {

      if (!itemsB.hasNext())
        Suspend(restB.map(loop))
      else {
        NextBatch(Batch(refA.item, itemsB.next()), F.pure(Iterant.empty))
      }
    }

    def processSeqASeqB(refA: NextCursor[F, A], refB: NextCursor[F, A]): Iterant[F, A] = {
      val NextCursor(itemsA, restA) = refA
      val NextCursor(itemsB, restB) = refB

      // Processing multiple batch at once, but only if the iterators
      // aren't infinite, otherwise we have to process them lazily
      val batchSize = math.min(itemsA.recommendedBatchSize, itemsB.recommendedBatchSize)
      if (batchSize > 1) {
        val buffer = ArrayBuffer.empty[A]
        var toFetch = batchSize

        while (toFetch > 0 && itemsA.hasNext() && itemsB.hasNext()) {
          buffer += itemsA.next()
          buffer += itemsB.next()
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
              Batch.fromArray(array).asInstanceOf[Batch[A]],
              A.map2(restA, restB)(loop))
        }
        else if (isEmptyItemsA) {
          if (array.isEmpty)
            Suspend(restA.map(lhLoop.withRh(refB)))
          else
            NextBatch(
              Batch.fromArray(array).asInstanceOf[Batch[A]],
              restA.map(lhLoop.withRh(refB)))
        }
        else if (isEmptyItemsB) {
          if (array.isEmpty)
            Suspend(restB.map(loop(refA, _)))
          else
            NextBatch(
              Batch.fromArray(array).asInstanceOf[Batch[A]],
              restB.map(loop(refA, _)))
        }
        else {
          // We are not done, continue loop
          NextBatch(Batch.fromArray(array).asInstanceOf[Batch[A]], F.delay(loop(refA, refB)))
        }
      }
      else if (!itemsA.hasNext)
        Suspend(restA.map(lhLoop.withRh(refB)))
      else if (!itemsB.hasNext)
        Suspend(restB.map(loop(refA, _)))
      else {
        val a = itemsA.next()
        val b = itemsB.next()
        NextBatch(Batch(a, b), F.delay(loop(refA, refB)))
      }
    }
  }
}
