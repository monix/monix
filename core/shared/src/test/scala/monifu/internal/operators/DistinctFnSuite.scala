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

package monifu.internal.operators

import monifu.Observable
import scala.concurrent.duration.Duration.Zero

object DistinctFnSuite extends BaseOperatorSuite {
  case class Val(x: Long)
  def createObservable(sourceCount: Int) = Some {
    val o = Observable.range(0, sourceCount)
      .flatMap(i => Observable.from(Val(i), Val(i), Val(i)))
      .distinct(_.x)
      .map(_.x)

    Sample(o, count(sourceCount), sum(sourceCount), Zero, Zero)
  }

  def observableInError(sourceCount: Int, ex: Throwable) = Some {
    val source = Observable.range(0, sourceCount)
      .flatMap(i => Observable.from(i, i, i))

    val o = source.endWithError(ex)
      .map(Val.apply)
      .distinct(_.x)
      .map(_.x)

    Sample(o, count(sourceCount), sum(sourceCount), Zero, Zero)
  }

  def brokenUserCodeObservable(sourceCount: Int, ex: Throwable) = Some {
    val o = Observable.range(0, sourceCount)
      .flatMap(i => Observable.from(Val(i), Val(i), Val(i)))
      .distinct(i => if (i.x == sourceCount-1) throw ex else i.x)
      .map(_.x)

    Sample(o, count(sourceCount-1), sum(sourceCount-1), Zero, Zero)
  }

  def count(sourceCount: Int) = sourceCount
  def sum(sourceCount: Int) = sourceCount * (sourceCount - 1) / 2
}