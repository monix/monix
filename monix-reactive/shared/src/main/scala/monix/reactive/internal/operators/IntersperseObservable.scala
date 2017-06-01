package monix.reactive.internal.operators

import monix.execution.Ack.Continue
import monix.execution.atomic.AtomicBoolean
import monix.execution.{Ack, Cancelable}
import monix.reactive.Observable
import monix.reactive.observers.Subscriber

import scala.concurrent.Future

/**
  * Created by omainegra on 5/29/17.
  */
private[reactive] final class IntersperseObservable[+A](source: Observable[A],
                                                        separator: A) extends Observable[A]{ self =>

  override def unsafeSubscribeFn(out: Subscriber[A]): Cancelable = {
    val firstTime = AtomicBoolean(true)
    val isDone = AtomicBoolean(false)
    var downstreamAck = Continue : Future[Ack]

    val upstream = source.unsafeSubscribeFn(new Subscriber[A] {
      implicit val scheduler = out.scheduler

      override def onNext(elem: A): Future[Ack] = {
        downstreamAck = if (firstTime.getAndSet(false)) {
          out.onNext(elem)
        }
        else {
          out.onNext(separator).flatMap {
            case Continue => out.onNext(elem)
            case ack => ack
          }
        }
        downstreamAck
      }

      def onError(ex: Throwable) = {
        if (!isDone.getAndSet(true)){
          out.onError(ex)
        }
      }
      def onComplete() = {
        downstreamAck.syncOnContinue {
          if (!isDone.getAndSet(true)){
            out.onComplete()
          }
        }
      }
    })

    Cancelable { () =>
      upstream.cancel()
    }
  }
}
