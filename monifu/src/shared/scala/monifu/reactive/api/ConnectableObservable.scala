package monifu.reactive.api

import monifu.reactive.{Observer, Observable}
import monifu.concurrent.{Scheduler, Cancelable}
import monifu.reactive.subjects.Subject
import monifu.concurrent.cancelables.BooleanCancelable
import monifu.concurrent.atomic.Atomic

/**
 * A `ConnectableObservable` resembles an ordinary observable, except that it does not
 * begin emitting items when it is subscribed to, but only after its
 * [[ConnectableObservable#connect connect]] method is called.
 *
 * Useful for turning a cold observable into a hot observable
 * (i.e. same source for all subscribers).
 */
trait ConnectableObservable[+T] extends Observable[T] {
  /**
   * Call a ConnectableObservable's connect() method to instruct it to begin emitting the
   * items from its underlying [[Observable]] to its [[monifu.reactive.Observer Observers]].
   */
  def connect(): Cancelable

  /**
   * Returns an observable sequence that stays connected to the source as long
   * as there is at least one subscription to the observable sequence.
   */
  def refCount(): Observable[T]
}

object ConnectableObservable {
  def apply[T](source: Observable[T], subject: Subject[T], s: Scheduler): ConnectableObservable[T] =
    new ConnectableObservable[T] {
      implicit val scheduler = s

      def subscribe(observer: Observer[T]): Cancelable =
        subject.subscribe(observer)

      def connect(): Cancelable =
        source.subscribe(subject)

      def refCount(): Observable[T] = {
        val subscriptionsCount = Atomic(0)
        val mainSubscription = Atomic(null : Cancelable)

        Observable.create { observer =>
          val subjectSub = subject.subscribe(observer)
          if (subscriptionsCount.getAndIncrement() == 0) {
            mainSubscription.set(source.subscribe(subject))
          }

          BooleanCancelable {
            subjectSub.cancel()
            if (subscriptionsCount.decrementAndGet() == 0) {
              val ms = mainSubscription.getAndSet(null)
              if (ms != null) ms.cancel()
            }
          }
        }
      }
  }
}
