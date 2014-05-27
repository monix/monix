package monifu.reactive.api

import monifu.reactive.Observer
import monifu.reactive.api.Ack.{Done, Continue}
import scala.concurrent.{Promise, Future}
import monifu.concurrent.async.AsyncQueue
import monifu.concurrent.Scheduler


final class BufferedObserver[-T](observer: Observer[T])(implicit scheduler: Scheduler) extends Observer[T] {
  private[this] val elements = AsyncQueue[AnyRef]()

  def onNext(elem: T): Future[Ack] = {
    val anyRef = elem.asInstanceOf[AnyRef]
    if (anyRef != null) {
      elements.offer(anyRef)
      Continue
    }
    else {
      onError(new IllegalArgumentException("cannot buffer null elements"))
      Done
    }
  }

  def onComplete(): Unit = {
    elements.offer(null)
    terminationPromise.future.onSuccess {
      case Continue =>
        observer.onComplete()
    }
  }

  def onError(ex: Throwable): Unit = {
    elements.offer(null)
    terminationPromise.future.onSuccess {
      case Continue =>
        observer.onError(ex)
    }
  }

  private[this] val terminationPromise = Promise[Ack]()
  private[this] def loop(): Unit =
    elements.poll().onSuccess {
      case null =>
        terminationPromise.success(Continue)

      case elem =>
        val t = elem.asInstanceOf[T]

        observer.onNext(t).onSuccess {
          case Continue =>
            loop()
          case Done =>
            terminationPromise.success(Done)
        }
    }

  locally {
    loop()
  }
}
