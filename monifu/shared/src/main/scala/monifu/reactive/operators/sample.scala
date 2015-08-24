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

package monifu.reactive.operators

import monifu.concurrent.Scheduler
import monifu.reactive.Ack.{Cancel, Continue}
import monifu.reactive.observers.SynchronousObserver
import monifu.reactive.{Ack, Observable, Observer}
import scala.concurrent.Future
import scala.concurrent.duration._


private[reactive] object sample {
  /**
   * Implementation for `Observable.sample(initialDelay, delay)`.
   */
  def once[T](source: Observable[T], initialDelay: FiniteDuration, delay: FiniteDuration): Observable[T] =
    once(source, Observable.intervalAtFixedRate(initialDelay, delay))

  /**
   * Implementation for `Observable.sample(sampler)`.
   */
  def once[T,U](source: Observable[T], sampler: Observable[U]): Observable[T] =
    Observable.create { subscriber =>
      implicit val s = subscriber.scheduler
      val observer = subscriber.observer
      source.unsafeSubscribe(new SampleObserver(
        observer, sampler, shouldRepeatOnSilence = false))
    }

  /**
   * Implementation for `Observable.sampleRepeated(sampler)`.
   */
  def repeated[T,U](source: Observable[T], sampler: Observable[U]): Observable[T] =
    Observable.create { subscriber =>
      implicit val s = subscriber.scheduler
      val observer = subscriber.observer
      source.unsafeSubscribe(new SampleObserver(
        observer, sampler, shouldRepeatOnSilence = true))
    }

  /**
   * Implementation for `Observable.sampleRepeated(initialDelay, delay)`.
   */
  def repeated[T](source: Observable[T], initialDelay: FiniteDuration, delay: FiniteDuration): Observable[T] =
    repeated(source, Observable.intervalAtFixedRate(initialDelay, delay))

  private class SampleObserver[T,U]
      (downstream: Observer[T], sampler: Observable[U], shouldRepeatOnSilence: Boolean)
      (implicit s: Scheduler)
    extends SynchronousObserver[T] {

    @volatile private[this] var hasValue = false
    // MUST BE written before `hasValue = true`
    private[this] var lastValue: T = _
    // to be written in onComplete/onError, to be read from tick
    @volatile private[this] var upstreamIsDone = false
    // MUST BE written to before `upstreamIsDone = true`
    private[this] var upstreamError: Throwable = null

    def onNext(elem: T): Ack = {
      lastValue = elem
      hasValue = true
      Continue
    }

    def onError(ex: Throwable): Unit = {
      upstreamError = ex
      upstreamIsDone = true
    }

    def onComplete(): Unit = {
      upstreamIsDone = true
    }

    sampler.unsafeSubscribe(new Observer[U] {
      private[this] var samplerIsDone = false

      def onNext(elem: U): Future[Ack] = {
        if (samplerIsDone) Cancel else {
          if (upstreamIsDone)
            signalComplete(upstreamError)
          else if (!hasValue)
            Continue
          else {
            hasValue = shouldRepeatOnSilence
            downstream.onNext(lastValue)
          }
        }
      }

      def onError(ex: Throwable): Unit = {
        signalComplete(ex)
      }

      def onComplete(): Unit = {
        signalComplete()
      }

      private def signalComplete(ex: Throwable = null): Cancel = {
        if (!samplerIsDone) {
          samplerIsDone = true
          if (ex != null) downstream.onError(ex) else
            downstream.onComplete()
        }

        Cancel
      }
    })
  }
}
