package monifu.reactive.operators

import monifu.reactive.Ack.Cancel
import monifu.reactive.{Observer, Observable}

object take {
  /**
   * Implementation for [[monifu.reactive.Observable.take]].
   */
  def apply[T](n: Int)(source: Observable[T]): Observable[T] =
    Observable.create { observer =>
      source.unsafeSubscribe(new Observer[T] {
        private[this] var counter = 0
        private[this] var isDone = false

        def onNext(elem: T) = {
          if (n <= 0 && !isDone) {
            isDone = true
            observer.onComplete()
            Cancel
          }
          else if (!isDone && counter < n) {
            // ^^ short-circuit for not endlessly incrementing that number
            counter += 1

            if (counter < n) {
              // this is not the last event in the stream, so send it directly
              observer.onNext(elem)
            }
            else  {
              // last event in the stream, so we need to send the event followed by an EOF downstream
              // after which we signal upstream to the producer that it should stop
              isDone = true
              observer.onNext(elem)
              observer.onComplete()
              Cancel
            }
          }
          else {
            // we already emitted the maximum number of events, so signal upstream
            // to the producer that it should stop sending events
            Cancel
          }
        }

        def onError(ex: Throwable) =
          if (!isDone) {
            isDone = true
            observer.onError(ex)
          }

        def onComplete() =
          if (!isDone) {
            isDone = true
            observer.onComplete()
          }
      })
    }
}
