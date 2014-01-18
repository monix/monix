package monifu

import scala.language.higherKinds
import scala.concurrent.Future
import monifu.concurrent.atomic.Atomic
import scala.collection.SortedSet
import scala.concurrent.duration._

trait Streams {
  type Stream[T]

  def map[A,B](stream: Stream[A])(f: A => B): Stream[B]

  def fold[A,B](stream: Stream[A])(initial: B)(onNext: (B,A) => B, onError: Throwable => B, onComplete: => B): Future[B]
}

object Streams {
  trait Observer[T] {
    def onNext(elem: T): Unit
    def onError(error: Throwable): Unit
    def onComplete(): Unit
  }

  trait Observable[A] {
    def map[B](f: A => B): Observable[B] = 
      Observable({ observer: Observer[B] =>
        subscribe(new Observer[A] {
          def onNext(elem: A) = observer.onNext(f(elem))
          def onError(ex: Throwable) = observer.onError(ex)
          def onComplete() = observer.onComplete()
        })
      })

    def flatMap[B](f: A => Observable[B]): Observable[B] = 
      // f(elem).subscribe()
      Observable({ observer: Observer[B] => {
        val mainSubscription = subscribe(new Observer[A] {
          // TODO: deal with unsubscriptions
          def onNext(elem: A) = 
            f(elem).subscribe(observer)
          def onError(ex: Throwable) = 
            observer.onError(ex)
          def onComplete() = 
            observer.onComplete()
        })

        mainSubscription
      }})

    def subscribe(observer: Observer[A]): Subscription
  }

  trait Subscription {
    def unsubscribe(): Unit
  }

  object Observer {
    def apply[T](f: T => Unit): Observer[T] = new Observer[T] {
      def onNext(elem: T): Unit = f(elem)
      def onError(ex: Throwable): Unit = throw ex
      def onComplete(): Unit = {}
    }
  }

  object Observable {
    def apply[A](f: Observer[A] => Subscription): Observable[A] = 
      new Observable[A] {
        def subscribe(observer: Observer[A]) = f(observer)
      }
  }

  def pushEvents[T](duration: FiniteDuration, events: Seq[T]): Observable[T] = {
    def createThread(observer: Observer[T], isCanceled: Atomic[Boolean]) = 
      new Thread(new Runnable() {
        override def run(): Unit = {
          try {
            for (e <- events; if !isCanceled.get) {
              observer.onNext(e)
              Thread.sleep(duration.toMillis)
            }

            if (!isCanceled.get)
              observer.onComplete()
          }
          catch {
            case _: InterruptedException =>
              // do nothing
            case ex: Exception =>
              observer.onError(ex)
          }
        }
      })

    Observable({ observer: Observer[T] =>
      val isCanceled = Atomic(false)
      val th = createThread(observer, isCanceled)
      th.start()

      new Subscription {
        def unsubscribe(): Unit = {
          th.interrupt
          th.join
        }
      }
    })
  }
}
