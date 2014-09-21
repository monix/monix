package monifu.reactive.operators

import monifu.concurrent.Scheduler
import monifu.concurrent.cancelables.RefCountCancelable
import monifu.reactive.Ack.{Continue, Cancel}
import monifu.reactive.{Ack, Observer, Observable}
import monifu.reactive.internals._
import scala.concurrent.Promise


object concat {
  /**
   * Implementation for [[Observable.concat]].
   */
  def apply[T,U](implicit ev: T <:< Observable[U], s: Scheduler) =
    (source: Observable[T]) => {
      Observable.create[U] { observerU =>
        source.unsafeSubscribe(new Observer[T] {
          private[this] val refCount = RefCountCancelable(observerU.onComplete())

          def onNext(childObservable: T) = {
            val upstreamPromise = Promise[Ack]()
            val refID = refCount.acquire()

            childObservable.unsafeSubscribe(new Observer[U] {
              def onNext(elem: U) = {
                observerU.onNext(elem)
                  .ifCancelTryCanceling(upstreamPromise)
              }

              def onError(ex: Throwable) = {
                // error happened, so signaling both the main thread that it should stop
                // and the downstream consumer of the error
                observerU.onError(ex)
                upstreamPromise.trySuccess(Cancel)
              }

              def onComplete() = {
                // NOTE: we aren't sending this onComplete signal downstream to our observerU
                // instead we are just instructing upstream to send the next observable
                if (upstreamPromise.trySuccess(Continue)) {
                  refID.cancel()
                }
              }
            })

            upstreamPromise.future
          }

          def onError(ex: Throwable) = {
            // oops, error happened on main thread, piping that along should cancel everything
            observerU.onError(ex)
          }

          def onComplete() = {
            refCount.cancel()
          }
        })
      }
    }
}
