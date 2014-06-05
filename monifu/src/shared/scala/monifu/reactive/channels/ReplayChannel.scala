package monifu.reactive.channels

import monifu.concurrent.Scheduler
import monifu.reactive.subjects.ReplaySubject
import monifu.reactive.api.BufferPolicy
import monifu.reactive.api.BufferPolicy.Unbounded

/**
 * Represents a [[monifu.reactive.Channel Channel]] that uses an underlying
 * [[monifu.reactive.subjects.ReplaySubject ReplaySubject]].
 */
final class ReplayChannel[T] private (policy: BufferPolicy, s: Scheduler)
  extends SubjectChannel(ReplaySubject[T]()(s), policy, s)

object ReplayChannel {
  /**
   * Builds a [[monifu.reactive.Channel Channel]] that uses an underlying
   * [[monifu.reactive.subjects.ReplaySubject ReplaySubject]].
   */
  def apply[T](bufferPolicy: BufferPolicy = Unbounded)(implicit s: Scheduler): ReplayChannel[T] = {
    new ReplayChannel[T](bufferPolicy, s)
  }
}
