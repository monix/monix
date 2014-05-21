package monifu.reactive.internals

import monifu.reactive.subjects.Subject
import monifu.reactive.{Observer, Observable}
import monifu.reactive.api.Ack.Done
import scala.util.control.NonFatal

/**
 * Internal representation of a ref counted multicast Observable, used in
 * [[Observable.multicast]]
 */
protected[monifu] final class MulticastObservable[T](source: Observable[T], subject: Subject[T], refCount: RefCounter)
  extends Observable[T] {

  val scheduler = source.scheduler

  def subscribe(observer: Observer[T]): Unit = {
    source.subscribe(new Observer[T] {
      @volatile var shouldContinue = true

      def onNext(elem: T) = {
        if (shouldContinue) {
          // See Section 6.4. in the Rx Design Guidelines:
          // Protect calls to user code from within an operator
          try {
            if (!refCount.isCanceled)
              observer.onNext(elem)
            else {
              shouldContinue = false
              Done
            }
          }
          catch {
            case NonFatal(ex) =>
              onError(ex)
          }
        }
        else
          Done
      }

      def onCompleted() =
        observer.onCompleted()

      def onError(ex: Throwable) =
        observer.onError(ex)
    })
  }
}
