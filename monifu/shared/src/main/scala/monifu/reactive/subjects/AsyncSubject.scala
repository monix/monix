/*
 * Copyright (c) 2014-2015 by its authors. Some rights reserved.
 * See the project homepage at: http://www.monifu.org
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
 
package monifu.reactive.subjects

import monifu.reactive.Ack.{Cancel, Continue}
import monifu.reactive.internals._
import monifu.reactive.{Ack, Subject, Subscriber}
import scala.concurrent.Future

/**
 * An `AsyncSubject` emits the last value (and only the last value) emitted by the source Observable,
 * and only after that source Observable completes.
 *
 * If the source terminates with an error, the `AsyncSubject` will not emit any
 * items to subsequent subscribers, but will simply pass along the error
 * notification from the source Observable.
 */
final class AsyncSubject[T] extends Subject[T,T] { self =>
  @volatile private[this] var subscribers = Vector.empty[Subscriber[T]]
  @volatile private[this] var isDone = false

  private[this] var onNextHappened = false
  private[this] var errorThrown: Throwable = null
  private[this] var cachedElem: T = _

  private[this] def onDone(subscriber: Subscriber[T]) = {
    import subscriber.scheduler
    if (errorThrown != null)
      subscriber.onError(errorThrown)
    else if (onNextHappened) {
      subscriber.onNext(cachedElem)
        .onContinueSignalComplete(subscriber)
    }
    else {
      subscriber.onComplete()
    }
  }

  def onSubscribe(subscriber: Subscriber[T]): Unit =
    if (isDone) {
      // fast path
      onDone(subscriber)
    }
    else self.synchronized {
      if (isDone)
        onDone(subscriber)
      else
        subscribers = subscribers :+ subscriber
    }

  def onNext(elem: T): Future[Ack] = {
    if (isDone) Cancel else {
      if (!onNextHappened) onNextHappened = true
      cachedElem = elem
      Continue
    }
  }

  def onError(ex: Throwable): Unit = self.synchronized {
    if (!isDone) {
      errorThrown = ex
      isDone = true
      subscribers.foreach(onDone)
      subscribers = Vector.empty
    }
  }

  def onComplete(): Unit = self.synchronized {
    if (!isDone) {
      isDone = true
      subscribers.foreach(onDone)
      subscribers = Vector.empty
    }
  }
}

object AsyncSubject {
  def apply[T](): AsyncSubject[T] =
    new AsyncSubject[T]()
}