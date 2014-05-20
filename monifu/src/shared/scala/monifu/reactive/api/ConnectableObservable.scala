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
  final def refCount(): Observable[T] = {
    var count = 0
    val gate = new AnyRef
    var subscription = null : Cancelable

    Observable.create { observer =>
      gate.synchronized {
        val childSubscription = subscribe(observer)
        if (count == 0) subscription = connect()
        count += 1

        Cancelable(gate.synchronized {
          childSubscription.cancel()
          count -= 1

          if (count == 0) {
            subscription.cancel()
            subscription = null
          }
        })
      }
    }
  }}

object ConnectableObservable {
  def apply[T](source: Observable[T], subject: Subject[T], s: Scheduler): ConnectableObservable[T] =
    new ConnectableObservable[T] {
      implicit val scheduler = s

      def subscribe(observer: Observer[T]): Cancelable =
        subject.subscribe(observer)

      def connect(): Cancelable =
        source.subscribe(subject)
  }
}
