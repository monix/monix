/*
 * Copyright (c) 2014 by its authors. Some rights reserved.
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

package monifu.reactive.streams

import monifu.reactive.Observer
import monifu.reactive.observers._

import scala.concurrent.ExecutionContext

/**
 * Mirrors the `Subscriber` interface from the
 * [[http://www.reactive-streams.org/ Reactive Streams]] project.
 */
trait Subscriber[T] {
  def onSubscribe(s: Subscription): Unit
  def onNext(elem: T): Unit
  def onError(ex: Throwable): Unit
  def onComplete(): Unit
}

object Subscriber {
  /**
   * Given an [[Observer]], builds a [[Subscriber]] instance as defined by the
   * [[http://www.reactive-streams.org/ Reactive Streams]] specification.
   */
  def from[T](observer: Observer[T], requestSize: Int = 128)(implicit ec: ExecutionContext): Subscriber[T] = {
    observer match {
      case sync: SynchronousObserver[_] =>
        val inst = sync.asInstanceOf[SynchronousObserver[T]]
        SynchronousObserverAsSubscriber(inst, requestSize)
      case async =>
        ObserverAsSubscriber(async, requestSize)
    }
  }
}
