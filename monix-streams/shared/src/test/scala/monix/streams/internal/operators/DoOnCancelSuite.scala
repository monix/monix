/*
 * Copyright (c) 2014-2016 by its authors. Some rights reserved.
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

package monix.streams.internal.operators

import org.sincron.atomic.Atomic
import monix.streams.{Observer, Observable, Ack}
import monix.streams.Ack.{Continue, Cancel}
import monix.streams.Observer
import Observable.now
import scala.concurrent.duration.Duration.Zero

object DoOnCancelSuite extends BaseOperatorSuite {
  case class DummyException(value: Long) extends RuntimeException

  def createObservable(sourceCount: Int) = Some {
    val o = Observable.unsafeCreate[Long] { s =>
      implicit val ec = s.scheduler

      val sum = Atomic(0L)
      val source = Observable.range(0, sourceCount)
        .doWork(sum.add)
        .doOnCanceled(now(sum.get).subscribe(s))

      source.subscribe(new Observer[Long] {
        def onError(ex: Throwable) = ()
        def onComplete() = ()
        def onNext(elem: Long) = {
          if (elem == sourceCount - 1)
            Cancel
          else
            Continue
        }
      })
    }

    Sample(o, count(sourceCount), sum(sourceCount), Zero, Zero)
  }

  def count(sourceCount: Int) = 1
  def brokenUserCodeObservable(sourceCount: Int, ex: Throwable) = None
  def observableInError(sourceCount: Int, ex: Throwable) = None

  def sum(sourceCount: Int) = {
    (0 until sourceCount).sum
  }
}