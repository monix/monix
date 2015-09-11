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

import monifu.reactive.Subscriber
import monifu.reactive.internals._

/**
 * `BehaviorSubject` when subscribed, will emit the most recently emitted item by the source,
 * or the `initialValue` (as the seed) in case no value has yet been emitted, then continuing
 * to emit events subsequent to the time of invocation.
 *
 * When the source terminates in error, the `BehaviorSubject` will not emit any items to
 * subsequent subscribers, but instead it will pass along the error notification.
 *
 * @see [[monifu.reactive.Subject]]
 */
final class BehaviorSubject[T] private (initialValue: T) extends GenericSubject[T] {
  private[this] var cachedElem = initialValue
  protected type LiftedSubscriber = FreezeOnFirstOnNextSubscriber[T]

  protected def liftSubscriber(ref: Subscriber[T]): LiftedSubscriber =
    new FreezeOnFirstOnNextSubscriber(ref)

  protected def cacheOrIgnore(elem: T): Unit = {
    cachedElem = elem
  }

  protected def onSubscribeCompleted(s: Subscriber[T], errorThrown: Throwable): Unit = {
    if (errorThrown == null)
      s.onNext(cachedElem).onContinueSignalComplete(s)(s.scheduler)
    else
      s.onError(errorThrown)
  }

  protected def onSubscribeContinue(lifted: FreezeOnFirstOnNextSubscriber[T], s: Subscriber[T]): Unit = {
    // if update succeeded, then wait for `onNext`,
    // then feed our buffer, then unfreeze onNext
    lifted.initializeOnNext(s.onNext(cachedElem))
  }
}

object BehaviorSubject {
  /** Builder for [[BehaviorSubject]] */
  def apply[T](initialValue: T): BehaviorSubject[T] =
    new BehaviorSubject[T](initialValue)
}
