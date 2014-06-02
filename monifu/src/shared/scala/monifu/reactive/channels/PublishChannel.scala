package monifu.reactive.channels

import monifu.concurrent.Scheduler
import monifu.reactive.subjects.PublishSubject

/**
 * Represents a [[monifu.reactive.Channel Channel]] that uses an underlying [[PublishSubject]].
 */
final class PublishChannel[T] private (s: Scheduler)
  extends SubjectChannel(PublishSubject[T]()(s), s)

object PublishChannel {
  /**
   * Builds a [[monifu.reactive.Channel Channel]] that uses an underlying [[PublishSubject]].
   */
  def apply[T]()(implicit s: Scheduler): PublishChannel[T] = {
    new PublishChannel[T](s)
  }
}
