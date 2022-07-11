/*
 * Copyright (c) 2014-2021 by The Monix Project Developers.
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

package monix.reactive.internal.operators

import monix.execution.Ack.{ Continue, Stop }
import monix.execution.ChannelType.SingleProducer
import monix.execution.atomic.Atomic
import scala.util.control.NonFatal
import monix.execution.{ Ack, Cancelable, Scheduler }
import monix.reactive.observables.GroupedObservable
import monix.reactive.Observable.Operator
import monix.reactive.observers.{ BufferedSubscriber, Subscriber }
import monix.reactive.{ Observer, OverflowStrategy }
import scala.annotation.tailrec
import scala.concurrent.Future

private[reactive] final class GroupByOperator[A, K](
  os: OverflowStrategy.Synchronous[GroupedObservable[K, A]],
  keyFn: A => K
) extends Operator[A, GroupedObservable[K, A]] {

  def apply(subscriber: Subscriber[GroupedObservable[K, A]]): Subscriber[A] =
    new Subscriber[A] { self =>
      implicit val scheduler: Scheduler = subscriber.scheduler
      private[this] var isDone = false
      private[this] val downstream = BufferedSubscriber(subscriber, os, SingleProducer)
      private[this] val cacheRef = Atomic(Map.empty[K, Observer[A]])

      @tailrec
      private[this] def recycleKey(key: K): Unit = {
        val current = cacheRef.get()
        if (!cacheRef.compareAndSet(current, current - key))
          recycleKey(key)
      }

      private def retryOnNext(elem: A): Future[Ack] =
        onNext(elem)

      def onNext(elem: A): Future[Ack] =
        loop(elem)

      @tailrec
      private def loop(elem: A): Future[Ack] = {
        if (isDone) Stop
        else {
          val cache = cacheRef.get()
          var streamError = true

          val result =
            try {
              val key = keyFn(elem)
              streamError = false

              if (cache.contains(key)) {
                // If downstream cancels, it only canceled the current group,
                // so we should recreate the group. The call is concurrent with
                // `recycleKey`, so we'll keep retrying until either the downstream
                // returns `Continue` or the cache no longer has our key.
                cache(key).onNext(elem).syncFlatMap {
                  case Continue => Continue
                  case Stop => retryOnNext(elem)
                }
              } else {
                val onCancel = Cancelable(() => recycleKey(key))
                val (observer, observable) =
                  GroupedObservable.broadcast[K, A](key, onCancel)

                if (cacheRef.compareAndSet(cache, cache.updated(key, observer)))
                  downstream.onNext(observable).syncFlatMap {
                    case Continue =>
                      // pushing the first element
                      observer.onNext(elem).syncMap(_ => Continue)

                    case Stop =>
                      foreachObserver(_.onComplete())
                      Stop
                  }
                else
                  null // this will trigger a tailrec retry
              }
            } catch {
              case NonFatal(ex) if streamError =>
                self.onError(ex)
                Stop
            }

          if (result == null)
            loop(elem)
          else
            result
        }
      }

      private[this] def foreachObserver(f: Observer[A] => Unit): Unit = {
        val cache = cacheRef.get()
        if (cacheRef.compareAndSet(cache, Map.empty)) {
          cache.values.foreach(f)
        } else {
          foreachObserver(f)
        }
      }

      def onError(ex: Throwable): Unit = {
        if (!isDone) {
          isDone = true
          foreachObserver(_.onError(ex))
          downstream.onError(ex)
        }
      }

      def onComplete(): Unit = {
        if (!isDone) {
          isDone = true
          foreachObserver(_.onComplete())
          downstream.onComplete()
        }
      }
    }
}
