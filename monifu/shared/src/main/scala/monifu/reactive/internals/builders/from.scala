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

package monifu.reactive.internals.builders

import monifu.reactive.Ack.Continue
import monifu.reactive.{Ack, Observable}
import monifu.reactive.internals._

import scala.annotation.tailrec
import scala.concurrent.Future
import scala.util.{Failure, Success}
import scala.util.control.NonFatal

private[reactive] object from {
  /**
   * Implementation for [[Observable.fromIterable]].
   */
  def iterable[T](iterable: Iterable[T]): Observable[T] =
    Observable.create { subscriber =>
      var streamError = true
      try {
        val i = iterable.iterator
        streamError = false
        iterator(i).unsafeSubscribe(subscriber)
      }
      catch {
        case NonFatal(ex) if streamError =>
          subscriber.observer.onError(ex)
      }
    }

  /**
   * Implementation for [[Observable.fromIterator]].
   */
  def iterator[T](iterator: Iterator[T]): Observable[T] = {
    Observable.create { subscriber =>
      implicit val s = subscriber.scheduler
      val observer = subscriber.observer

      def startFeedLoop(iterator: Iterator[T]): Unit = s.execute(new Runnable {
        /**
         * Loops synchronously, pushing elements into the downstream observer,
         * until either the iterator is finished or an asynchronous barrier
         * is being hit.
         */
        @tailrec
        def fastLoop(): Unit = {
          // the result of onNext calls, on which we must do back-pressure
          var ack: Future[Ack] = Continue
          // we do not want to catch errors from our interaction with our observer,
          // since SafeObserver should take care of than, hence we must only
          // catch and stream errors related to the interactions with the iterator
          var streamError = true
          // true in case our iterator is seen to be empty and we must signal onComplete
          var signalDone = false
          // non-null in case we caught an iterator related error and we must signal onError
          var signalError: Throwable = null

          try {
            if (iterator.hasNext) {
              val next = iterator.next()
              streamError = false
              ack = observer.onNext(next)
            }
            else
              signalDone = true
          }
          catch {
            case NonFatal(ex) if streamError =>
              signalError = ex
          }

          if (signalDone) {
            observer.onComplete()
          }
          else if (signalError != null) {
            observer.onError(signalError)
          }
          else
            ack match {
              case sync if sync.isCompleted =>
                sync.value.get match {
                  case Continue.IsSuccess =>
                    fastLoop()
                  case _ =>
                    () // do nothing
                }

              case async =>
                async.onComplete {
                  case Continue.IsSuccess =>
                    run()
                  case _ =>
                    () // do nothing
                }
            }
        }

        def run(): Unit = {
          fastLoop()
        }
      })

      var streamError = true
      try {
        val isEmpty = iterator.isEmpty
        streamError = false

        if (isEmpty)
          observer.onComplete()
        else
          startFeedLoop(iterator)
      }
      catch {
        case NonFatal(ex) if streamError =>
          observer.onError(ex)
      }
    }
  }

  /**
   * Implementation for [[Observable.fromFuture]].
   */
  def future[T](f: Future[T]): Observable[T] =
    Observable.create { subscriber =>
      implicit val s = subscriber.scheduler
      val o = subscriber.observer

      f.value match {
        case Some(Success(value)) =>
          o.onNext(value).onContinueSignalComplete(o)
        case Some(Failure(ex)) =>
          o.onError(ex)
        case None => f.onComplete {
          case Success(value) =>
            o.onNext(value).onContinueSignalComplete(o)
          case Failure(ex) =>
            o.onError(ex)
        }
      }
    }
}
