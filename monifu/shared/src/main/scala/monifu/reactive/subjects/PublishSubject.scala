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
import monifu.reactive.internals.GenericSubject

/**
 * A `PublishSubject` emits to a subscriber only those items that are
 * emitted by the source subsequent to the time of the subscription
 *
 * If the source terminates with an error, the `PublishSubject` will not emit any
 * items to subsequent subscribers, but will simply pass along the error
 * notification from the source Observable.
 *
 * @see [[monifu.reactive.Subject]]
 */
final class PublishSubject[T] private () extends GenericSubject[T] {
  protected type LiftedSubscriber = Subscriber[T]

  protected def cacheOrIgnore(elem: T): Unit = ()
  protected def liftSubscriber(ref: Subscriber[T]) = ref
  protected def onSubscribeContinue(lifted: Subscriber[T], s: Subscriber[T]): Unit = ()

  protected def onSubscribeCompleted(subscriber: Subscriber[T], errorThrown: Throwable): Unit = {
    if (errorThrown == null)
      subscriber.onComplete()
    else
      subscriber.onError(errorThrown)
  }
}

object PublishSubject {
  /** Builder for [[PublishSubject]] */
  def apply[T](): PublishSubject[T] =
    new PublishSubject[T]()
}
