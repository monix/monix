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

import monifu.reactive.{DummyException, Observable}
import scala.concurrent.duration.Duration.Zero

object OnErrorResumeNextSuite extends BaseOperatorSuite {
  override def observable(sourceCount: Int) = Some {
    val (mid, rest) = divideCount(sourceCount)
    val ex = new DummyException("expected")
    val o1 = createObservableEndingInError(Observable.range(0, mid), ex)
    val o2 = Observable.range(0, rest)

    val o = o1.onErrorResumeNext(o2)

    Sample(o, count(sourceCount), sum(sourceCount), Zero, Zero)
  }

  override def observableInError(sourceCount: Int, ex: Throwable) = Some {
    val (mid, rest) = divideCount(sourceCount)
    val ex1 = new DummyException("expected")
    val o1 = createObservableEndingInError(Observable.range(0, mid), ex1)
    val o2 = createObservableEndingInError(Observable.range(0, rest), ex)

    val o = o1.onErrorResumeNext(o2)

    Sample(o, count(sourceCount), sum(sourceCount), Zero, Zero)
  }

  def count(sourceCount: Int) = sourceCount
  def sum(sourceCount: Int) = sourceCount * (sourceCount - 1) / 2

  private def divideCount(sourceCount: Int): (Int, Int) = {
    val mid = 1
    val rest = sourceCount - mid
    (mid, rest)
  }

  override def brokenUserCodeObservable(sourceCount: Int, ex: Throwable): Option[OnErrorResumeNextSuite.Sample] = None
}
