package monifu.reactive.operators

import monifu.concurrent.Scheduler
import monifu.concurrent.locks.SpinLock
import monifu.reactive.Ack.Cancel
import monifu.reactive.observers.SynchronousObserver
import monifu.reactive.{Observer, Observable}
import monifu.reactive.internals._

object takeUntilOtherEmits {
  /**
   * Implementation for [[Observable.takeWhileRefIsTrue]].
   */
  def apply[T, U](other: Observable[U])(implicit s: Scheduler) =
    (source: Observable[T]) => {
      Observable.create[T] { observer =>
        // we've got contention between the source and the other observable
        // and we can't use an Atomic here because of `onNext`, so we are
        // forced to use a lock in order to preserve the non-concurrency
        // clause in the contract
        val lock = SpinLock()
        // must be at all times synchronized by lock
        var isDone = false

        def terminate(ex: Throwable = null): Unit =
          lock.enter {
            if (!isDone) {
              isDone = true
              if (ex == null)
                observer.onComplete()
              else
                observer.onError(ex)
            }
          }

        source.unsafeSubscribe(new Observer[T] {
          def onNext(elem: T) = lock.enter {
            if (isDone) Cancel
            else
              observer.onNext(elem).onCancel {
                lock.enter { isDone = true }
              }
          }

          def onError(ex: Throwable): Unit = terminate(ex)
          def onComplete(): Unit = terminate()
        })

        other.unsafeSubscribe(new SynchronousObserver[U] {
          def onNext(elem: U) = {
            terminate()
            Cancel
          }

          def onError(ex: Throwable) = terminate(ex)
          def onComplete() = terminate()
        })
      }
    }
}
