package monifu.reactive.channels

import monifu.concurrent.Scheduler
import monifu.reactive.subjects.ReplaySubject

/**
 * Represents a [[monifu.reactive.Channel Channel]] that uses an underlying
 * [[monifu.reactive.subjects.ReplaySubject ReplaySubject]].
 */
final class ReplayChannel[T] private (s: Scheduler)
  extends SubjectChannel(ReplaySubject[T]()(s), s)

object ReplayChannel {
  /**
   * Builds a [[monifu.reactive.Channel Channel]] that uses an underlying
   * [[monifu.reactive.subjects.ReplaySubject ReplaySubject]].
   */
  def apply[T]()(implicit s: Scheduler): ReplayChannel[T] = {
    new ReplayChannel[T](s)
  }
}
