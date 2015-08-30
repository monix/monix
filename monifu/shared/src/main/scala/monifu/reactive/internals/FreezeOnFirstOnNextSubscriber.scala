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

package monifu.reactive.internals

import monifu.reactive.{Ack, Subscriber}
import scala.concurrent.{Future, Promise}

private[reactive] final class FreezeOnFirstOnNextSubscriber[-T]
    (underlying: Subscriber[T])
  extends Subscriber[T] { self =>

  implicit val scheduler = underlying.scheduler
  private[this] val connectedPromise = Promise[Ack]()
  private[this] val connectedFuture = connectedPromise.future
  private[this] val firstTimePromise = Promise[Unit]()
  @volatile private[this] var isConnected = false

  val firstTimeOnNext = firstTimePromise.future

  def continue(ack: Future[Ack]): Unit = {
    connectedPromise.tryCompleteWith(ack)
    isConnected = true
  }

  def onNext(elem: T): Future[Ack] = {
    if (!isConnected) {
      firstTimePromise.trySuccess(())
      connectedFuture
    }
    else {
      // fast path
      underlying.onNext(elem)
    }
  }

  def onComplete() = {
    if (!isConnected) firstTimePromise.trySuccess(())
    // we cannot take a fast path here
    connectedFuture.onContinueSignalComplete(underlying)
  }

  def onError(ex: Throwable) = {
    if (!isConnected) firstTimePromise.trySuccess(())
    // we cannot take a fast path here
    connectedFuture.onContinueSignalError(underlying, ex)
  }
}