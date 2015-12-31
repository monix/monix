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

package monix


import scala.concurrent.{CanAwait, ExecutionContext, Future}
import scala.util.{Success, Try}
import scala.concurrent.duration.Duration

/**
 * Represents the acknowledgement of processing that a consumer
 * sends back upstream on `Observer.onNext`
 */
sealed trait Ack extends Future[Ack]

object Ack {
  /**
   * Acknowledgement of processing that signals upstream that the
   * consumer is interested in receiving more events.
   */
  sealed trait Continue extends Ack

  case object Continue extends Continue with AckIsFuture[Continue] {
    final val IsSuccess = Success(Continue)
    final val value = Some(IsSuccess)
  }

  /**
   * Acknowledgement or processing that signals upstream that the
   * consumer is no longer interested in receiving events.
   */
  sealed trait Cancel extends Ack

  case object Cancel extends Cancel with AckIsFuture[Cancel] {
    final val IsSuccess = Success(Cancel)
    final val value = Some(IsSuccess)
  }
}

private[monix] sealed trait AckIsFuture[T <: Ack]
  extends Future[T] { self =>

  final val isCompleted = true

  final def ready(atMost: Duration)(implicit permit: CanAwait) = self
  final def result(atMost: Duration)(implicit permit: CanAwait) = value.get.get

  final def onComplete[U](func: (Try[T]) => U)(implicit executor: ExecutionContext): Unit =
    executor.execute(new Runnable {
      def run(): Unit = func(value.get)
    })
}
