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

package monix.streams.observers.buffers

import java.util.concurrent.ConcurrentLinkedQueue
import monix.streams.{Subscriber, OverflowStrategy, Ack}
import monix.streams.Ack.{Cancel, Continue}
import monix.execution.internal.Platform
import monix.streams.observers.buffers.DropNewBufferedSubscriber.State
import monix.streams.observers.{BufferedSubscriber, SyncSubscriber}
import scala.annotation.tailrec
import scala.concurrent.Future
import scala.util.control.NonFatal
import org.sincron.atomic.Atomic

/**
 * A high-performance and non-blocking [[BufferedSubscriber]] implementation
 * for the [[OverflowStrategy.DropNew DropNew]]
 * overflow strategy.
 */
private[buffers] final class DropNewBufferedSubscriber[-T] private
  (underlying: Subscriber[T], bufferSize: Int, onOverflow: Long => T = null)
  extends BufferedSubscriber[T] with SyncSubscriber[T] { self =>

  require(bufferSize > 0, "bufferSize must be a strictly positive number")

  implicit val scheduler = underlying.scheduler

  // State for managing contention between multiple producers and one consumer
  private[this] val stateRef = Atomic(State())
  // Concurrent queue, producers can push into it without extra synchronization
  // and there's a happens before relationship between `queue.offer` and
  // incrementing `stateRef.itemsToPush`, which we are using on the consumer
  // side in order to know how many items to process and when to stop
  private[this] val queue = new ConcurrentLinkedQueue[T]()
  // Used on the consumer side to split big synchronous workloads in batches
  private[this] val batchSizeModulus = Platform.recommendedBatchSize - 1

  @tailrec
  def onNext(elem: T): Ack = {
    val state = stateRef.get
    // if upstream has completed or downstream canceled, we should cancel
    if (state.upstreamShouldStop) Cancel else {
      // itemsToPush is our current count of active (unprocessed) events
      // and if it exceeds the bufferSize, then we've got an overflow
      if (state.itemsToPush >= bufferSize) {
        // no more room, dropping event
        if (onOverflow == null || stateRef.compareAndSet(state, state.incrementDropped))
          Continue
        else // the compareAndSet has failed, retry
          onNext(elem)
      }
      else if (onOverflow != null && state.eventsDropped > 0) {
        // this branch happens when the buffer is free again, but
        // we've had events dropped that have to be signaled
        val update = state.copy(eventsDropped = 0)

        if (!stateRef.compareAndSet(state, update))
          onNext(elem) // CAS failed, so retry
        else {
          // composing the overflow message; we've got to do error handling
          // because this is a user supplied function
          val shouldContinue = try {
            val message = onOverflow(state.eventsDropped)
            queue.offer(message)
            pushToConsumer(update)
            true
          }
          catch {
            case NonFatal(ex) =>
              onError(ex)
              false
          }

          if (shouldContinue)
            onNext(elem)
          else
            Cancel
        }
      }
      else {
        // everything normal, we're just going to push our event
        queue.offer(elem)
        pushToConsumer(state)
        Continue
      }
    }
  }

  def onError(ex: Throwable): Unit = {
    signalCompleteOrError(ex)
  }

  def onComplete(): Unit = {
    signalCompleteOrError(null)
  }

  /**
   * Reusable logic for signaling a completion event or an error downstream,
   * used in both `onError` and `onComplete`. The `error` param should
   * be `null` for signaling a normal `onComplete`.
   */
  private def signalCompleteOrError(error: Throwable): Unit = {
    /* The purpose of `startToComplete` is to declare to the upstream
     * that a completion operation is in effect, without the consumer
     * run-loop noticing. This is because we want to stop upstream
     * signals, then compose our overflow message in case we've got
     * dropped events, enqueue it and only after that signal the
     * onComplete or onError event to the consumer.
     */
    @tailrec def startToComplete(error: Throwable): Unit = {
      val state = stateRef.get

      if (!state.upstreamShouldStop) {
        // isDoneInProgress is our signal that gets noticed by our upstream
        // but not by our consumer run-loop
        val update = state.copy(isDoneInProgress = true, eventsDropped = 0)
        if (stateRef.compareAndSet(state, update))
          signal(state, state.eventsDropped, error)
        else
          // at this point all other onComplete/onError calls should be rejected
          startToComplete(error)
      }
    }

    /* Logic for sending our final overflow message, coupled with our final
     * onComplete/onError event to the consumer's run-loop.
     */
    @tailrec def signal(state: State, eventsDropped: Long, error: Throwable): Unit = {
      // we ignore isDoneInProgress, but not upstreamIsComplete
      if (!state.upstreamIsComplete) {
        if (onOverflow != null && eventsDropped > 0) {
          // composing the overflow message; we've got to do error handling
          // because this is a user supplied function
          val ex = try {
            val message = onOverflow(state.eventsDropped)
            queue.offer(message)
            pushToConsumer(state)
            null
          }
          catch {
            case NonFatal(ref) => ref
          }

          if (ex != null) {
            // we've got to signal our original error somewhere
            if (error != null) scheduler.reportFailure(error)
            signal(state, 0, ex)
          }
          else {
            signal(state, 0, error)
          }
        }
        else {
          // signaling the completion of upstream to the consumer's run-loop
          val update = state.copy(upstreamIsComplete = true, errorThrown = error)
          if (stateRef.compareAndSet(state, update))
            pushToConsumer(update)
          else // CAS failed? retry
            signal(stateRef.get, 0, error)
        }
      }
    }

    startToComplete(error)
  }

  /**
   * Function that starts an asynchronous consumer run-loop or in case
   * a run-loop is already in progress this function simply increments
   * the itemsToPush count.
   */
  @tailrec private def pushToConsumer(state: State): Future[Ack] = {
    // whenever itemsToPush is zero, that means there is no active run-loop
    // whenever it is different from zero, that means there is an active loop
    if (state.itemsToPush <= 0) {
      val update = state.copy(itemsToPush = 1)
      if (!stateRef.compareAndSet(state, update)) {
        pushToConsumer(stateRef.get) // retry
      }
      else {
        scheduler.execute(new Runnable { def run() = fastLoop(update, 0, 0) })
        Continue
      }
    }
    else {
      val update = state.incrementItemsToPush
      if (!stateRef.compareAndSet(state, update))
        pushToConsumer(stateRef.get) // retry
      else
        Continue
    }
  }

  private def rescheduled(processed: Int): Unit = {
    fastLoop(stateRef.get, processed, 0)
  }

  /**
   * Starts a consumer run-loop that consumers everything we have in our
   * queue, pushing those events to our downstream observer and then stops.
   */
  @tailrec private def fastLoop(state: State, processed: Int, syncIndex: Int): Unit = {
    // should be called when signaling a complete
    def downstreamSignalComplete(ex: Throwable = null): Unit = {
      stateRef.transformAndGet(_.downstreamComplete)
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
        val next: T = queue.poll()

        if (next != null) {
          val ack = underlying.onNext(next)
          // for establishing whether the next call is asynchronous,
          // note that the check with batchSizeModulus is meant for splitting
          // big synchronous loops in smaller batches
          val nextIndex = if (!ack.isCompleted) 0 else
            (syncIndex + 1) & batchSizeModulus

          if (nextIndex > 0) {
            if (ack == Continue || ack.value.get == Continue.AsSuccess)
              fastLoop(state, processed + 1, nextIndex)
            else if (ack == Cancel || ack.value.get == Cancel.AsSuccess) {
              // ending loop
              stateRef.transformAndGet(_.downstreamComplete)
            }
            else {
              // ending loop
              val ex = ack.value.get.failed.getOrElse(new MatchError(ack.value.get))
              downstreamSignalComplete(ex)
            }
          }
          else ack.onComplete {
            case Continue.AsSuccess =>
              // re-run loop (in different thread)
              rescheduled(processed + 1)

            case Cancel.AsSuccess =>
              // ending loop
              stateRef.transformAndGet(_.downstreamComplete)

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
      else {
        // at this point processed == itemsToPush
        val ref = state.declareProcessed(processed)
        // trying update, if it fails it probably means we've got more
        // items to process and if it succeeds it means we are done
        // note that we don't need to check that itemsToPush is zero
        if (!stateRef.compareAndSet(state, ref)) {
          // concurrent modifications happened, continuing loop
          fastLoop(stateRef.get, processed, syncIndex)
        }
      }
    }
  }
}

private[monix] object DropNewBufferedSubscriber {
  /**
   * Returns an instance of a [[DropNewBufferedSubscriber]]
   * for the [[monix.streams.OverflowStrategy.DropNew DropNew]]
   * overflowStrategy.
   */
  def simple[T](underlying: Subscriber[T], bufferSize: Int): SyncSubscriber[T] = {
    new DropNewBufferedSubscriber[T](underlying, bufferSize, null)
  }

  /**
   * Returns an instance of a [[DropNewBufferedSubscriber]]
   * for the [[monix.streams.OverflowStrategy.DropNew DropNew]]
   * overflowStrategy.
   */
  def withSignal[T](underlying: Subscriber[T], bufferSize: Int, onOverflow: Long => T): SyncSubscriber[T] = {
    new DropNewBufferedSubscriber[T](underlying, bufferSize, onOverflow)
  }

  /** State used in our implementation to manage concurrency */
  private case class State(
    itemsToPush: Int = 0,
    eventsDropped: Long = 0L,
    upstreamIsComplete: Boolean = false,
    downstreamIsDone: Boolean = false,
    isDoneInProgress: Boolean = false,
    errorThrown: Throwable = null) {

    def upstreamShouldStop: Boolean = {
      upstreamIsComplete || downstreamIsDone || isDoneInProgress
    }

    def downstreamComplete: State = {
      copy(itemsToPush = 0, downstreamIsDone = true)
    }

    def declareProcessed(processed: Int): State = {
      copy(itemsToPush = itemsToPush - processed)
    }

    def incrementDropped: State = {
      copy(eventsDropped = eventsDropped + 1)
    }

    def incrementItemsToPush: State = {
      copy(itemsToPush = itemsToPush + 1)
    }
  }
}
