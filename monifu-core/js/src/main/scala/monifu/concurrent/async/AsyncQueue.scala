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
 
package monifu.concurrent.async

import scala.collection.mutable
import scala.concurrent.{Future, Promise}


final class AsyncQueue[T] private (elems: T*) {
  def poll(): Future[T] = {
    if (items.nonEmpty) {
      val item = items.dequeue()
      Future.successful(item)
    }
    else {
      val p = Promise[T]()
      promises.enqueue(p)
      p.future
    }
  }

  def offer(elem: T): Unit = {
    if (promises.nonEmpty) {
      val p = promises.dequeue()
      p.success(elem)
    }
    else {
      items.enqueue(elem)
    }
  }
  
  def clear(): Unit = {
    items.clear()
    promises.clear()
  }

  private[this] val items = mutable.Queue.empty[T]
  private[this] val promises = mutable.Queue.empty[Promise[T]]
}

object AsyncQueue {
  def apply[T](elems: T*) = new AsyncQueue[T](elems: _*)
  def empty[T] = new AsyncQueue[T]()
}
