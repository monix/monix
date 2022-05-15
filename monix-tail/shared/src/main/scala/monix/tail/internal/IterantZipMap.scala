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

package monix.tail.internal

import cats.effect.Sync
import cats.syntax.all._
import cats.{ Applicative, Parallel }
import monix.execution.internal.Platform
import monix.catnap.internal.ParallelApplicative
import monix.execution.internal.collection.ChunkedArrayStack
import monix.tail.Iterant
import monix.tail.Iterant.{ Concat, Halt, Last, Next, NextBatch, NextCursor, Scope, Suspend }
import monix.tail.batches.{ Batch, BatchCursor }

import scala.collection.mutable.ArrayBuffer

private[tail] object IterantZipMap {
  /**
    * Implementation for `Iterant#zipMap`
    */
  def seq[F[_], A, B, C](lh: Iterant[F, A], rh: Iterant[F, B], f: (A, B) => C)(implicit F: Sync[F]): Iterant[F, C] = {

    Suspend(F.delay(new Loop[F, A, B, C](f)(F, F).apply(lh, rh)))
  }

  /**
    * Implementation for `Iterant#parZipMap`
    */
  def par[F[_], G[_], A, B, C](lh: Iterant[F, A], rh: Iterant[F, B], f: (A, B) => C)(
    implicit
    F: Sync[F],
    P: Parallel[F]
  ): Iterant[F, C] = {

    val A = ParallelApplicative(P)
    Suspend(F.delay(new Loop[F, A, B, C](f)(F, A).apply(lh, rh)))
  }

  private final class Loop[F[_], A, B, C](f: (A, B) => C)(implicit F: Sync[F], A: Applicative[F])
    extends ((Iterant[F, A], Iterant[F, B]) => Iterant[F, C]) { loop =>

    def apply(lh: Iterant[F, A], rh: Iterant[F, B]): Iterant[F, C] =
      lhLoop.visit(lh, rh)

    // -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=
    // Used by Concat:

    private[this] var _lhStack: ChunkedArrayStack[F[Iterant[F, A]]] = _
    private[this] var _rhStack: ChunkedArrayStack[F[Iterant[F, B]]] = _

    private def lhStackPush(ref: F[Iterant[F, A]]): Unit = {
      if (_lhStack == null) _lhStack = ChunkedArrayStack()
      _lhStack.push(ref)
    }

    private def lhStackPop(): F[Iterant[F, A]] =
      if (_lhStack == null) null.asInstanceOf[F[Iterant[F, A]]]
      else _lhStack.pop()

    private def rhStackPush(ref: F[Iterant[F, B]]): Unit = {
      if (_rhStack == null) _rhStack = ChunkedArrayStack()
      _rhStack.push(ref)
    }

    private def rhStackPop(): F[Iterant[F, B]] =
      if (_rhStack == null) null.asInstanceOf[F[Iterant[F, B]]]
      else _rhStack.pop()

    // -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=

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

    // -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=

    private final class LHLoop extends Iterant.Visitor[F, A, Iterant[F, C]] {
      protected var rhRef: Iterant[F, B] = _

      def withRh(ref: Iterant[F, B]): LHLoop = {
        rhRef = ref
        this
      }

      def visit(lh: Iterant[F, A], rh: Iterant[F, B]): Iterant[F, C] = {
        rhRef = rh
        this.apply(lh)
      }

      def visit(ref: Next[F, A]): Iterant[F, C] =
        rhNextLoop.visit(ref, rhRef)

      def visit(ref: NextBatch[F, A]): Iterant[F, C] =
        rhNextCursorLoop.visit(ref.toNextCursor(), rhRef)

      def visit(ref: NextCursor[F, A]): Iterant[F, C] =
        rhNextCursorLoop.visit(ref, rhRef)

      def visit(ref: Suspend[F, A]): Iterant[F, C] =
        rhSuspendLoop.visit(ref, rhRef)

      def visit(ref: Concat[F, A]): Iterant[F, C] = {
        lhStackPush(ref.rh)
        rhSuspendLoop.visit(Suspend(ref.lh), rhRef)
      }

      def visit[S](ref: Scope[F, S, A]): Iterant[F, C] =
        ref.runMap(this)

      def visit(ref: Last[F, A]): Iterant[F, C] =
        lhStackPop() match {
          case null =>
            rhLastLoop.visit(ref, rhRef)
          case rest =>
            rhNextLoop.visit(Next(ref.item, rest), rhRef)
        }

      def visit(ref: Halt[F, A]): Iterant[F, C] =
        lhStackPop() match {
          case null =>
            rhRef match {
              case haltB @ Halt(eb) =>
                ref.e match {
                  case None => haltB.asInstanceOf[Iterant[F, C]]
                  case Some(ea) =>
                    Halt(Some(eb.fold(ea)(Platform.composeErrors(ea, _))))
                }
              case _ =>
                ref.asInstanceOf[Iterant[F, C]]
            }
          case xs =>
            rhSuspendLoop.visit(Suspend(xs), rhRef)
        }

      def fail(e: Throwable): Iterant[F, C] =
        Iterant.raiseError(e)
    }

    private abstract class RHBaseLoop[LH <: Iterant[F, A]] extends Iterant.Visitor[F, B, Iterant[F, C]] {

      protected var lhRef: LH = _

      def visit(lh: LH, rh: Iterant[F, B]): Iterant[F, C] = {
        lhRef = lh
        this.apply(rh)
      }
    }

    private final class RHNextLoop extends RHBaseLoop[Next[F, A]] {
      def visit(ref: Next[F, B]): Iterant[F, C] =
        processPair(lhRef.item, lhRef.rest, ref.item, ref.rest)

      def visit(ref: NextBatch[F, B]): Iterant[F, C] =
        processOneASeqB(lhRef, lhRef.item, lhRef.rest, ref.toNextCursor(), this)

      def visit(ref: NextCursor[F, B]): Iterant[F, C] =
        processOneASeqB(lhRef, lhRef.item, lhRef.rest, ref, this)

      def visit(ref: Suspend[F, B]): Iterant[F, C] =
        Suspend(ref.rest.map(this))

      def visit(ref: Concat[F, B]): Iterant[F, C] = {
        rhStackPush(ref.rh)
        Suspend(ref.lh.map(this))
      }

      def visit[S](ref: Scope[F, S, B]): Iterant[F, C] =
        ref.runMap(this)

      def visit(ref: Last[F, B]): Iterant[F, C] =
        rhStackPop() match {
          case null =>
            Last[F, C](f(lhRef.item, ref.item))
          case rest =>
            processPair(lhRef.item, lhRef.rest, ref.item, rest)
        }

      def visit(ref: Halt[F, B]): Iterant[F, C] =
        rhStackPop() match {
          case null => ref.asInstanceOf[Iterant[F, C]]
          case rest => Suspend(rest.map(this))
        }

      def fail(e: Throwable): Iterant[F, C] =
        Iterant.raiseError(e)
    }

    private final class RHNextCursorLoop extends RHBaseLoop[NextCursor[F, A]] {
      def visit(ref: Next[F, B]): Iterant[F, C] =
        processSeqAOneB(lhRef, ref, ref.item, ref.rest)

      def visit(ref: NextBatch[F, B]): Iterant[F, C] =
        visit(ref.toNextCursor())

      def visit(ref: NextCursor[F, B]): Iterant[F, C] =
        processSeqASeqB(lhRef, ref)

      def visit(ref: Suspend[F, B]): Iterant[F, C] =
        Suspend(ref.rest.map(this))

      def visit(ref: Concat[F, B]): Iterant[F, C] = {
        rhStackPush(ref.rh)
        Suspend(ref.lh.map(this))
      }

      def visit[S](ref: Scope[F, S, B]): Iterant[F, C] =
        ref.runMap(this)

      def visit(ref: Last[F, B]): Iterant[F, C] = {
        rhStackPop() match {
          case null =>
            val NextCursor(itemsA, restA) = lhRef
            if (!itemsA.hasNext())
              Suspend(restA.map(lhLoop))
            else {
              val a = itemsA.next()
              Last[F, C](f(a, ref.item))
            }
          case xs =>
            visit(Next(ref.item, xs))
        }
      }

      def visit(ref: Halt[F, B]): Iterant[F, C] =
        ref.e match {
          case None =>
            rhStackPop() match {
              case null => ref.asInstanceOf[Iterant[F, C]]
              case rest => Suspend(rest.map(this))
            }
          case _ =>
            ref.asInstanceOf[Iterant[F, C]]
        }

      def fail(e: Throwable): Iterant[F, C] =
        Iterant.raiseError(e)
    }

    private final class RHSuspendLoop extends RHBaseLoop[Suspend[F, A]] {
      def visit(ref: Next[F, B]): Iterant[F, C] =
        Suspend(lhRef.rest.map(lhLoop))

      def visit(ref: NextBatch[F, B]): Iterant[F, C] =
        Suspend(lhRef.rest.map(lhLoop))

      def visit(ref: NextCursor[F, B]): Iterant[F, C] =
        Suspend(lhRef.rest.map(lhLoop))

      def visit(ref: Suspend[F, B]): Iterant[F, C] =
        Suspend(A.map2(lhRef.rest, ref.rest)(loop))

      def visit(ref: Concat[F, B]): Iterant[F, C] = {
        rhStackPush(ref.rh)
        Suspend(A.map2(lhRef.rest, ref.lh)(loop))
      }

      def visit[S](ref: Scope[F, S, B]): Iterant[F, C] =
        ref.runMap(this)

      def visit(ref: Last[F, B]): Iterant[F, C] =
        Suspend(lhRef.rest.map(lhLoop))

      def visit(ref: Halt[F, B]): Iterant[F, C] =
        ref.e match {
          case None =>
            rhStackPop() match {
              case null => ref.asInstanceOf[Iterant[F, C]]
              case xs => Suspend(A.map2(lhRef.rest, xs)(loop))
            }
          case _ =>
            ref.asInstanceOf[Iterant[F, C]]
        }

      def fail(e: Throwable): Iterant[F, C] =
        Iterant.raiseError(e)
    }

    private final class RHLastLoop extends RHBaseLoop[Last[F, A]] {
      def visit(ref: Next[F, B]): Iterant[F, C] =
        Last(f(lhRef.item, ref.item))

      def visit(ref: NextBatch[F, B]): Iterant[F, C] =
        processLastASeqB(lhRef, ref.batch.cursor(), ref.rest, this)

      def visit(ref: NextCursor[F, B]): Iterant[F, C] =
        processLastASeqB(lhRef, ref.cursor, ref.rest, this)

      def visit(ref: Suspend[F, B]): Iterant[F, C] =
        Suspend(ref.rest.map(this))

      def visit(ref: Concat[F, B]): Iterant[F, C] = {
        rhStackPush(ref.rh)
        Suspend(ref.lh.map(this))
      }

      def visit[S](ref: Scope[F, S, B]): Iterant[F, C] =
        ref.runMap(this)

      def visit(ref: Last[F, B]): Iterant[F, C] =
        Last(f(lhRef.item, ref.item))

      def visit(ref: Halt[F, B]): Iterant[F, C] =
        ref.e match {
          case None =>
            rhStackPop() match {
              case null => ref.asInstanceOf[Iterant[F, C]]
              case xs => Suspend(xs.map(this))
            }
          case _ =>
            ref.asInstanceOf[Iterant[F, C]]
        }

      def fail(e: Throwable): Iterant[F, C] =
        Iterant.raiseError(e)
    }

    // -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=

    def processPair(a: A, restA: F[Iterant[F, A]], b: B, restB: F[Iterant[F, B]]) = {
      val rest = A.map2(restA, restB)(loop)
      Next(f(a, b), rest)
    }

    def processOneASeqB(
      lh: Iterant[F, A],
      a: A,
      restA: F[Iterant[F, A]],
      refB: NextCursor[F, B],
      loop: Iterant.Visitor[F, B, Iterant[F, C]]
    ): Iterant[F, C] = {

      val NextCursor(itemsB, restB) = refB
      if (!itemsB.hasNext())
        Suspend(restB.map(loop))
      else
        processPair(a, restA, itemsB.next(), F.pure(refB))
    }

    def processSeqAOneB(refA: NextCursor[F, A], rh: Iterant[F, B], b: B, restB: F[Iterant[F, B]]): Iterant[F, C] = {

      val NextCursor(itemsA, restA) = refA
      if (!itemsA.hasNext())
        Suspend(restA.map(lhLoop.withRh(rh)))
      else
        processPair(itemsA.next(), F.pure(refA), b, restB)
    }

    def processLastASeqB(
      refA: Last[F, A],
      itemsB: BatchCursor[B],
      restB: F[Iterant[F, B]],
      loop: Iterant.Visitor[F, B, Iterant[F, C]]
    ): Iterant[F, C] = {

      if (!itemsB.hasNext())
        Suspend(restB.map(loop))
      else {
        Last[F, C](f(refA.item, itemsB.next()))
      }
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
            NextBatch(Batch.fromArray(array).asInstanceOf[Batch[C]], A.map2(restA, restB)(loop))
        } else if (isEmptyItemsA) {
          if (array.isEmpty)
            Suspend(restA.map(lhLoop.withRh(refB)))
          else
            NextBatch(Batch.fromArray(array).asInstanceOf[Batch[C]], restA.map(lhLoop.withRh(refB)))
        } else if (isEmptyItemsB) {
          if (array.isEmpty)
            Suspend(restB.map(loop(refA, _)))
          else
            NextBatch(Batch.fromArray(array).asInstanceOf[Batch[C]], restB.map(loop(refA, _)))
        } else {
          // We are not done, continue loop
          NextBatch(Batch.fromArray(array).asInstanceOf[Batch[C]], F.delay(loop(refA, refB)))
        }
      } else if (!itemsA.hasNext())
        Suspend(restA.map(lhLoop.withRh(refB)))
      else if (!itemsB.hasNext())
        Suspend(restB.map(loop(refA, _)))
      else {
        val a = itemsA.next()
        val b = itemsB.next()
        Next(f(a, b), F.delay(loop(refA, refB)))
      }
    }
  }
}
