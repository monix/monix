/*
 * Copyright (c) 2014 by its authors. Some rights reserved.
 * See the project homepage at
 *
 *     http://www.monifu.org/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  	http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package monifu.reactive.operators

import monifu.concurrent.cancelables.{BooleanCancelable, SingleAssignmentCancelable}
import monifu.concurrent.locks.SpinLock
import monifu.concurrent.{Scheduler, Cancelable}
import monifu.reactive.Ack.{Cancel, Continue}
import monifu.reactive.BufferPolicy.{BackPressured, OverflowTriggering, Unbounded}
import monifu.reactive.internals._
import monifu.reactive._

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success}


object delay {
  /**
   * Implementation for `Observable.delayFirst(policy, init)`.
   */
  def onEvent[T](source: Observable[T], policy: BufferPolicy)
      (eventInit: (() => Unit, Throwable => Unit) => Cancelable)
      (implicit s: Scheduler) = Observable.create[T] { observer =>
    source.unsafeSubscribe(new Observer[T] {
      private[this] val lock = SpinLock()
      // MUST BE synchronized by lock at all times
      private[this] var buffer = ArrayBuffer.empty[T]
      // SHOULD BE synchronized by lock if false, if true then
      // the logic can take the fast path and avoid lock synchronization
      private[this] var isConnected = false
      // signals that the downstream observer has canceled the stream
      // during buffer draining, set to true after isConnected = true and
      // before connectedF is completed
      private[this] var isCanceledDuringFeeding = false
      // Promise to be completed after the observer has been fed
      // with the whole buffer
      private[this] val connectedP = Promise[Ack]()
      // Future to be completed after the observer has been fed
      // with the whole buffer - reference must be mutable, because
      // that flatMap-ing in onNext can become concurrent with
      // onComplete / onError, so it must be changed to reflect
      // the latest acknowledgement of `observer.onNext(elem)`
      private[this] var connectedF = connectedP.future
      // cancelable of the task that has to run for executing `connect`,
      // initialized either in onNext or in onComplete.
      private[this] val task = SingleAssignmentCancelable()
      // value to be set to false after the first item being signaled
      // accessed from onNext/onComplete so does not need synchronization
      private[this] var isTaskInitialized = false

      /** Initializes scheduled task, to be called in onNext/onComplete */
      @inline private[this] def initializeTask(): Unit = {
        if (!isTaskInitialized) {
          task := eventInit(connect, onError)
          isTaskInitialized = true
        }
      }

      private[this] def connect(): Unit = lock.enter {
        if (!isConnected) {
          // signals that the observer has been connected to the stream
          // and thus future events mustn't be buffered
          isConnected = true

          Observer.feed(observer, buffer).onCompleteNow {
            case ack @ Success(Continue | Cancel) =>
              lock.enter {
                isCanceledDuringFeeding = ack == Cancel.IsSuccess
                buffer = null // GC purposes
                connectedP.complete(ack)
              }
            case Failure(ex) =>
              isCanceledDuringFeeding = true
              observer.onError(ex)
              buffer = null // GC purposes
              connectedP.success(Cancel)
          }
        }
      }

      def onNext(elem: T): Future[Ack] = {
        if (isConnected && connectedF.isCompleted) {
          // fast path branch taken when the observer is connected
          // and the whole buffer has been drained
          if (!isCanceledDuringFeeding) {
            connectedF = observer.onNext(elem)
            connectedF
          }
          else {
            Cancel
          }
        }
        else if (isConnected) {
          // slow path branch taken when the observer is connected,
          // but the buffer draining is in progress, so we must still do
          // buffering, this time piggybacking on the execution context
          connectedF = connectedF.flatMap {
            case Cancel => Cancel
            case Continue =>
              observer.onNext(elem)
          }
          connectedF
        }
        else lock.enter {
          // race condition guard
          if (isConnected)
            onNext(elem) // retry
          else {
            // initializing the task that will eventually connect
            // the buffer to the downstream observer
            initializeTask()

            // we are still within the delay period and hence we are still
            // buffering incoming events, but we can only do buffering
            // according to the requested policy
            policy match {
              case Unbounded =>
                // unbounded means buffer everything
                buffer.append(elem)
                Continue

              case OverflowTriggering(capacity) =>
                if (buffer.length < capacity) {
                  buffer.append(elem)
                  Continue
                }
                else {
                  // if over capacity, then trigger error
                  onError(new BufferOverflowException(
                    s"Reached buffer capacity of $capacity during delay, " +
                      "cannot buffer any more items"
                  ))
                  Cancel
                }

              case BackPressured(capacity) =>
                if (buffer.length < capacity) {
                  buffer.append(elem)
                  Continue
                }
                else {
                  // if over capacity, then do back-pressure
                  connectedF = connectedF.flatMap {
                    case Cancel => Cancel
                    case Continue =>
                      observer.onNext(elem)
                  }
                  connectedF
                }
            }
          }
        }
      }

      def onComplete(): Unit = {
        initializeTask()
        // the complete event gets scheduled to run after the
        // observer has been connected and the buffer has
        // been drained - careful here, since this logic can end up
        // running concurrent with the flatMap-ing in onNext
        connectedF.onContinueComplete(observer)
      }

      def onError(ex: Throwable): Unit = lock.enter {
        if (!isConnected) {
          // we are not connected yet, so cancel the buffer
          // and stream the error immediately
          task.cancel()
          buffer = null // GC purposes
          isConnected = true
          observer.onError(ex)
        }
        else {
          // we are connected, so we must stream the error only after
          // the buffer has been drained - careful here, since this logic
          // can end up running concurrently with the flatMap-ing in onNext
          connectedF.onContinueComplete(observer, ex)
        }
      }
    })
  }

  /**
   * Implementation for `Observable.delayFirst(policy, future)`.
   */
  def onFuture[T](source: Observable[T], policy: BufferPolicy, future: Future[_])(implicit s: Scheduler) =
      source.delayFirstOnEvent(policy = policy, eventInit = {
        (connect, signalError) =>
          val task = BooleanCancelable()
          future.onComplete {
            case Success(_) =>
              if (!task.isCanceled)
                connect()
            case Failure(ex) =>
              if (!task.isCanceled)
                signalError(ex)
          }

          task
      })
}
