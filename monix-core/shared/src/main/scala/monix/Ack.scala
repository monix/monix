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

import scala.concurrent.duration.Duration
import scala.concurrent.{CanAwait, ExecutionContext, Future}
import scala.util.{Success, Try}

/** Represents the acknowledgement of processing that a consumer
  * sends back upstream on `Observer.onNext`
  */
sealed abstract class Ack extends Future[Ack]

object Ack {
  /** Acknowledgement of processing that signals upstream that the
    * consumer is interested in receiving more events.
    */
  sealed abstract class Continue extends Ack

  case object Continue extends Continue with Future[Continue] { self =>
    final val AsSuccess = Success(Continue)
    final val value = Some(AsSuccess)
    final val isCompleted = true

    final def ready(atMost: Duration)(implicit permit: CanAwait) = self
    final def result(atMost: Duration)(implicit permit: CanAwait) = Continue

    final def onComplete[U](func: Try[Continue] => U)(implicit executor: ExecutionContext): Unit =
      executor.execute(new Runnable {
        def run(): Unit = func(AsSuccess)
      })
  }

  /** Acknowledgement or processing that signals upstream that the
    * consumer is no longer interested in receiving events.
    */
  sealed abstract class Cancel extends Ack

  case object Cancel extends Cancel with Future[Cancel] { self =>
    final val AsSuccess = Success(Cancel)
    final val value = Some(AsSuccess)
    final val isCompleted = true

    final def ready(atMost: Duration)(implicit permit: CanAwait) = self
    final def result(atMost: Duration)(implicit permit: CanAwait) = Cancel

    final def onComplete[U](func: Try[Cancel] => U)(implicit executor: ExecutionContext): Unit =
      executor.execute(new Runnable {
        def run(): Unit = func(AsSuccess)
      })
  }
}

