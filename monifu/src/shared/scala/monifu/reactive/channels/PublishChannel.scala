package monifu.reactive.channels

import monifu.concurrent.Scheduler
import monifu.reactive.subjects.PublishSubject
import monifu.reactive.api.BufferPolicy
import monifu.reactive.api.BufferPolicy.Unbounded

/**
 * Represents a [[monifu.reactive.Channel Channel]] that uses an underlying
 * [[monifu.reactive.subjects.PublishSubject PublishSubject]].
 */
final class PublishChannel[T] private (policy: BufferPolicy, s: Scheduler)
  extends SubjectChannel(PublishSubject[T]()(s), policy, s)

object PublishChannel {
  /**
   * Builds a [[monifu.reactive.Channel Channel]] that uses an underlying
   * [[monifu.reactive.subjects.PublishSubject PublishSubject]].
   */
  def apply[T](bufferPolicy: BufferPolicy = Unbounded)(implicit s: Scheduler): PublishChannel[T] = {
    new PublishChannel[T](bufferPolicy, s)
  }
}
