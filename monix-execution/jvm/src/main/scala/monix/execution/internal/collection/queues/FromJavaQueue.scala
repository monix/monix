/*
 * Copyright (c) 2014-2022 Monix Contributors.
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

package monix.execution.internal.collection.queues

import java.util
import monix.execution.internal.collection.LowLevelConcurrentQueue
import scala.collection.mutable

private[internal] class FromJavaQueue[A](queue: util.Queue[A]) extends LowLevelConcurrentQueue[A] {

  final def fenceOffer(): Unit = ()
  final def fencePoll(): Unit = ()

  final def isEmpty: Boolean =
    queue.isEmpty

  final def offer(elem: A): Int =
    if (queue.offer(elem)) 0 else 1

  final def poll(): A =
    queue.poll()

  final def clear(): Unit =
    queue.clear()

  final def drainToBuffer(buffer: mutable.Buffer[A], limit: Int): Int = {
    var idx = 0
    var hasElems = true
    while (hasElems && idx < limit) {
      val a = queue.poll()
      if (a != null) {
        buffer += a
        idx += 1
      } else {
        hasElems = false
      }
    }
    idx
  }
}
