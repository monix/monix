package monifu.reactive

import language.implicitConversions
import monifu.concurrent.Scheduler
import scala.concurrent.Future
import monifu.reactive.api._
import Ack.{Cancel, Continue}
import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.util.control.NonFatal
import scala.annotation.tailrec
import scala.util.{Failure, Success}
import monifu.reactive.observers.SafeObserver
import monifu.reactive.internals.FutureAckExtensions
import monifu.reactive.observables.{GenericObservable, ObservableOperators}


/**
 * Asynchronous implementation of the Observable interface
 */
trait Observable[+T] extends ObservableOperators[T] {
  /**
   * Characteristic function for an `Observable` instance,
   * that creates the subscription and that starts the stream,
   * being meant to be overridden in custom combinators
   * or in classes implementing Observable.
   *
   * @param observer is an [[Observer]] on which `onNext`, `onComplete` and `onError`
   *                 happens, according to the Monifu Rx contract.
   */
  protected def subscribeFn(observer: Observer[T]): Unit
}

object Observable {
  /**
   * Observable constructor for creating an [[Observable]] from the specified function.
   *
   * <img src="https://raw.githubusercontent.com/wiki/alexandru/monifu/assets/rx-operators/create.png" />
   *
   * Example: {{{
   *   import monifu.reactive._
   *   import monifu.reactive.api.Ack.Continue
   *   import monifu.concurrent.Scheduler
   *
   *   def emit[T](elem: T, nrOfTimes: Int)(implicit scheduler: Scheduler): Observable[T] =
   *     Observable.create { observer =>
   *       def loop(times: Int): Unit =
   *         scheduler.scheduleOnce {
   *           if (times > 0)
   *             observer.onNext(elem).onSuccess {
   *               case Continue => loop(times - 1)
   *             }
   *           else
   *             observer.onComplete()
   *         }
   *       loop(nrOfTimes)
   *     }
   *
   *   // usage sample
   *   import monifu.concurrent.Scheduler.Implicits.global

   *   emit(elem=30, nrOfTimes=3).dump("Emit").subscribe()
   *   //=> 0: Emit-->30
   *   //=> 1: Emit-->30
   *   //=> 2: Emit-->30
   *   //=> 3: Emit completed
   * }}}
   */
  def create[T](f: Observer[T] => Unit)(implicit scheduler: Scheduler): Observable[T] =
    GenericObservable.create(f)

  /**
   * Creates an observable that doesn't emit anything, but immediately calls `onComplete`
   * instead.
   *
   * <img src="https://raw.githubusercontent.com/wiki/alexandru/monifu/assets/rx-operators/empty.png" />
   */
  def empty[A](implicit scheduler: Scheduler): Observable[A] =
    Observable.create { observer =>
      SafeObserver(observer).onComplete()
    }

  /**
   * Creates an Observable that only emits the given ''a''
   *
   * <img src="https://raw.githubusercontent.com/wiki/alexandru/monifu/assets/rx-operators/unit.png" />
   */
  def unit[A](elem: A)(implicit scheduler: Scheduler): Observable[A] = {
    Observable.create { o =>
      val observer = SafeObserver(o)
      observer.onNext(elem).onSuccess {
        case Continue =>
          observer.onComplete()
      }
    }
  }

  /**
   * Creates an Observable that emits an error.
   *
   * <img src="https://raw.githubusercontent.com/wiki/alexandru/monifu/assets/rx-operators/error.png" />
   */
  def error(ex: Throwable)(implicit scheduler: Scheduler): Observable[Nothing] =
    Observable.create { observer =>
      SafeObserver[Nothing](observer).onError(ex)
    }

  /**
   * Creates an Observable that doesn't emit anything and that never completes.
   *
   * <img src="https://raw.githubusercontent.com/wiki/alexandru/monifu/assets/rx-operators/never.png"" />
   */
  def never(implicit scheduler: Scheduler): Observable[Nothing] =
    Observable.create { _ => () }

  /**
   * Creates an Observable that emits auto-incremented natural numbers (longs) spaced by
   * a given time interval. Starts from 0 with no delay, after which it emits incremented
   * numbers spaced by the `period` of time.
   *
   * <img src="https://raw.githubusercontent.com/wiki/alexandru/monifu/assets/rx-operators/interval.png"" />
   *
   * @param period the delay between two subsequent events
   * @param scheduler the execution context in which `onNext` will get called
   */
  def interval(period: FiniteDuration)(implicit scheduler: Scheduler): Observable[Long] = {
    Observable.create { o =>
      val observer = SafeObserver(o)
      var counter = 0

      scheduler.scheduleRecursive(Duration.Zero, period, { reschedule =>
        val result = observer.onNext(counter)
        counter += 1

        result.onSuccess {
          case Continue =>
            reschedule()
        }
      })
    }
  }

  /**
   * Creates an Observable that continuously emits the given ''item'' repeatedly.
   */
  def repeat[T](elems: T*)(implicit scheduler: Scheduler): Observable[T] = {
    if (elems.size == 0) Observable.empty
    else if (elems.size == 1) {
      Observable.create { o =>
        val observer = SafeObserver(o)

        def loop(elem: T): Unit =
          scheduler.execute(new Runnable {
            def run(): Unit =
              observer.onNext(elem).onSuccess {
                case Continue =>
                  loop(elem)
              }
          })

        loop(elems.head)
      }
    }
    else
      Observable.from(elems).repeat
  }

  /**
   * Creates an Observable that emits items in the given range.
   *
   * <img src="https://raw.githubusercontent.com/wiki/alexandru/monifu/assets/rx-operators/range.png" />
   *
   * @param from the range start
   * @param until the range end
   * @param step increment step, either positive or negative
   */
  def range(from: Int, until: Int, step: Int = 1)(implicit scheduler: Scheduler): Observable[Int] = {
    require(step != 0, "step must be a number different from zero")

    Observable.create { o =>
      val observer = SafeObserver(o)

      def scheduleLoop(from: Int, until: Int, step: Int): Unit =
        scheduler.execute(new Runnable {
          @tailrec
          def loop(from: Int, until: Int, step: Int): Unit =
            if ((step > 0 && from < until) || (step < 0 && from > until)) {
              observer.onNext(from) match {
                case Continue =>
                  loop(from + step, until, step)
                case Cancel =>
                  // do nothing else
                case async =>
                  async.onSuccess {
                    case Continue =>
                      scheduleLoop(from + step, until, step)
                  }
              }
            }
            else
              observer.onComplete()

          def run(): Unit =
            loop(from, until, step)
        })

      scheduleLoop(from, until, step)
    }
  }

  /**
   * Creates an Observable that emits the given elements.
   *
   * Usage sample: {{{
   *   val obs = Observable(1, 2, 3, 4)
   *
   *   obs.dump("MyObservable").subscribe()
   *   //=> 0: MyObservable-->1
   *   //=> 1: MyObservable-->2
   *   //=> 2: MyObservable-->3
   *   //=> 3: MyObservable-->4
   *   //=> 4: MyObservable completed
   * }}}
   */
  def apply[T](elems: T*)(implicit scheduler: Scheduler): Observable[T] = {
    from(elems)
  }

  /**
   * Converts a Future to an Observable.
   *
   * <img src="https://raw.githubusercontent.com/wiki/alexandru/monifu/assets/rx-operators/fromIterable.png" />
   */
  def from[T](future: Future[T])(implicit scheduler: Scheduler): Observable[T] =
    Observable.create { o =>
      val observer = SafeObserver(o)

      future.onComplete {
        case Success(value) =>
          observer.onNext(value)
            .onContinueTriggerComplete(observer)
        case Failure(ex) =>
          observer.onError(ex)
      }
    }

  /**
   * Creates an Observable that emits the elements of the given ''iterable''.
   *
   * <img src="https://raw.githubusercontent.com/wiki/alexandru/monifu/assets/rx-operators/fromIterable.png" />
   */
  def from[T](iterable: Iterable[T])(implicit scheduler: Scheduler): Observable[T] =
    Observable.create { o =>
      val observer = SafeObserver(o)

      def startFeedLoop(iterator: Iterator[T]): Unit =
        scheduler.execute(new Runnable {
          def run(): Unit =
            while (true) {
              try {
                if (iterator.hasNext) {
                  val elem = iterator.next()

                  observer.onNext(elem) match {
                    case Continue =>
                    // continue loop
                    case Cancel =>
                      return
                    case async =>
                      async.onSuccess {
                        case Continue =>
                          startFeedLoop(iterator)
                      }
                      return // interrupt the loop
                  }
                }
                else {
                  observer.onComplete()
                  return
                }
              }
              catch {
                case NonFatal(ex) =>
                  observer.onError(ex)
              }
            }
        })

      val iterator = iterable.iterator
      startFeedLoop(iterator)
    }

  /**
   * Concatenates the given list of ''observables'' into a single observable.
   */
  def flatten[T](sources: Observable[T]*)(implicit scheduler: Scheduler): Observable[T] =
    Observable.from(sources).flatten

  /**
   * Merges the given list of ''observables'' into a single observable.
   */
  def merge[T](sources: Observable[T]*)(implicit scheduler: Scheduler): Observable[T] =
    Observable.from(sources).merge

  /**
   * Creates a new Observable from two observables,
   * by emitting elements combined in pairs. If one of the Observable emits fewer
   * events than the other, then the rest of the unpaired events are ignored.
   */
  def zip[T1, T2](obs1: Observable[T1], obs2: Observable[T2]): Observable[(T1,T2)] =
    obs1.zip(obs2)

  /**
   * Creates a new Observable from three observables,
   * by emitting elements combined in tuples of 3 elements. If one of the Observable emits fewer
   * events than the others, then the rest of the unpaired events are ignored.
   */
  def zip[T1, T2, T3](obs1: Observable[T1], obs2: Observable[T2], obs3: Observable[T3]): Observable[(T1, T2, T3)] =
    obs1.zip(obs2).zip(obs3).map { case ((t1, t2), t3) => (t1, t2, t3) }

  /**
   * Creates a new Observable from three observables,
   * by emitting elements combined in tuples of 4 elements. If one of the Observable emits fewer
   * events than the others, then the rest of the unpaired events are ignored.
   */
  def zip[T1, T2, T3, T4](obs1: Observable[T1], obs2: Observable[T2], obs3: Observable[T3], obs4: Observable[T4]): Observable[(T1, T2, T3, T4)] =
    obs1.zip(obs2).zip(obs3).zip(obs4).map { case (((t1, t2), t3), t4) => (t1, t2, t3, t4) }

  /**
   * Concatenates the given list of ''observables'' into a single observable.
   */
  def concat[T](sources: Observable[T]*)(implicit scheduler: Scheduler): Observable[T] =
    Observable.from(sources).concat

  /**
   * Implicit conversion from Future to Observable.
   */
  implicit def FutureIsObservable[T](future: Future[T])(implicit scheduler: Scheduler): Observable[T] =
    Observable.from(future)
}
