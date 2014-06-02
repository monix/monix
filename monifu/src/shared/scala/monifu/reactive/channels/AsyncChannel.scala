package monifu.reactive.channels

import monifu.concurrent.Scheduler
import monifu.reactive.subjects.AsyncSubject

/**
 * Represents a [[monifu.reactive.Channel Channel]] that uses an underlying 
 * [[monifu.reactive.subjects.AsyncSubject AsyncSubject]].
 */
final class AsyncChannel[T] private (s: Scheduler)
  extends SubjectChannel(AsyncSubject[T]()(s), s)

object AsyncChannel {
  /**
   * Builds a [[monifu.reactive.Channel Channel]] that uses an underlying
   * [[monifu.reactive.subjects.AsyncSubject AsyncSubject]].
   */
  def apply[T]()(implicit s: Scheduler): AsyncChannel[T] = {
    new AsyncChannel[T](s)
  }
}