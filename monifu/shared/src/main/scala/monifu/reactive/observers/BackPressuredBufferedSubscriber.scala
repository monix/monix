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

package monifu.reactive.observers

import monifu.collection.mutable.ConcurrentQueue
import monifu.concurrent.atomic.padded.Atomic
import monifu.reactive.Ack.{Cancel, Continue}
import monifu.reactive.observers.BackPressuredBufferedSubscriber.State
import monifu.reactive.{Ack, Subscriber}
import scala.annotation.tailrec
import scala.concurrent.{Future, Promise}


/**
 * A [[BufferedSubscriber]] implementation for the
 * [[monifu.reactive.OverflowStrategy.BackPressure BackPressured]] 
 * buffer overflowStrategy.
 */
private[reactive] final class BackPressuredBufferedSubscriber[-T] private
  (underlying: Subscriber[T], bufferSize: Int)
  extends BufferedSubscriber[T] { self =>

  require(bufferSize > 0, "bufferSize must be a strictly positive number")

  implicit val scheduler = underlying.scheduler

  // State for managing contention between multiple producers and one consumer
  private[this] val stateRef = Atomic(State())
  // This is a concurrent queue so we can push to it without worries
  private[this] val queue = ConcurrentQueue.empty[T]
  // Used on the consumer side to split big synchronous workloads in batches
  private[this] val batchSizeModulus = scheduler.env.batchSize - 1

  def onNext(elem: T): Future[Ack] = {
    val state = stateRef.get
    // if upstream has completed or downstream canceled, we should cancel
    if (state.upstreamShouldStop) Cancel else {
      queue.offer(elem)
      pushToConsumer(state)
    }
  }

  /**
   * Reusable logic for signaling a completion event or an error downstream,
   * used in both `onError` and `onComplete`. The `error` param should
   * be `null` for signaling a normal `onComplete`.
   */
  @tailrec private def signalCompleteOrError(ex: Throwable): Unit = {
    val state = stateRef.get

    if (!state.upstreamShouldStop) {
      // signaling the completion of upstream to the consumer's run-loop
      val update = state.copy(upstreamIsComplete = true, errorThrown = ex)
      if (stateRef.compareAndSet(state, update))
        pushToConsumer(update)
      else // CAS failed, retry
        signalCompleteOrError(ex)
    }
  }

  def onError(ex: Throwable): Unit = {
    signalCompleteOrError(ex)
  }

  def onComplete() = {
    signalCompleteOrError(null)
  }

  /**
   * Function that starts an asynchronous consumer run-loop or in case
   * a run-loop is already in progress this function increments the
   * itemsToPush count. And in case we've exceeded the bufferSize, then
   * we start to apply back-pressure.
   */
  @tailrec private def pushToConsumer(state: State): Future[Ack] = {
    // no run-loop is active? then we need to start one
    if (state.itemsToPush <= 0) {
      val update = state.copy(
        nextAckPromise = Promise[Ack](),
        appliesBackPressure = false,
        itemsToPush = 1)

      if (!stateRef.compareAndSet(state, update)) {
        // CAS failed, retry
        pushToConsumer(stateRef.get)
      }
      else {
        scheduler.execute(new Runnable { def run() = fastLoop(update, 0, 0) })
        Continue
      }
    }
    else {
      val appliesBackPressure = state.shouldBackPressure(bufferSize)
      val update = state.copy(
        itemsToPush = state.itemsToPush + 1,
        appliesBackPressure = appliesBackPressure)

      if (!stateRef.compareAndSet(state, update)) {
        // CAS failed, retry
        pushToConsumer(stateRef.get)
      }
      else if (appliesBackPressure) {
        // the buffer is full, so we need to apply back-pressure
        state.nextAckPromise.future
      }
      else {
        // everything normal, buffer is not full
        Continue
      }
    }
  }

  // Needed in fastLoop for usage in asynchronous callbacks
  private def rescheduled(processed: Int): Unit = {
    fastLoop(stateRef.get, processed, 0)
  }

  /**
   * Starts a consumer run-loop that consumers everything we have in our
   * queue, pushing those events to our downstream observer and then stops.
   */
  @tailrec private def fastLoop(state: State, processed: Int, syncIndex: Int): Unit = {
    // should be called when downstream is canceling or triggering a failure
    def downstreamSignalCancel(): Unit = {
      val ref = stateRef.transformAndGet(_.downstreamComplete)
      ref.nextAckPromise.success(Cancel)
    }

    // should be called when signaling a complete
    def downstreamSignalComplete(ex: Throwable = null): Unit = {
      downstreamSignalCancel()
      if (ex != null)
        underlying.onError(ex)
      else
        underlying.onComplete()
    }

    // We protect the downstream, only doing this as long as the downstream
    // hasn't canceled, or as long as we haven't signaled an onComplete or an
    // onError event yet
    if (!state.downstreamIsDone) {
      val next: T = queue.poll()

      if (next != null) {
        val ack = underlying.onNext(next)
        val nextIndex = if (!ack.isCompleted) 0 else
          (syncIndex + 1) & batchSizeModulus

        if (nextIndex > 0) {
          if (ack == Continue || ack.value.get == Continue.IsSuccess)
            fastLoop(state, processed + 1, nextIndex)
          else if (ack == Cancel || ack.value.get == Cancel.IsSuccess) {
            // ending loop
            downstreamSignalCancel()
          }
          else {
            // ending loop
            val ex = ack.value.get.failed.getOrElse(new MatchError(ack.value.get))
            downstreamSignalComplete(ex)
          }
        }
        else ack.onComplete {
          case Continue.IsSuccess =>
            // re-run loop (in different thread)
            rescheduled(processed + 1)

          case Cancel.IsSuccess =>
            // ending loop
            downstreamSignalCancel()

          case failure =>
            // ending loop
            val ex = failure.failed.getOrElse(new MatchError(failure))
            downstreamSignalComplete(ex)
        }
      }
      else if (state.upstreamIsComplete || state.errorThrown != null) {
        // Race-condition check, but if upstreamIsComplete=true is visible,
        // then the queue should be fully published because there's a clear
        // happens-before relationship between queue.offer() and upstreamIsComplete=true
        // NOTE: errors have priority, so in case of an error seen, then the loop is stopped
        if (!queue.isEmpty) {
          fastLoop(stateRef.get, processed, syncIndex) // re-run loop
        }
        else {
          // ending loop
          try downstreamSignalComplete(state.errorThrown) finally
            queue.clear() // for GC purposes
        }
      }
      else {
        val ref = stateRef.transformAndGet(_.declareProcessed(processed))
        // this really has to be LESS-or-equal
        if (ref.itemsToPush <= 0) {
          // race-condition check
          if (!queue.isEmpty && stateRef.compareAndSet(ref, ref.copy(itemsToPush = 1)))
            fastLoop(ref, 0, syncIndex)
          else
            // if in back-pressure mode, unblock
            ref.nextAckPromise.success(Continue)
        }
        else {
          // if the queue is non-empty (i.e. concurrent modifications
          // might have happened) then start all over again
          fastLoop(ref, 0, syncIndex)
        }
      }
    }
  }
}

private[reactive] object BackPressuredBufferedSubscriber {
  /** Builder for [[BackPressuredBufferedSubscriber]] */
  def apply[T](underlying: Subscriber[T], bufferSize: Int) =
    new BackPressuredBufferedSubscriber[T](underlying, bufferSize)

  /** State used in our implementation to manage concurrency */
  private case class State(
    itemsToPush: Int = 0,
    nextAckPromise: Promise[Ack] = Promise(),
    appliesBackPressure: Boolean = false,
    upstreamIsComplete: Boolean = false,
    downstreamIsDone: Boolean = false,
    errorThrown: Throwable = null) {

    def upstreamShouldStop: Boolean = {
      upstreamIsComplete || downstreamIsDone
    }

    def shouldBackPressure(bufferSize: Int): Boolean = {
      !upstreamIsComplete && (appliesBackPressure || itemsToPush >= bufferSize)
    }

    def downstreamComplete: State = {
      copy(itemsToPush = 0, downstreamIsDone = true)
    }

    def declareProcessed(processed: Int): State = {
      val count = itemsToPush - (if (processed > 0) processed else 1)
      copy(itemsToPush = if (count > 0) count else 0)
    }
  }
}
