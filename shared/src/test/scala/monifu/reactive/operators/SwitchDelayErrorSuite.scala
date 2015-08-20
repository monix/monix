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

import monifu.reactive.{CompositeException, DummyException, Observable}
import scala.concurrent.duration._

object SwitchDelayErrorSuite extends BaseOperatorSuite {
  def createChild() = {
    Observable.interval(1.second).take(2) ++
      Observable.interval(1.second).drop(3)
  }

  def observable(sourceCount: Int) = Some {
    val source = Observable.interval(2.seconds)
      .take(sourceCount)
      .endWithError(DummyException("dummy"))
      .map(i => (if (i < sourceCount-1) createChild() else Observable.interval(1.second)).take(sourceCount))
      .switchDelayError

    val o = source.onErrorRecoverWith {
      case CompositeException(Seq(DummyException("dummy"))) =>
        Observable.unit(10L)
    }

    val count = (sourceCount - 1) * 2 + sourceCount + 1
    val sum = (sourceCount - 1) + (1 until sourceCount).sum + 10
    Sample(o, count, sum, 0.seconds, 1.seconds)
  }

  def observableInError(sourceCount: Int, ex: Throwable) = None
  def brokenUserCodeObservable(sourceCount: Int, ex: Throwable) = None
}

