/*
 * Copyright (c) 2014-2016 by its authors. Some rights reserved.
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

package monix.streams.internal.builders

import monix.execution.cancelables.BooleanCancelable
import monix.execution.{Cancelable, Ack}
import monix.execution.Ack.{Cancel, Continue}
import monix.streams.Observable
import monix.streams.observers.Subscriber

import scala.annotation.tailrec
import scala.util.control.NonFatal
import scala.util.{Failure, Try}

private[streams] final
class StateActionObservable[S,A](seed: S, f: S => (A,S)) extends Observable[A] {

  def unsafeSubscribeFn(subscriber: Subscriber[A]): Cancelable = {
    val cancelable = BooleanCancelable()
    new StateRunLoop(subscriber, cancelable, seed, f).run()
    cancelable
  }

  private[this]
  final class StateRunLoop(o: Subscriber[A], c: BooleanCancelable, initialSeed: S, f: S => (A,S))
    extends Runnable { self =>

    import o.{scheduler => s}
    private[this] var seed = initialSeed
    private[this] val modulus = s.batchedExecutionModulus

    private[this] val asyncReschedule: Try[Ack] => Unit = {
      case Continue.AsSuccess =>
        self.run()
      case Failure(ex) =>
        o.onError(ex)
      case _ =>
        () // do nothing else
    }

    @tailrec
    def fastLoop(syncIndex: Int): Unit = {
      val ack = try {
        val (nextA, newState) = f(seed)
        this.seed = newState
        o.onNext(nextA)
      } catch {
        case NonFatal(ex) =>
          o.onError(ex)
          Cancel
      }

      val nextIndex =
        if (ack == Continue) (syncIndex + 1) & modulus
        else if (ack == Cancel) -1
        else 0

      if (nextIndex > 0)
        fastLoop(nextIndex)
      else if (nextIndex == 0 && !c.isCanceled)
        ack.onComplete(asyncReschedule)
    }

    def run(): Unit =
      try fastLoop(0) catch {
        case NonFatal(ex) =>
          s.reportFailure(ex)
      }
  }
}
