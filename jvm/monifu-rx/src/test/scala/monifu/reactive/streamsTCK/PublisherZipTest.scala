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

package monifu.reactive.streamsTCK

import monifu.reactive.Observable
import monifu.concurrent.Implicits.globalScheduler

class PublisherZipTest extends PublisherVerification[Int]({
  val obs1 = Observable.repeat(1)
  val obs2 = Observable.repeat(2)
  val obs3 = Observable.repeat(3)
  val obs4 = Observable.repeat(4)

  Observable.zip(obs1, obs2, obs3, obs4)
    .map { case (x1, x2, x3, x4) => x1 + x2 + x3 + x4 }
})
