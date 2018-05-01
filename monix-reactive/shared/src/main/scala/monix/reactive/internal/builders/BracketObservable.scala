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

package monix.reactive.internal.builders

import monix.eval.Task
import monix.execution.BracketResult._
import monix.execution.atomic.Atomic
import monix.execution.misc.NonFatal
import monix.execution.{Ack, BracketResult, Cancelable, Scheduler}
import monix.reactive.Observable
import monix.reactive.observers.Subscriber

import scala.concurrent.Future

private[reactive] final class BracketObservable[A, B](source: Observable[B])(a: A, release: (A, BracketResult) => Task[Unit]) extends Observable[B] {

  override def unsafeSubscribeFn(out: Subscriber[B]): Cancelable = {
    // Calling release might be concurrent so
    // we need to make sure it's called only once
    val active = Atomic(true)

    val downstream = source.unsafeSubscribeFn(new Subscriber[B] {
      override implicit val scheduler: Scheduler = out.scheduler

      def onError(ex: Throwable): Unit = {
        out.onError(ex)
        try release(a, Error(ex)).runAsync catch {
          case err if NonFatal(err) =>
            scheduler.reportFailure(err)
//            out.onError(err)
        }
      }

      def onComplete(): Unit = {
        out.onComplete()
        try release(a, Completed).runAsync catch {
          case err if NonFatal(err) =>
            scheduler.reportFailure(err)
            out.onError(err)
        }
      }

      def onNext(elem: B): Future[Ack] = {
        out.onNext(elem).syncOnStopOrFailure {
          case Some(_) =>
            if (active.getAndSet(false))
              try release(a, EarlyStop).runAsync catch {
                case err if NonFatal(err) =>
                  scheduler.reportFailure(err)
                  out.onError(err)
              }
          case None =>
            if (active.getAndSet(false))
              try release(a, EarlyStop).runAsync catch {
                case err if NonFatal(err) =>
                  scheduler.reportFailure(err)
                  out.onError(err)
              }
        }
      }
    })

    Cancelable { () =>
      try downstream.cancel() finally {
        if (active.getAndSet(false))
          try release(a, EarlyStop).runAsync(out.scheduler) catch {
            case err if NonFatal(err) =>
              out.scheduler.reportFailure(err)
          }
      }
    }
  }
}