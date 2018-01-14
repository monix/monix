/*
 * Copyright (c) 2014-2018 by The Monix Project Developers.
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

package monix.reactive.internal.consumers

import monix.eval.Callback
import monix.execution.Scheduler
import monix.execution.cancelables.AssignableCancelable
import monix.reactive.observers.Subscriber
import monix.reactive.{Consumer, Observable, Pipe}

/** Implementation for [[monix.reactive.Consumer.transformInput]]. */
private[reactive]
final class TransformInputConsumer[In2, -In, +R]
  (source: Consumer[In, R], f: Observable[In2] => Observable[In])
  extends Consumer[In2, R] {

  def createSubscriber(cb: Callback[R], s: Scheduler): (Subscriber[In2], AssignableCancelable) = {
    val (input1, conn) = source.createSubscriber(cb, s)

    val (input2, output1) = Pipe.publishToOne[In2].transform(f).unicast
    output1.unsafeSubscribeFn(input1)

    (Subscriber(input2, input1.scheduler), conn)
  }
}
