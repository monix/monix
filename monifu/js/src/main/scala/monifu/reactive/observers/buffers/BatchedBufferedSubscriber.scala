/*
 * Copyright (c) 2014-2016 by its authors. Some rights reserved.
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

package monifu.reactive.observers.buffers

import monifu.concurrent.atomic.Atomic
import monifu.reactive.{Subscriber, Ack}
import monifu.reactive.Ack.{Cancel, Continue}
import monifu.reactive.observers.BufferedSubscriber
import monifu.reactive.observers.buffers.BatchedBufferedSubscriber.State
import scala.annotation.tailrec
import scala.collection.mutable
import scala.concurrent.{Future, Promise}

/** A [[BufferedSubscriber]] implementation for the
  * [[monifu.reactive.OverflowStrategy.BackPressure BackPressured]]
  * buffer overflowStrategy that sends events in bundles.
  */
private[monifu] final class BatchedBufferedSubscriber[-T] private
  (underlying: Subscriber[List[T]], bufferSize: Int)
  extends BufferedSubscriber[T] { self =>

  require(bufferSize > 0, "bufferSize must be a strictly positive number")

  implicit val scheduler = underlying.scheduler

  // State for managing contention between multiple producers and one consumer
  private[this] val stateRef = Atomic(State())
  // Concurrent queue, producers can push into it without extra synchronization
  // and there's a happens before relationship between `queue.offer` and
  // incrementing `stateRef.itemsToPush`, which we are using on the consumer
  // side in order to know how many items to process and when to stop
  private[this] val queue = mutable.Queue.empty[T]
  // Used on the consumer side to split big synchronous workloads in batches
  private[this] val batchSizeModulus = scheduler.env.batchSize - 1

  def onNext(elem: T): Future[Ack] = {
    val state = stateRef.get
    // if upstream has completed or downstream canceled, we should cancel
    if (state.upstreamShouldCancel) Cancel else {
      queue.enqueue(elem)
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

    if (!state.upstreamShouldCancel) {
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

  def onComplete(): Unit = {
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
    if (state.itemsToPush == 0) {
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
      // Processing until we're hitting `itemsToPush`
      if (processed < state.itemsToPush) {

        if (queue.nonEmpty) {
          val ack = {
            val buffer = mutable.ListBuffer.empty[T]
            val maxToConsume = batchSizeModulus + 1 - syncIndex
            var consumed = 0
            do {
              val next = queue.dequeue()
              buffer += next
              consumed += 1
            } while (queue.nonEmpty && consumed < maxToConsume)

            underlying.onNext(buffer.toList)
          }

          val consumed = queue.length
          // for establishing whether the next call is asynchronous,
          // note that the check with batchSizeModulus is meant for splitting
          // big synchronous loops in smaller batches
          val nextIndex = if (!ack.isCompleted) 0 else
            (syncIndex + consumed) & batchSizeModulus

          if (nextIndex > 0) {
            if (ack == Continue || ack.value.get == Continue.IsSuccess)
              fastLoop(state, processed + consumed, nextIndex)
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
              rescheduled(processed + consumed)

            case Cancel.IsSuccess =>
              // ending loop
              downstreamSignalCancel()

            case failure =>
              // ending loop
              val ex = failure.failed.getOrElse(new MatchError(failure))
              downstreamSignalComplete(ex)
          }
        }
        else {
          // upstreamIsComplete=true, ending loop
          assert(state.upstreamIsComplete, "upstreamIsComplete should be true")
          try downstreamSignalComplete(state.errorThrown) finally
            queue.clear() // for GC purposes
        }
      }
      else { // if processed == itemsToPush
      val ref = state.declareProcessed(processed)
        // trying update, if it fails it probably means
        // we've got more items to process
        if (stateRef.compareAndSet(state, ref)) {
          // if in back-pressure mode, unblock
          // note that we don't need to check that itemsToPush is zero
          ref.nextAckPromise.success(Continue)
        }
        else {
          // concurrent modifications happened, continuing loop
          fastLoop(stateRef.get, processed, syncIndex)
        }
      }
    }
  }
}

private[monifu] object BatchedBufferedSubscriber {
  /** Builder for [[BatchedBufferedSubscriber]] */
  def apply[T](underlying: Subscriber[List[T]], bufferSize: Int): BatchedBufferedSubscriber[T] =
    new BatchedBufferedSubscriber[T](underlying, bufferSize)

  /** State used in our implementation to manage concurrency */
  private case class State(
    itemsToPush: Int = 0,
    nextAckPromise: Promise[Ack] = Promise(),
    appliesBackPressure: Boolean = false,
    upstreamIsComplete: Boolean = false,
    downstreamIsDone: Boolean = false,
    errorThrown: Throwable = null) {

    def upstreamShouldCancel: Boolean = {
      upstreamIsComplete || downstreamIsDone
    }

    def shouldBackPressure(bufferSize: Int): Boolean = {
      !upstreamIsComplete && (appliesBackPressure || itemsToPush >= bufferSize)
    }

    def downstreamComplete: State = {
      copy(itemsToPush = 0, downstreamIsDone = true)
    }

    def declareProcessed(processed: Int): State = {
      copy(itemsToPush = itemsToPush - processed)
    }
  }
}
