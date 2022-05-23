/*
 * Copyright (c) 2014-2021 by The Monix Project Developers.
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

package monix.reactive.internal.operators

import cats.effect.ExitCase
import monix.execution.Callback
import monix.eval.Task
import monix.execution.Ack.{ Continue, Stop }
import monix.execution.atomic.{ Atomic, AtomicBoolean }
import monix.execution.internal.Platform
import monix.execution.schedulers.TrampolineExecutionContext.immediate
import monix.execution.schedulers.TrampolinedRunnable
import monix.execution.{ Ack, Cancelable, FutureUtils, Scheduler }
import monix.reactive.Observable
import monix.reactive.observers.Subscriber
import scala.concurrent.Future
import scala.util.control.NonFatal
import scala.util.{ Failure, Success, Try }

private[reactive] class GuaranteeCaseObservable[A](source: Observable[A], f: ExitCase[Throwable] => Task[Unit])
  extends Observable[A] {

  def unsafeSubscribeFn(out: Subscriber[A]): Cancelable = {
    implicit val s = out.scheduler
    val isActive = Atomic(true)
    try {
      val out2 = new GuaranteeSubscriber(out, isActive)
      val c = source.unsafeSubscribeFn(out2)
      Cancelable.collection(c, out2)
    } catch {
      case NonFatal(e) =>
        fireAndForget(isActive, ExitCase.Error(e))
        s.reportFailure(e)
        Cancelable.empty
    }
  }

  private def fireAndForget(isActive: AtomicBoolean, ec: ExitCase[Throwable])(implicit s: Scheduler): Unit = {
    if (isActive.getAndSet(false))
      s.execute(new TrampolinedRunnable {
        def run(): Unit =
          try {
            f(ec).runAsyncAndForget
          } catch {
            case NonFatal(e) =>
              s.reportFailure(e)
          }
      })
  }

  private final class GuaranteeSubscriber(out: Subscriber[A], isActive: AtomicBoolean)
    extends Subscriber[A] with Cancelable {

    implicit val scheduler: Scheduler = out.scheduler
    private[this] var ack: Future[Ack] = Continue

    def onNext(elem: A): Future[Ack] = {
      var catchErrors = true
      try {
        val fa = out.onNext(elem)
        ack = fa
        catchErrors = false
        detectStopOrFailure(fa)
      } catch {
        case NonFatal(e) if catchErrors =>
          detectStopOrFailure(Future.failed(e))
      }
    }

    def onError(ex: Throwable): Unit =
      signalComplete(ex)
    def onComplete(): Unit =
      signalComplete(null)
    def cancel(): Unit =
      fireAndForget(isActive, ExitCase.Canceled)

    private def detectStopOrFailure(ack: Future[Ack]): Future[Ack] =
      ack match {
        case Continue => Continue
        case Stop =>
          stopAsFuture(ExitCase.Canceled)
        case async =>
          FutureUtils.transformWith(async, asyncTransformRef)(immediate)
      }

    private[this] val asyncTransformRef: Try[Ack] => Future[Ack] = {
      case Success(value) =>
        detectStopOrFailure(value)
      case Failure(e) =>
        stopAsFuture(ExitCase.Error(e))
    }

    private def stopAsFuture(e: ExitCase[Throwable]): Future[Ack] = {
      // Thread-safety guard
      if (isActive.getAndSet(false)) {
        Task
          .suspend(f(e))
          .redeem(e => { scheduler.reportFailure(e); Stop }, _ => Stop)
          .runToFuture
      } else {
        Stop
      }
    }

    private def signalComplete(e: Throwable): Unit = {
      def composeError(e: Throwable, e2: Throwable) = {
        if (e != null) Platform.composeErrors(e, e2)
        else e2
      }

      // We have to back-pressure the final acknowledgement, otherwise
      // the implementation is broken
      val task = Task
        .fromFuture(ack)
        .redeemWith(
          e2 => {
            if (isActive.getAndSet(false)) {
              val error = composeError(e, e2)
              f(ExitCase.Error(error)).map(_ => Stop)
            } else {
              scheduler.reportFailure(e2)
              Task.now(Stop)
            }
          },
          ack => {
            if (isActive.getAndSet(false)) {
              val code = if (e != null) ExitCase.Error(e) else ExitCase.Completed
              f(code).map(_ => ack)
            } else {
              Task.now(Stop)
            }
          }
        )

      task.runAsyncUncancelable(new Callback[Throwable, Ack] {
        def onSuccess(value: Ack): Unit = {
          if (value == Continue) {
            if (e != null) out.onError(e)
            else out.onComplete()
          }
        }

        def onError(e2: Throwable): Unit =
          out.onError(composeError(e, e2))
      })
    }
  }
}
