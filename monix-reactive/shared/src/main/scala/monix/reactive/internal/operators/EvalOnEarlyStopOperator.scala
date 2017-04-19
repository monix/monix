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

package monix.reactive.internal.operators

import monix.eval.Task
import monix.execution.Ack
import monix.execution.Ack.{Continue, Stop}
import monix.execution.atomic.Atomic
import monix.execution.misc.NonFatal
import monix.reactive.observables.ObservableLike.Operator
import monix.reactive.observers.Subscriber
import monix.reactive.internal.util.Instances.ContinueTask

import scala.concurrent.Future
import scala.util.Success

private[reactive] final class EvalOnEarlyStopOperator[A](onStop: Task[Unit])
  extends Operator[A,A] {

  def apply(out: Subscriber[A]): Subscriber[A] =
    new Subscriber[A] {
      implicit val scheduler = out.scheduler
      private[this] val isActive = Atomic(true)

      def onNext(elem: A): Future[Ack] = {
        val result =
          try out.onNext(elem)
          catch { case NonFatal(ex) => Future.failed(ex) }

        val task = Task.fromFuture(result)
          .onErrorHandle { ex => onError(ex); Stop }
          .flatMap { case Continue => ContinueTask; case Stop => onStop.map(_ => Stop) }

        val future = task.runAsync
        // Execution might be immediate
        future.value match {
          case Some(Success(ack)) => ack
          case _ => future
        }
      }

      def onError(ex: Throwable): Unit = {
        if (isActive.getAndSet(false))
          out.onError(ex)
        else
          scheduler.reportFailure(ex)
      }

      def onComplete(): Unit = {
        if (isActive.getAndSet(false))
          out.onComplete()
      }
    }
}