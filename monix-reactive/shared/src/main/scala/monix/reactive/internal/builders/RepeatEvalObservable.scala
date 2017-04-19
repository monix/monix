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

package monix.reactive.internal.builders

import monix.execution.Ack.{Continue, Stop}
import monix.execution.cancelables.BooleanCancelable
import monix.execution._
import monix.execution.misc.NonFatal
import monix.reactive.Observable
import monix.reactive.observers.Subscriber

import scala.annotation.tailrec
import scala.concurrent.Future
import scala.util.{Failure, Success}

private[reactive] final class RepeatEvalObservable[+A](eval: => A)
  extends Observable[A] {

  def unsafeSubscribeFn(subscriber: Subscriber[A]): Cancelable = {
    val s = subscriber.scheduler
    val cancelable = BooleanCancelable()
    fastLoop(subscriber, cancelable, s.executionModel, 0)(s)
    cancelable
  }

  def reschedule(ack: Future[Ack], o: Subscriber[A], c: BooleanCancelable, em: ExecutionModel)
    (implicit s: Scheduler): Unit =
    ack.onComplete {
      case Success(success) =>
        if (success == Continue) fastLoop(o, c, em, 0)
      case Failure(ex) =>
        s.reportFailure(ex)
      case _ =>
        () // this was a Stop, do nothing
    }

  @tailrec
  def fastLoop(o: Subscriber[A], c: BooleanCancelable,
    em: ExecutionModel, syncIndex: Int)(implicit s: Scheduler): Unit = {

    val ack = try o.onNext(eval) catch {
      case NonFatal(ex) =>
        Future.failed(ex)
    }

    val nextIndex =
      if (ack == Continue) em.nextFrameIndex(syncIndex)
      else if (ack == Stop) -1
      else 0

    if (nextIndex > 0)
      fastLoop(o, c, em, nextIndex)
    else if (nextIndex == 0 && !c.isCanceled)
      reschedule(ack, o, c, em)
  }
}
