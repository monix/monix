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
 *
 */

package monix.streams.internal.operators

import monix.streams.{Observable, Ack}
import monix.streams.Ack.Continue
import scala.concurrent.duration.Duration
import scala.concurrent.duration.Duration._
import scala.util.Success

object SumSuite extends BaseOperatorSuite {
  def createObservable(sourceCount: Int) = Some {
    val o = Observable.range(0, sourceCount).sum
    Sample(o, count(sourceCount), sum(sourceCount), Zero, Zero)
  }

  def observableInError(sourceCount: Int, ex: Throwable) = Some {
    val o = Observable.unsafeCreate[Long] { subscriber =>
      implicit val s = subscriber.scheduler
      val source = createObservableEndingInError(Observable.range(0, sourceCount), ex).sum

      subscriber.onNext(sum(sourceCount)).onComplete {
        case Success(Continue) =>
          source.subscribe(subscriber)
        case _ => ()
      }
    }

    Sample(o, count(sourceCount), sum(sourceCount), Zero, Zero)
  }

  def count(sourceCount: Int) = 1
  def sum(sourceCount: Int) = (0 until sourceCount).sum
  def brokenUserCodeObservable(sourceCount: Int, ex: Throwable) = None

  def waitForNext = Duration.Zero
  def waitForFirst = Duration.Zero
}
