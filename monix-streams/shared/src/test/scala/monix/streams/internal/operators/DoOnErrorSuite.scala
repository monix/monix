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

import monix.streams.Observable
import Observable.now
import monix.streams.Observable
import scala.concurrent.duration.Duration.Zero

object DoOnErrorSuite extends BaseOperatorSuite {
  case class DummyException(value: Long) extends RuntimeException

  def createObservable(sourceCount: Int) = Some {
    val o = Observable.unsafeCreate[Long] { s =>
      import s.scheduler

      Observable.range(0, sourceCount)
        .foldLeft(0L)(_ + _)
        .map(x => throw DummyException(x))
        .doOnError(ex => now(ex.asInstanceOf[DummyException].value).subscribe(s))
        .subscribe()
    }

    Sample(o, 1, sum(sourceCount), Zero, Zero)
  }

  def brokenUserCodeObservable(sourceCount: Int, ex: Throwable) = None
  def observableInError(sourceCount: Int, ex: Throwable) = None

  def sum(sourceCount: Int) = {
    (0 until sourceCount).sum
  }
}