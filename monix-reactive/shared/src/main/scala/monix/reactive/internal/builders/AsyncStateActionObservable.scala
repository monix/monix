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

import monix.eval.{Callback, Task}
import monix.execution.Ack.{Continue, Stop}
import monix.execution.Cancelable
import monix.execution.misc.NonFatal
import monix.reactive.Observable
import monix.reactive.observers.Subscriber

private[reactive] final
class AsyncStateActionObservable[S,A](seed: => S, f: S => Task[(A,S)]) extends Observable[A] {
  def unsafeSubscribeFn(subscriber: Subscriber[A]): Cancelable = {
    import subscriber.scheduler
    var streamErrors = true
    try {
      val init = seed
      streamErrors = false

      Task.defer(loop(subscriber, init))
        .executeWithOptions(_.enableAutoCancelableRunLoops)
        .runAsync(Callback.empty)
    }
    catch {
      case NonFatal(ex) =>
        if (streamErrors) subscriber.onError(ex)
        else subscriber.scheduler.reportFailure(ex)
        Cancelable.empty
    }
  }

  def loop(subscriber: Subscriber[A], state: S): Task[Unit] =
    try f(state).flatMap { case (a, newState) =>
      Task.fromFuture(subscriber.onNext(a)).flatMap {
        case Continue => loop(subscriber, newState)
        case Stop => Task.unit
      }
    } catch {
      case NonFatal(ex) =>
        Task.raiseError(ex)
    }
}
