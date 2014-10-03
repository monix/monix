package monifu.reactive.builders

import monifu.concurrent.Scheduler
import monifu.reactive.Ack.Continue
import monifu.reactive.Observable

object repeat {
  /**
   * Creates an Observable that continuously emits the given ''item'' repeatedly.
   */
  def apply[T](elems: T*)(implicit s: Scheduler): Observable[T] = {
    if (elems.size == 0)
      Observable.empty
    else if (elems.size == 1) {
      Observable.create { o =>
        def loop(elem: T): Unit =
          o.onNext(elem).onSuccess {
            case Continue =>
              loop(elem)
          }

        loop(elems.head)
      }
    }
    else
      Observable.from(elems).repeat
  }
}
