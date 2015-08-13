/*
 * Copyright (c) 2014-2015 Alexandru Nedelcu
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

package monifu.reactive.operators

import java.util.concurrent.TimeUnit

import monifu.concurrent.cancelables.MultiAssignmentCancelable
import monifu.reactive.Ack.{Cancel, Continue}
import monifu.reactive.internals._
import monifu.reactive.{Ack, Observable, Observer}
import scala.concurrent.duration._
import scala.concurrent.{Future, TimeoutException}


object timeout {
  def emitError[T](source: Observable[T], timeout: FiniteDuration): Observable[T] =
    switchToBackup(source, timeout, Observable.error(
      new TimeoutException(s"Observable timed-out after $timeout of inactivity")
    ))

  def switchToBackup[T](source: Observable[T], timeout: FiniteDuration, backup: Observable[T]) =
    Observable.create[T] { subscriber =>
      implicit val s = subscriber.scheduler

      source.unsafeSubscribe(new Observer[T] with Runnable { self =>
        private[this] val downstream = subscriber.observer
        private[this] val timeoutMillis = timeout.toMillis
        private[this] val task = MultiAssignmentCancelable()
        private[this] var ack: Future[Ack] = Continue
        // MUST BE synchronized by `self`
        private[this] var isDone = false
        // MUST BE synchronized by `self`
        private[this] var lastEmittedMillis: Long = s.currentTimeMillis()

        locally {
          task := s.scheduleOnce(timeout, self)
        }

        def run(): Unit = self.synchronized {
          if (!isDone) {
            val rightNow = s.currentTimeMillis()
            val sinceLastOnNextInMillis = rightNow - lastEmittedMillis

            if (sinceLastOnNextInMillis >= timeoutMillis) {
              isDone = true
              ack.onContinue {
                // subscribing our downstream observer to the backup observable
                backup.unsafeSubscribe(subscriber)
              }
            }
            else {
              val remainingTimeMillis = timeoutMillis - sinceLastOnNextInMillis
              task := s.scheduleOnce(remainingTimeMillis, TimeUnit.MILLISECONDS, self)
            }
          }
        }

        def onNext(elem: T): Future[Ack] = {
          // unfortunately we can't get away of this synchronization
          // as the scheduler is contending on the same downstream
          self.synchronized {
            if (isDone) Cancel else {
              lastEmittedMillis = s.currentTimeMillis()
              ack = downstream.onNext(elem)
              ack
            }
          }
        }

        def onError(ex: Throwable) = self.synchronized {
          if (!isDone) {
            isDone = true
            ack.onContinueSignalError(downstream, ex)
          }
        }

        def onComplete() = self.synchronized {
          if (!isDone) {
            isDone = true
            ack.onContinueSignalComplete(downstream)
          }
        }
      })
    }
}
