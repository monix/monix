package monifu.reactive.channels

import monifu.concurrent.Scheduler
import monifu.reactive.subjects.AsyncSubject
import monifu.reactive.api.BufferPolicy
import monifu.reactive.api.BufferPolicy.Unbounded

/**
 * Represents a [[monifu.reactive.Channel Channel]] that uses an underlying 
 * [[monifu.reactive.subjects.AsyncSubject AsyncSubject]].
 */
final class AsyncChannel[T] private (policy: BufferPolicy, s: Scheduler)
  extends SubjectChannel(AsyncSubject[T]()(s), policy, s)

object AsyncChannel {
  /**
   * Builds a [[monifu.reactive.Channel Channel]] that uses an underlying
   * [[monifu.reactive.subjects.AsyncSubject AsyncSubject]].
   */
  def apply[T](bufferPolicy: BufferPolicy = Unbounded)(implicit s: Scheduler): AsyncChannel[T] = {
    new AsyncChannel[T](bufferPolicy, s)
  }
}