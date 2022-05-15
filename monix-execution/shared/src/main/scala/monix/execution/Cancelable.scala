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

package monix.execution

import monix.execution.atomic.AtomicAny
import monix.execution.internal.Platform
import monix.execution.schedulers.TrampolinedRunnable
import scala.collection.mutable.ListBuffer
import scala.concurrent.Promise
import scala.util.control.NonFatal

/** Represents a one-time idempotent action that can be used
  * to cancel async computations, or to release resources that
  * active data sources are holding.
  *
  * It is equivalent to `java.io.Closeable`, but without the I/O focus, or
  * to `IDisposable` in Microsoft .NET, or to `akka.actor.Cancellable`.
  */
trait Cancelable extends Serializable {
  /** Cancels the unit of work represented by this reference.
    *
    * Guaranteed idempotency - calling it multiple times should have
    * the same side-effect as calling it only once. Implementations
    * of this method should also be thread-safe.
    */
  def cancel(): Unit
}

object Cancelable {
  /** Builds a [[monix.execution.Cancelable Cancelable]]. */
  def apply(): Cancelable =
    empty

  /** Builds a [[monix.execution.Cancelable Cancelable]] that executes the given
    * `callback` when calling [[Cancelable.cancel cancel]].
    */
  def apply(callback: () => Unit): Cancelable =
    new CancelableTask(callback)

  /** Returns a dummy [[Cancelable]] that doesn't do anything. */
  val empty: Cancelable =
    new Empty {
      def cancel(): Unit = ()
      override def toString = "monix.execution.Cancelable.empty"
    }

  /** Builds a [[Cancelable]] reference from a sequence,
    * cancelling everything on `cancel`.
    */
  def collection(refs: Cancelable*): Cancelable =
    collection(refs)

  /** Builds a [[Cancelable]] reference from a sequence,
    * cancelling everything on `cancel`.
    */
  def collection(seq: Iterable[Cancelable]): Cancelable =
    apply { () =>
      cancelAll(seq)
    }

  /** Wraps a collection of cancelable references into a `Cancelable`
    * that will cancel them all by triggering a trampolined async
    * boundary first, in order to prevent stack overflows.
    */
  def trampolined(refs: Cancelable*)(implicit s: Scheduler): Cancelable =
    trampolined(refs)

  /** Wraps a collection of cancelable references into a `Cancelable`
    * that will cancel them all by triggering a trampolined async
    * boundary first, in order to prevent stack overflows.
    */
  def trampolined(seq: Iterable[Cancelable])(implicit s: Scheduler): Cancelable =
    new CollectionTrampolined(seq, s)

  /** Builds a [[Cancelable]] out of a Scala `Promise`, completing the
    * promise with the given `Throwable` on cancel.
    */
  def fromPromise[A](p: Promise[A], e: Throwable): Cancelable =
    new Cancelable {
      def cancel(): Unit = { p.tryFailure(e); () }
    }

  /** Given a collection of cancelables, cancel them all.
    *
    * This function collects non-fatal exceptions and throws them all
    * at the end as a composite, in a platform specific way:
    *
    *  - for the JVM "Suppressed Exceptions" are used
    *  - for JS they are wrapped in a `CompositeException`
    */
  def cancelAll(seq: Iterable[Cancelable]): Unit = {
    val errors = ListBuffer.empty[Throwable]
    val cursor = seq.iterator
    while (cursor.hasNext) {
      try cursor.next().cancel()
      catch { case ex if NonFatal(ex) => errors += ex }
    }

    errors.toList match {
      case one :: Nil =>
        throw one
      case first :: rest =>
        throw Platform.composeErrors(first, rest: _*)
      case _ =>
        () // Nothing
    }
  }

  /** Interface for cancelables that are empty or already canceled. */
  trait Empty extends Cancelable with IsDummy

  /** Marker for cancelables that are dummies that can be ignored. */
  trait IsDummy { self: Cancelable => }

  private final class CancelableTask(cb: () => Unit) extends Cancelable {

    private[this] val callbackRef = /*_*/ AtomicAny(cb) /*_*/

    def cancel(): Unit = {
      // Setting the callback to null with a `getAndSet` is solving
      // two problems: `cancel` is idempotent, plus we allow the garbage
      // collector to collect the task.
      /*_*/
      val callback = callbackRef.getAndSet(null)
      if (callback != null) callback()
      /*_*/
    }
  }

  private final class CollectionTrampolined(refs: Iterable[Cancelable], sc: Scheduler)
    extends Cancelable with TrampolinedRunnable {

    private[this] val atomic = /*_*/ AtomicAny(refs) /*_*/

    def cancel(): Unit =
      sc.execute(this)

    def run(): Unit = {
      /*_*/
      val refs = atomic.getAndSet(null)
      if (refs ne null) cancelAll(refs)
      /*_*/
    }
  }
}
