/*
 * Copyright (c) 2014-2020 by The Monix Project Developers.
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

package monix.execution.internal

import monix.execution.atomic.PaddingStrategy
import monix.execution.atomic.AtomicAny
import monix.execution.internal.GenericSemaphore.Listener
import scala.annotation.tailrec
import scala.collection.immutable.Queue

private[monix] abstract class GenericSemaphore[CancelToken] protected (provisioned: Long, ps: PaddingStrategy)
  extends Serializable {

  import GenericSemaphore.State
  require(provisioned >= 0, "provisioned >= 0")

  private[this] val stateRef =
    AtomicAny.withPadding(GenericSemaphore.initialState(provisioned), ps)

  protected def emptyCancelable: CancelToken
  protected def makeCancelable(f: Listener[Unit] => Unit, p: Listener[Unit]): CancelToken

  protected final def unsafeAvailable(): Long =
    stateRef.get().available

  protected final def unsafeCount(): Long =
    stateRef.get().count

  @tailrec
  protected final def unsafeAcquireN(n: Long, await: Listener[Unit]): CancelToken = {
    assert(n >= 0, "n must be positive")

    stateRef.get() match {
      case current @ State(available, awaitPermits, _) =>
        val stillAvailable = available - n
        if (stillAvailable >= 0) {
          val update = current.copy(available = stillAvailable)

          if (!stateRef.compareAndSet(current, update))
            unsafeAcquireN(n, await) // retry
          else {
            await(Constants.eitherOfUnit)
            emptyCancelable
          }
        } else {
          val tuple = (-stillAvailable, await)
          val update = current.copy(available = 0, awaitPermits = awaitPermits.enqueue(tuple))

          if (!stateRef.compareAndSet(current, update))
            unsafeAcquireN(n, await) // retry
          else
            makeCancelable(cancelAcquisition(n, isAsync = false), await)
        }
    }
  }

  @tailrec
  protected final def unsafeAsyncAcquireN(n: Long, await: Listener[Unit]): CancelToken = {
    assert(n >= 0, "n must be positive")

    stateRef.get() match {
      case current @ State(available, awaitPermits, _) =>
        val stillAvailable = available - n
        if (stillAvailable >= 0) {
          val update = current.copy(available = stillAvailable)

          if (!stateRef.compareAndSet(current, update))
            unsafeAsyncAcquireN(n, await) // retry
          else {
            await(Constants.eitherOfUnit)
            makeCancelable(cancelAcquisition(n, isAsync = true), await)
          }
        } else {
          val tuple = (-stillAvailable, await)
          val update = current.copy(available = 0, awaitPermits = awaitPermits.enqueue(tuple))

          if (!stateRef.compareAndSet(current, update))
            unsafeAsyncAcquireN(n, await) // retry
          else
            makeCancelable(cancelAcquisition(n, isAsync = true), await)
        }
    }
  }

  @tailrec
  protected final def unsafeTryAcquireN(n: Long): Boolean =
    stateRef.get() match {
      case current @ State(available, _, _) =>
        val stillAvailable = available - n
        if (stillAvailable >= 0) {
          val update = current.copy(available = stillAvailable)
          if (!stateRef.compareAndSet(current, update))
            unsafeTryAcquireN(n) // retry
          else
            true
        } else {
          false
        }
    }

  @tailrec
  protected final def unsafeReleaseN(n: Long): Unit = {
    assert(n >= 0, "n must be positive")

    stateRef.get() match {
      case current @ State(available, promises, awaitReleases) =>
        // No promises made means we should only increase
        if (promises.isEmpty) {
          val available2 = available + n
          val triggeredReleases = current.triggerAwaitReleases(available2)
          var promisesAwaitingRelease: List[Listener[Unit]] = Nil

          val awaitReleases2 = triggeredReleases match {
            case None => awaitReleases
            case Some((list, newValue)) =>
              promisesAwaitingRelease = list
              newValue
          }

          val update = current.copy(
            available = available2,
            awaitReleases = awaitReleases2
          )

          if (!stateRef.compareAndSet(current, update))
            unsafeReleaseN(n)
          else if (promisesAwaitingRelease ne Nil)
            triggerAll(promisesAwaitingRelease)
        } else {
          val ((c, cb), newPromises) =
            if (promises.nonEmpty) promises.dequeue else (null, promises)

          if (c <= n) {
            val rem = n - c
            val update = current.copy(awaitPermits = newPromises)

            if (!stateRef.compareAndSet(current, update))
              unsafeReleaseN(n)
            else {
              cb(Constants.eitherOfUnit)
              // Keep going - try to release another one
              if (rem > 0) unsafeReleaseN(rem)
            }
          } else {
            val rem = c - n
            val update = current.copy(awaitPermits = (rem, cb) +: newPromises)
            if (!stateRef.compareAndSet(current, update))
              unsafeReleaseN(n)
          }
        }
    }
  }

  @tailrec
  protected final def unsafeAwaitAvailable(n: Long, await: Listener[Unit]): CancelToken =
    stateRef.get() match {
      case current @ State(available, _, awaitReleases) =>
        if (available >= n) {
          await(Constants.eitherOfUnit)
          emptyCancelable
        } else {
          val update = current.copy(awaitReleases = (n -> await) :: awaitReleases)
          if (!stateRef.compareAndSet(current, update))
            unsafeAwaitAvailable(n, await)
          else
            makeCancelable(cancelAwaitRelease, await)
        }
    }

  private final def triggerAll(promises: Seq[Listener[Unit]]): Unit = {
    val cursor = promises.iterator
    while (cursor.hasNext) cursor.next().apply(Constants.eitherOfUnit)
  }

  private[this] val cancelAwaitRelease: (Listener[Unit] => Unit) = {
    @tailrec def loop(p: Listener[Unit]): Unit = {
      val current: State = stateRef.get()
      val update = current.removeAwaitReleaseRef(p)
      if (!stateRef.compareAndSet(current, update))
        loop(p) // retry
    }
    loop
  }

  private[this] def cancelAcquisition(n: Long, isAsync: Boolean): (Listener[Unit] => Unit) = {
    @tailrec def loop(permit: Listener[Unit]): Unit = {
      val current: State = stateRef.get()

      current.awaitPermits.find(_._2 eq permit) match {
        case None =>
          if (isAsync) unsafeReleaseN(n)
        case Some((m, _)) =>
          val update = current.removeAwaitPermitRef(permit)
          if (!stateRef.compareAndSet(current, update))
            loop(permit) // retry
          else if (n > m)
            unsafeReleaseN(n - m)
      }
    }
    loop
  }
}

private[monix] object GenericSemaphore {
  /** Internal. Reusable initial state. */
  private def initialState(available: Long): State =
    State(available, Queue.empty, Nil)

  /** Callback type used internally. */
  type Listener[A] = Either[Throwable, A] => Unit

  /** Internal. For keeping the state of our
    * [[GenericSemaphore]] in an atomic reference.
    */
  private final case class State(
    available: Long,
    awaitPermits: Queue[(Long, Listener[Unit])],
    awaitReleases: List[(Long, Listener[Unit])]) {

    def count: Long = {
      if (available > 0) available
      else -awaitPermits.map(_._1).sum
    }

    def removeAwaitPermitRef(p: Listener[Unit]): State =
      copy(awaitPermits = awaitPermits.filter { case (_, ref) => ref ne p })

    def removeAwaitReleaseRef(p: Listener[Unit]): State =
      copy(awaitReleases = awaitReleases.filter { case (_, ref) => ref ne p })

    def triggerAwaitReleases(available2: Long): Option[(List[Listener[Unit]], List[(Long, Listener[Unit])])] = {
      assert(available2 >= 0, "n >= 0")

      if (available2 == 0) None
      else
        awaitReleases match {
          case Nil => None
          case list =>
            val cursor = list.iterator
            var toComplete = List.empty[Listener[Unit]]
            var toKeep = List.empty[(Long, Listener[Unit])]

            while (cursor.hasNext) {
              val ref = cursor.next()
              val (awaits, p) = ref
              if (awaits <= available2)
                toComplete = p :: toComplete
              else
                toKeep = ref :: toKeep
            }
            Some((toComplete, toKeep))
        }
    }
  }
}
