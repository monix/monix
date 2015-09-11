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

import monifu.reactive._
import monifu.reactive.internals._
import monifu.reactive.internals.collection._

/**
 * `ReplaySubject` emits to any observer all of the items that were emitted
 * by the source, regardless of when the observer subscribes.
 */
final class ReplaySubject[T] private (initial: Buffer[T])
  extends GenericSubject[T] {

  protected type LiftedSubscriber = FreezeOnFirstOnNextSubscriber[T]

  protected def liftSubscriber(ref: Subscriber[T]): LiftedSubscriber =
    new FreezeOnFirstOnNextSubscriber(ref)

  private[this] val buffer = initial
  protected def cacheOrIgnore(elem: T): Unit =
    buffer.offer(elem)

  protected def onSubscribeCompleted(s: Subscriber[T], errorThrown: Throwable): Unit = {
    import s.scheduler
    val f = s.feed(buffer)

    if (errorThrown != null)
      f.onContinueSignalError(s, errorThrown)
    else
      f.onContinueSignalComplete(s)
  }

  protected def onSubscribeContinue(lifted: FreezeOnFirstOnNextSubscriber[T], s: Subscriber[T]): Unit = {
    // if update succeeded, then wait for `onNext`,
    // then feed our buffer, then unfreeze onNext
    lifted.initializeOnNext(s.feed(buffer))
  }
}

object ReplaySubject {
  /** Creates an unbounded replay subject. */
  def apply[T](initial: T*): ReplaySubject[T] = {
    val buffer = UnlimitedBuffer[Any]()
    if (initial.nonEmpty) buffer.offerMany(initial: _*)
    new ReplaySubject[T](buffer.asInstanceOf[Buffer[T]])
  }

  /** Creates an unbounded replay subject. */
  def create[T](initial: T*): ReplaySubject[T] = {
    val buffer = UnlimitedBuffer[Any]()
    if (initial.nonEmpty) buffer.offerMany(initial: _*)
    new ReplaySubject[T](buffer.asInstanceOf[Buffer[T]])
  }

  /**
   * Creates a size-bounded replay subject.
   *
   * In this setting, the ReplaySubject holds at most size items in its
   * internal buffer and discards the oldest item.
   *
   * NOTE: the `capacity` is actually grown to the next power of 2 (minus 1),
   * because buffers sized as powers of two can be more efficient and the
   * underlying implementation is most likely to be a ring buffer. So give it
   * `300` and its capacity is going to be `512 - 1`
   */
  def createWithSize[T](capacity: Int) = {
    val buffer = DropHeadOnOverflowQueue[Any](capacity)
    new ReplaySubject[T](buffer.asInstanceOf[Buffer[T]])
  }
}
