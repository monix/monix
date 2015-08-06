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

import monifu.reactive.Observable
import concurrent.duration._

object DebounceSuite extends BaseOperatorSuite {
  def waitFirst = 1.second
  def waitNext = 1.second

  def observable(sourceCount: Int) = Some {
    val source = Observable.create[Long](_.observer.onNext(1L))
    val o = source.debounce(1.second).take(sourceCount)
    Sample(o, count(sourceCount), sum(sourceCount), waitFirst, waitNext)
  }

  def count(sourceCount: Int) = {
    sourceCount
  }

  def sum(sourceCount: Int) = {
    sourceCount
  }

  def observableInError(sourceCount: Int, ex: Throwable) = None
  def brokenUserCodeObservable(sourceCount: Int, ex: Throwable) = None
}
