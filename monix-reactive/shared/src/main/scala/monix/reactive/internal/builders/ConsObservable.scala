/*
 * Copyright (c) 2014-2017 by The Monix Project Developers.
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

import monix.execution.Ack.{Stop, Continue}
import monix.execution.Cancelable
import monix.execution.cancelables.SingleAssignmentCancelable
import monix.reactive.Observable
import monix.reactive.observers.Subscriber
import scala.util.{Failure, Success}

private[reactive] final class ConsObservable[+A](head: A, tail: Observable[A])
  extends Observable[A] {

  override def unsafeSubscribeFn(subscriber: Subscriber[A]): Cancelable = {
    import subscriber.scheduler
    val cancelable = SingleAssignmentCancelable()

    // Need an explicit asynchronous boundary because cons is used
    // in recursive contexts.
    subscriber.onNext(head).onComplete {
      case Success(Continue) =>
        cancelable := tail.unsafeSubscribeFn(subscriber)
      case Success(Stop) =>
        () // do nothing
      case Failure(ex) =>
        subscriber.onError(ex)
    }

    cancelable
  }
}
