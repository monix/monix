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

import cats.effect.ExitCase
import cats.effect.ExitCase.{Completed, Error}
import monix.eval.Task
import monix.execution.atomic.Atomic
import monix.execution.{Ack, Cancelable, Scheduler}
import monix.reactive.Observable
import monix.reactive.observers.Subscriber

import scala.concurrent.Future
import scala.util.control.NonFatal

private[reactive] final class DoOnExitCaseObservable[A, B](
  source: Observable[B])(
  release: ExitCase[Throwable] => Task[Unit]) extends Observable[B] {

  override def unsafeSubscribeFn(out: Subscriber[B]): Cancelable = {
    // Calling release might be concurrent so
    // we need to make sure it's called only once
    val active = Atomic(true)

    val downstream = source.unsafeSubscribeFn(new Subscriber[B] {
      override implicit val scheduler: Scheduler = out.scheduler

      def onError(ex: Throwable): Unit = {
        out.onError(ex)
        if (active.getAndSet(false))
          try {
            release(Error(ex)).runAsync
          } catch {
            case err if NonFatal(err) =>
              scheduler.reportFailure(err)
          }
      }

      def onComplete(): Unit = {
        if (active.getAndSet(false))
          try {
            release(Completed)
              .attempt
              .map {
                case Left(e) =>
                  scheduler.reportFailure(e)
                  out.onError(e)
                case _ =>
                  out.onComplete()
              }.runAsync
          } catch {
            case err if NonFatal(err) =>
              scheduler.reportFailure(err)
              out.onError(err)
          }
      }

      def onNext(elem: B): Future[Ack] = {
        out.onNext(elem).syncOnStopOrFailure {
          case Some(ex) =>
            if (active.getAndSet(false))
              try {
                release(Error(ex)).runAsync
              } catch {
                case err if NonFatal(err) =>
                  scheduler.reportFailure(err)
                  out.onError(err)
              }
          case None =>
            if (active.getAndSet(false))
              try {
                release(ExitCase.canceled).runAsync
              } catch {
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
          try {
            release(ExitCase.canceled).runAsync(out.scheduler)
          } catch {
            case err if NonFatal(err) =>
              out.scheduler.reportFailure(err)
          }
      }
    }
  }
}