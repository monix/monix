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

package monifu.collection.mutable

import java.util.concurrent.ConcurrentLinkedQueue
import scala.collection.JavaConverters._
import scala.collection.generic.{GenericTraversableTemplate, CanBuildFrom, GenericCompanion, TraversableFactory}
import scala.collection.{mutable, Iterable}


/**
 * An efficient queue data-structure.
 *
 * On top of the JVM it is backed by a `java.util.concurrent.ConcurrentLinkedQueue`
 * so it can be used in a multi-threading context. On top of Scala.js / Javascript
 * it is backed by a `scala.collection.mutable.Queue`.
 *
 * Contrary to Scala best-practices, the `poll()` method that pulls elements from our
 * queue is returning `null` in case the queue is empty.
 */
private[monifu] final class ConcurrentQueue[A](elems: A*)
  extends Iterable[A]
  with GenericTraversableTemplate[A, ConcurrentQueue]
  with mutable.Cloneable[ConcurrentQueue[A]]
  with Serializable {

  private[this] val underlying = {
    val instance = new ConcurrentLinkedQueue[A]()
    instance.addAll(elems.asJava)
    instance
  }

  override def companion: GenericCompanion[ConcurrentQueue] =
    ConcurrentQueue

  override def newBuilder: mutable.Builder[A, ConcurrentQueue[A]] =
    companion.newBuilder[A]

  /**
   * Enqueues one element in this queue
   *
   * Throws `NullPointerException` if the given `elem` is null.
   */
  def offer(elem: A): Unit = {
    underlying.offer(elem)
  }

  /**
   * Appends all of the elements in the specified collection to the end of
   * this queue, in the order that they are returned by the specified
   * collection's iterator. Attempts to `addAll` of a queue to
   * itself result in `IllegalArgumentException`.
   *
   * Throws `NullPointerException` if the specified collection or any
   * of its elements are null.
   *
   * @param elems the elements to be inserted into this queue
   * @return `true` if this queue changed as a result of the call
   */
  def addAll(elems: A*): Unit = {
    underlying.addAll(elems.asJava)
  }

  /**
   * Returns the first element in the queue, and removes this element
   * from the queue.
   *
   * @return the first element of the queue or `null` in case the
   *         queue is empty.
   */
  def poll(): A = {
    underlying.poll()
  }

  /**
   * Removes all of the elements from this queue.
   * The queue will be empty after this call returns.
   *
   * This implementation repeatedly invokes [[poll]] until it
   * returns `null`.
   */
  def clear(): Unit = {
    underlying.clear()
  }

  def iterator: Iterator[A] = {
    underlying.iterator().asScala
  }

  override def seq: Iterable[A] = this

  override def clone(): ConcurrentQueue[A] = {
    new ConcurrentQueue[A](toSeq : _*)
  }

  override def isEmpty: Boolean = {
    underlying.isEmpty
  }

  override def nonEmpty: Boolean = {
    !underlying.isEmpty
  }
}

/**
 * An efficient queue data-structure.
 *
 * On top of the JVM it is backed by a `java.util.concurrent.ConcurrentLinkedQueue`
 * so it can be used in a multi-threading context. On top of Scala.js / Javascript
 * it is backed by a `scala.collection.mutable.Queue`.
 *
 * Contrary to Scala best-practices, the `poll()` method that pulls elements from our
 * queue is returning `null` in case the queue is empty.
 */
private[monifu] object ConcurrentQueue extends TraversableFactory[ConcurrentQueue] {
  implicit def canBuildFrom[A]: CanBuildFrom[Coll, A, ConcurrentQueue[A]] =
    ReusableCBF.asInstanceOf[GenericCanBuildFrom[A]]

  def newBuilder[A]: mutable.Builder[A, ConcurrentQueue[A]] =
    new mutable.Builder[A, ConcurrentQueue[A]] {
      private[this] val queue = new ConcurrentQueue[A]()

      def +=(elem: A): this.type = {
        queue.offer(elem)
        this
      }

      def result(): ConcurrentQueue[A] = {
        queue
      }

      def clear(): Unit = {
        queue.clear()
      }
    }
}
