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

package monix.eval.internal

import monix.eval.{Iterant, Task}
import monix.eval.Iterant._
import scala.collection.mutable.ArrayBuffer
import scala.util.control.NonFatal

private[eval] object IterantZipMap {
  /** Implementation for `Iterant#zipMap` */
  def apply[A, B, C](lh: Iterant[A], rh: Iterant[B])(f: (A, B) => C): Iterant[C] = {
    def loop(lh: Iterant[A], rh: Iterant[B]): Iterant[C] = {
      @inline
      def stopBoth(stopA: Task[Unit], stopB: Task[Unit]): Task[Unit] =
        stopA.flatMap(_ => stopB)
      
      @inline
      def processPair(a: A, restA: Task[Iterant[A]], stopA: Task[Unit], b: B, restB: Task[Iterant[B]], stopB: Task[Unit]) = {
        val rest = Task.zipMap2(restA, restB)(loop)
        Next(f(a, b), rest, stopBoth(stopA, stopB))
      }

      @inline
      def processOneASeqB(lh: Iterant[A], a: A, restA: Task[Iterant[A]], stopA: Task[Unit], refB: NextSeq[B]): Iterant[C] = {
        val NextSeq(itemsB, restB, stopB) = refB
        if (!itemsB.hasNext)
          Suspend(restB.map(loop(lh, _)), stopBoth(stopA, stopB))
        else
          processPair(a, restA, stopA, itemsB.next(), Task.now(refB), stopB)
      }

      @inline
      def processSeqAOneB(refA: NextSeq[A], rh: Iterant[B], b: B, restB: Task[Iterant[B]], stopB: Task[Unit]): Iterant[C] = {
        val NextSeq(itemsA, restA, stopA) = refA
        if (!itemsA.hasNext)
          Suspend(restA.map(loop(_, rh)), stopBoth(stopA, stopB))
        else
          processPair(itemsA.next(), Task.now(refA), stopA, b, restB, stopB)
      }

      @inline
      def processSeqASeqB(refA: NextSeq[A], refB: NextSeq[B]): Iterant[C] = {
        val NextSeq(itemsA, restA, stopA) = refA
        val NextSeq(itemsB, restB, stopB) = refB

        val buffer = ArrayBuffer.empty[C]
        while (itemsA.hasNext && itemsB.hasNext)
          buffer += f(itemsA.next(), itemsB.next())

        if (itemsA.isEmpty && itemsB.isEmpty) {
          if (buffer.isEmpty)
            Suspend(Task.zipMap2(restA, restB)(loop), stopBoth(stopA, stopB))
          else
            NextGen(buffer, Task.zipMap2(restA, restB)(loop), stopBoth(stopA, stopB))
        }
        else if (itemsA.isEmpty) {
          if (buffer.isEmpty)
            Suspend(restA.map(loop(_, refB)), stopBoth(stopA, stopB))
          else
            NextGen(buffer, restA.map(loop(_, refB)), stopBoth(stopA, stopB))
        }
        else { // if itemsB.isEmpty
          if (buffer.isEmpty)
            Suspend(restB.map(loop(refA, _)), stopBoth(stopA, stopB))
          else
            NextGen(buffer, restB.map(loop(refA, _)), stopBoth(stopA, stopB))
        }
      }

      @inline
      def processLast(a: A, b: B, stop: Task[Unit]) = {
        val last = Last(f(a, b))
        Suspend(stop.map(_ => last), stop)
      }

      @inline
      def processNextSeqA(lh: NextSeq[A], rh: Iterant[B]): Iterant[C] =
        rh match {
          case Next(b, restB, stopB) =>
            processSeqAOneB(lh, rh, b, restB, stopB)
          case refB @ NextSeq(_, _, _) =>
            processSeqASeqB(lh, refB)
          case NextGen(itemsB, restB, stopB) =>
            val seqB = NextSeq(itemsB.iterator, restB, stopB)
            processSeqASeqB(lh, seqB)
          case Suspend(restB, stopB) =>
            Suspend(restB.map(loop(lh, _)), stopBoth(lh.earlyStop, stopB))
          case Last(b) =>
            val NextSeq(itemsA, restA, stopA) = lh
            if (!itemsA.hasNext)
              Suspend(restA.map(loop(_, rh)), stopA)
            else {
              val a = itemsA.next()
              processLast(a, b, stopA)
            }
          case halt @ Halt(_) =>
            Suspend(lh.earlyStop.map(_ => halt), lh.earlyStop)
        }

      def processLastASeqB(a: A, itemsB: Iterator[B], restB: Task[Iterant[B]], stopB: Task[Unit]) = {
        if (!itemsB.hasNext)
          Suspend(restB.map(loop(lh, _)), stopB)
        else {
          val last = Last(f(a, itemsB.next()))
          Suspend(stopB.map(_ => last), stopB)
        }
      }

      try lh match {
        case Next(a, restA, stopA) =>
          rh match {
            case Next(b, restB, stopB) =>
              processPair(a, restA, stopA, b, restB, stopB)
            case refB @ NextSeq(itemsB, restB, stopB) =>
              processOneASeqB(lh, a, restA, stopB, refB)
            case NextGen(itemsB, restB, stopB) =>
              val seq = NextSeq(itemsB.iterator, restB, stopB)
              processOneASeqB(lh, a, restA, stopB, seq)
            case Suspend(restB, stopB) =>
              Suspend(restB.map(loop(lh, _)), stopBoth(stopA, stopB))
            case Last(b) =>
              processLast(a, b, stopA)
            case halt @ Halt(_) =>
              Suspend(stopA.map(_ => halt), stopA)
          }

        case refA @ NextSeq(_, _, _) =>
          processNextSeqA(refA, rh)

        case NextGen(itemsA, restA, stopA) =>
          val seq = NextSeq(itemsA.iterator, restA, stopA)
          processNextSeqA(seq, rh)

        case Suspend(restA, stopA) =>
          rh match {
            case halt @ Halt(_) =>
              Suspend(stopA.map(_ => halt), stopA)
            case Last(_) =>
              Suspend(restA.map(loop(_, rh)), stopA)
            case Suspend(restB, stopB) =>
              Suspend(Task.zipMap2(restA, restB)(loop), stopBoth(stopA, stopB))
            case _ =>
              Suspend(restA.map(loop(_, rh)), stopBoth(stopA, rh.earlyStop))
          }

        case Last(a) =>
          rh match {
            case Next(b, restB, stopB) =>
              processLast(a, b, stopB)
            case NextSeq(itemsB, restB, stopB) =>
              processLastASeqB(a, itemsB, restB, stopB)
            case NextGen(itemsB, restB, stopB) =>
              processLastASeqB(a, itemsB.iterator, restB, stopB)
            case Suspend(restB, stopB) =>
              Suspend(restB.map(loop(lh, _)), stopB)
            case Last(b) =>
              Last(f(a, b))
            case halt @ Halt(_) =>
              halt
          }

        case halt @ Halt(exA) =>
          rh match {
            case Halt(exB) => Halt(exA.orElse(exB))
            case Last(_) => halt
            case _ =>
              Suspend(rh.earlyStop.map(_ => halt), rh.earlyStop)
          }
      }
      catch {
        case NonFatal(ex) =>
          val stop = lh.earlyStop.flatMap(_ => rh.earlyStop)
          Suspend(stop.map(_ => Halt(Some(ex))), stop)
      }
    }

    // Given function can be side-effecting,
    // so we must suspend the execution
    val stop = lh.earlyStop.flatMap(_ => rh.earlyStop)
    Suspend(Task.eval(loop(lh, rh)), stop)
  }
}
