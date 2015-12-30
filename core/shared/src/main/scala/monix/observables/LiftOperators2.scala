/*
 * Copyright (c) 2014-2015 by its authors. Some rights reserved.
 * See the project homepage at: http://www.monix.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package monix.observables

import language.higherKinds
import java.io.PrintStream
import monix.concurrent.Scheduler
import monix.concurrent.cancelables.BooleanCancelable
import monix.OverflowStrategy.{Synchronous, Evicted}
import monix.{Observable, OverflowStrategy}
import scala.concurrent.duration.FiniteDuration

/** An interface to be extended in Observable types that want to preserve
  * the return type when applying operators. For example the result of
  * [[monix.Subject.map Subject.map]]
  * is still a `Subject` and this interface represents
  * an utility to do just that.
  */
trait LiftOperators2[I, +T, Self[A,+B] <: Observable[B]] { self: Observable[T] =>
  protected def liftToSelf[U](f: Observable[T] => Observable[U]): Self[I,U]
  
  override def map[U](f: T => U): Self[I,U] =
    liftToSelf(o => Observable.unsafeCreate[T](o.unsafeSubscribeFn).map(f))

  override def filter(p: (T) => Boolean): Self[I,T] =
    liftToSelf(o => Observable.unsafeCreate[T](o.unsafeSubscribeFn).filter(p))

  override def collect[U](pf: PartialFunction[T, U]): Self[I,U] =
    liftToSelf(o => Observable.unsafeCreate[T](o.unsafeSubscribeFn).collect(pf))

  override def flatMap[U](f: (T) => Observable[U]): Self[I,U] =
    liftToSelf(o => Observable.unsafeCreate[T](o.unsafeSubscribeFn).flatMap(f))

  override def flatMapDelayError[U](f: (T) => Observable[U]): Self[I,U] =
    liftToSelf(o => Observable.unsafeCreate[T](o.unsafeSubscribeFn).flatMapDelayError(f))

  override def concatMap[U](f: (T) => Observable[U]): Self[I,U] =
    liftToSelf(o => Observable.unsafeCreate[T](o.unsafeSubscribeFn).concatMap(f))

  override def concatMapDelayError[U](f: (T) => Observable[U]): Self[I,U] =
    liftToSelf(o => Observable.unsafeCreate[T](o.unsafeSubscribeFn).concatMapDelayError(f))

  override def mergeMap[U](f: (T) => Observable[U]): Self[I,U] =
    liftToSelf(o => Observable.unsafeCreate[T](o.unsafeSubscribeFn).mergeMap(f))

  override def mergeMapDelayErrors[U](f: (T) => Observable[U]): Self[I,U] =
    liftToSelf(o => Observable.unsafeCreate[T](o.unsafeSubscribeFn).mergeMapDelayErrors(f))

  override def flatten[U](implicit ev: <:<[T, Observable[U]]): Self[I,U] =
    liftToSelf(o => Observable.unsafeCreate[T](o.unsafeSubscribeFn).flatten)

  override def flattenDelayError[U](implicit ev: <:<[T, Observable[U]]): Self[I,U] =
    liftToSelf(o => Observable.unsafeCreate[T](o.unsafeSubscribeFn).flattenDelayError)

  override def concat[U](implicit ev: <:<[T, Observable[U]]): Self[I,U] =
    liftToSelf(o => Observable.unsafeCreate[T](o.unsafeSubscribeFn).concat)

  override def concatDelayError[U](implicit ev: <:<[T, Observable[U]]): Self[I,U] =
    liftToSelf(o => Observable.unsafeCreate[T](o.unsafeSubscribeFn).concatDelayError)

  override def merge[U](implicit ev: <:<[T, Observable[U]]): Self[I,U] =
    liftToSelf(o => Observable.unsafeCreate[T](o.unsafeSubscribeFn).merge)

  override def merge[U](overflowStrategy: OverflowStrategy)(implicit ev: <:<[T, Observable[U]]): Self[I,U] =
    liftToSelf(o => Observable.unsafeCreate[T](o.unsafeSubscribeFn).merge(overflowStrategy))

  override def merge[U](overflowStrategy: Evicted, onOverflow: (Long) => U)(implicit ev: <:<[T, Observable[U]]): Self[I,U] =
    liftToSelf(o => Observable.unsafeCreate[T](o.unsafeSubscribeFn).merge(overflowStrategy, onOverflow))

  override def mergeDelayErrors[U](implicit ev: <:<[T, Observable[U]]): Self[I,U] =
    liftToSelf(o => Observable.unsafeCreate[T](o.unsafeSubscribeFn).mergeDelayErrors)

  override def mergeDelayErrors[U](overflowStrategy: OverflowStrategy)(implicit ev: <:<[T, Observable[U]]): Self[I,U] =
    liftToSelf(o => Observable.unsafeCreate[T](o.unsafeSubscribeFn).mergeDelayErrors(overflowStrategy))

  override def mergeDelayErrors[U](overflowStrategy: Evicted, onOverflow: (Long) => U)(implicit ev: <:<[T, Observable[U]]): Self[I,U] =
    liftToSelf(o => Observable.unsafeCreate[T](o.unsafeSubscribeFn).mergeDelayErrors(overflowStrategy, onOverflow))

  override def switch[U](implicit ev: <:<[T, Observable[U]]): Self[I,U] =
    liftToSelf(o => Observable.unsafeCreate[T](o.unsafeSubscribeFn).switch)

  override def flattenLatest[U](implicit ev: <:<[T, Observable[U]]): Self[I,U] =
    liftToSelf(o => Observable.unsafeCreate[T](o.unsafeSubscribeFn).flattenLatest)

  override def flatMapLatest[U](f: (T) => Observable[U]): Self[I,U] =
    liftToSelf(o => Observable.unsafeCreate[T](o.unsafeSubscribeFn).flatMapLatest(f))

  override def switchMap[U](f: (T) => Observable[U]): Self[I,U] =
    liftToSelf(o => Observable.unsafeCreate[T](o.unsafeSubscribeFn).switchMap(f))

  override def ambWith[U >: T](other: Observable[U]): Self[I,U] =
    liftToSelf(o => Observable.unsafeCreate[T](o.unsafeSubscribeFn).ambWith(other))

  override def defaultIfEmpty[U >: T](default: U): Self[I,U] =
    liftToSelf(o => Observable.unsafeCreate[T](o.unsafeSubscribeFn).defaultIfEmpty(default))

  override def take(n: Long): Self[I,T] =
    liftToSelf(o => Observable.unsafeCreate[T](o.unsafeSubscribeFn).take(n))

  override def take(timespan: FiniteDuration): Self[I,T] =
    liftToSelf(o => Observable.unsafeCreate[T](o.unsafeSubscribeFn).take(timespan))

  override def takeRight(n: Int): Self[I,T] =
    liftToSelf(o => Observable.unsafeCreate[T](o.unsafeSubscribeFn).takeRight(n))

  override def drop(n: Int): Self[I,T] =
    liftToSelf(o => Observable.unsafeCreate[T](o.unsafeSubscribeFn).drop(n))

  override def dropByTimespan(timespan: FiniteDuration): Self[I,T] =
    liftToSelf(o => Observable.unsafeCreate[T](o.unsafeSubscribeFn).dropByTimespan(timespan))

  override def dropWhile(p: (T) => Boolean): Self[I,T] =
    liftToSelf(o => Observable.unsafeCreate[T](o.unsafeSubscribeFn).dropWhile(p))

  override def dropWhileWithIndex(p: (T, Int) => Boolean): Self[I,T] =
    liftToSelf(o => Observable.unsafeCreate[T](o.unsafeSubscribeFn).dropWhileWithIndex(p))

  override def takeWhile(p: (T) => Boolean): Self[I,T] =
    liftToSelf(o => Observable.unsafeCreate[T](o.unsafeSubscribeFn).takeWhile(p))

  override def takeWhileNotCanceled(c: BooleanCancelable): Self[I,T] =
    liftToSelf(o => Observable.unsafeCreate[T](o.unsafeSubscribeFn).takeWhileNotCanceled(c))

  override def count: Self[I,Long] =
    liftToSelf(o => Observable.unsafeCreate[T](o.unsafeSubscribeFn).count)

  override def buffer(count: Int): Self[I,Seq[T]] =
    liftToSelf(o => Observable.unsafeCreate[T](o.unsafeSubscribeFn).buffer(count))

  override def buffer(count: Int, skip: Int): Self[I,Seq[T]] =
    liftToSelf(o => Observable.unsafeCreate[T](o.unsafeSubscribeFn).buffer(count, skip))

  override def buffer(timespan: FiniteDuration): Self[I,Seq[T]] =
    liftToSelf(o => Observable.unsafeCreate[T](o.unsafeSubscribeFn).buffer(timespan))

  override def buffer(timespan: FiniteDuration, maxSize: Int): Self[I,Seq[T]] =
    liftToSelf(o => Observable.unsafeCreate[T](o.unsafeSubscribeFn).buffer(timespan, maxSize))

  override def window(count: Int): Self[I,Observable[T]] =
    liftToSelf(o => Observable.unsafeCreate[T](o.unsafeSubscribeFn).window(count))

  override def window(count: Int, skip: Int): Self[I,Observable[T]] =
    liftToSelf(o => Observable.unsafeCreate[T](o.unsafeSubscribeFn).window(count, skip))

  override def window(timespan: FiniteDuration): Self[I,Observable[T]] =
    liftToSelf(o => Observable.unsafeCreate[T](o.unsafeSubscribeFn).window(timespan))

  override def window(timespan: FiniteDuration, maxCount: Int): Self[I,Observable[T]] =
    liftToSelf(o => Observable.unsafeCreate[T](o.unsafeSubscribeFn).window(timespan, maxCount))

  override def throttleLast(period: FiniteDuration): Self[I,T] =
    liftToSelf(o => Observable.unsafeCreate[T](o.unsafeSubscribeFn).throttleLast(period))

  override def throttleFirst(interval: FiniteDuration): Self[I,T] =
    liftToSelf(o => Observable.unsafeCreate[T](o.unsafeSubscribeFn).throttleFirst(interval))

  override def throttleWithTimeout(timeout: FiniteDuration): Self[I,T] =
    liftToSelf(o => Observable.unsafeCreate[T](o.unsafeSubscribeFn).throttleWithTimeout(timeout))

  override def sample(delay: FiniteDuration): Self[I,T] =
    liftToSelf(o => Observable.unsafeCreate[T](o.unsafeSubscribeFn).sample(delay))

  override def sample(initialDelay: FiniteDuration, delay: FiniteDuration): Self[I,T] =
    liftToSelf(o => Observable.unsafeCreate[T](o.unsafeSubscribeFn).sample(initialDelay, delay))

  override def sample[U](sampler: Observable[U]): Self[I,T] =
    liftToSelf(o => Observable.unsafeCreate[T](o.unsafeSubscribeFn).sample(sampler))

  override def sampleRepeated(delay: FiniteDuration): Self[I,T] =
    liftToSelf(o => Observable.unsafeCreate[T](o.unsafeSubscribeFn).sampleRepeated(delay))

  override def sampleRepeated(initialDelay: FiniteDuration, delay: FiniteDuration): Self[I,T] =
    liftToSelf(o => Observable.unsafeCreate[T](o.unsafeSubscribeFn).sampleRepeated(initialDelay, delay))

  override def sampleRepeated[U](sampler: Observable[U]): Self[I,T] =
    liftToSelf(o => Observable.unsafeCreate[T](o.unsafeSubscribeFn).sampleRepeated(sampler))

  override def debounce(timeout: FiniteDuration): Self[I,T] =
    liftToSelf(o => Observable.unsafeCreate[T](o.unsafeSubscribeFn).debounce(timeout))

  override def debounceRepeated(period: FiniteDuration): Self[I,T] =
    liftToSelf(o => Observable.unsafeCreate[T](o.unsafeSubscribeFn).debounceRepeated(period))

  override def debounce[U](timeout: FiniteDuration, f: (T) => Observable[U]): Self[I,U] =
    liftToSelf(o => Observable.unsafeCreate[T](o.unsafeSubscribeFn).debounce(timeout, f))

  override def debounce(selector: (T) => Observable[Any]): Self[I,T] =
    liftToSelf(o => Observable.unsafeCreate[T](o.unsafeSubscribeFn).debounce(selector))

  override def debounce[U](selector: (T) => Observable[Any], f: (T) => Observable[U]): Self[I,U] =
    liftToSelf(o => Observable.unsafeCreate[T](o.unsafeSubscribeFn).debounce(selector, f))

  override def echoOnce(timeout: FiniteDuration): Self[I,T] =
    liftToSelf(o => Observable.unsafeCreate[T](o.unsafeSubscribeFn).echoOnce(timeout))

  override def echoRepeated(timeout: FiniteDuration): Self[I,T] =
    liftToSelf(o => Observable.unsafeCreate[T](o.unsafeSubscribeFn).echoRepeated(timeout))

  override def delaySubscription[U](trigger: Observable[U]): Self[I,T] =
    liftToSelf(o => Observable.unsafeCreate[T](o.unsafeSubscribeFn).delaySubscription(trigger))

  override def delaySubscription(timespan: FiniteDuration): Self[I,T] =
    liftToSelf(o => Observable.unsafeCreate[T](o.unsafeSubscribeFn).delaySubscription(timespan))

  override def delay(duration: FiniteDuration): Self[I, T] =
    liftToSelf(o => Observable.unsafeCreate[T](o.unsafeSubscribeFn).delay(duration))

  override def delay[U](selector: (T) => Observable[U]): Self[I, T] =
    liftToSelf(o => Observable.unsafeCreate[T](o.unsafeSubscribeFn).delay(selector))

  override def foldLeft[R](initial: R)(op: (R, T) => R): Self[I,R] =
    liftToSelf(o => Observable.unsafeCreate[T](o.unsafeSubscribeFn).foldLeft(initial)(op))

  override def reduce[U >: T](op: (U, U) => U): Self[I,U] =
    liftToSelf(o => Observable.unsafeCreate[T](o.unsafeSubscribeFn).reduce(op))

  override def scan[R](initial: R)(op: (R, T) => R): Self[I,R] =
    liftToSelf(o => Observable.unsafeCreate[T](o.unsafeSubscribeFn).scan(initial)(op))

  override def flatScan[R](initial: R)(op: (R, T) => Observable[R]): Self[I,R] =
    liftToSelf(o => Observable.unsafeCreate[T](o.unsafeSubscribeFn).flatScan(initial)(op))

  override def flatScanDelayError[R](initial: R)(op: (R, T) => Observable[R]): Self[I,R] =
    liftToSelf(o => Observable.unsafeCreate[T](o.unsafeSubscribeFn).flatScanDelayError(initial)(op))

  override def doOnComplete(cb: => Unit): Self[I,T] =
    liftToSelf(o => Observable.unsafeCreate[T](o.unsafeSubscribeFn).doOnComplete(cb))

  override def doWork(cb: (T) => Unit): Self[I,T] =
    liftToSelf(o => Observable.unsafeCreate[T](o.unsafeSubscribeFn).doWork(cb))

  override def doOnStart(cb: (T) => Unit): Self[I,T] =
    liftToSelf(o => Observable.unsafeCreate[T](o.unsafeSubscribeFn).doOnStart(cb))

  override def doOnCanceled(cb: => Unit): Self[I,T] =
    liftToSelf(o => Observable.unsafeCreate[T](o.unsafeSubscribeFn).doOnCanceled(cb))

  override def doOnError(cb: (Throwable) => Unit): Self[I,T] =
    liftToSelf(o => Observable.unsafeCreate[T](o.unsafeSubscribeFn).doOnError(cb))

  override def find(p: (T) => Boolean): Self[I,T] =
    liftToSelf(o => Observable.unsafeCreate[T](o.unsafeSubscribeFn).find(p))

  override def exists(p: (T) => Boolean): Self[I,Boolean] =
    liftToSelf(o => Observable.unsafeCreate[T](o.unsafeSubscribeFn).exists(p))

  override def isEmpty: Self[I,Boolean] =
    liftToSelf(o => Observable.unsafeCreate[T](o.unsafeSubscribeFn).isEmpty)

  override def nonEmpty: Self[I,Boolean] =
    liftToSelf(o => Observable.unsafeCreate[T](o.unsafeSubscribeFn).nonEmpty)

  override def forAll(p: (T) => Boolean): Self[I,Boolean] =
    liftToSelf(o => Observable.unsafeCreate[T](o.unsafeSubscribeFn).forAll(p))

  override def complete: Self[I,Nothing] =
    liftToSelf(o => Observable.unsafeCreate[T](o.unsafeSubscribeFn).ignoreElements)

  override def error: Self[I,Throwable] =
    liftToSelf(o => Observable.unsafeCreate[T](o.unsafeSubscribeFn).error)

  override def endWithError(error: Throwable): Self[I,T] =
    liftToSelf(o => Observable.unsafeCreate[T](o.unsafeSubscribeFn).endWithError(error))

  override def +:[U >: T](elem: U): Self[I,U] =
    liftToSelf(o => Observable.unsafeCreate[T](o.unsafeSubscribeFn).+:(elem))

  override def startWith[U >: T](elems: U*): Self[I,U] =
    liftToSelf(o => Observable.unsafeCreate[T](o.unsafeSubscribeFn).startWith(elems:_*))

  override def :+[U >: T](elem: U): Self[I,U] =
    liftToSelf(o => Observable.unsafeCreate[T](o.unsafeSubscribeFn).:+(elem))

  override def endWith[U >: T](elems: U*): Self[I,U] =
    liftToSelf(o => Observable.unsafeCreate[T](o.unsafeSubscribeFn).endWith(elems:_*))

  override def ++[U >: T](other: => Observable[U]): Self[I,U] =
    liftToSelf(o => Observable.unsafeCreate[T](o.unsafeSubscribeFn).++(other))

  override def head: Self[I,T] =
    liftToSelf(o => Observable.unsafeCreate[T](o.unsafeSubscribeFn).head)

  override def tail: Self[I,T] =
    liftToSelf(o => Observable.unsafeCreate[T](o.unsafeSubscribeFn).tail)

  override def last: Self[I,T] =
    liftToSelf(o => Observable.unsafeCreate[T](o.unsafeSubscribeFn).last)

  override def headOrElse[B >: T](default: => B): Self[I,B] =
    liftToSelf(o => Observable.unsafeCreate[T](o.unsafeSubscribeFn).headOrElse(default))

  override def firstOrElse[U >: T](default: => U): Self[I,U] =
    liftToSelf(o => Observable.unsafeCreate[T](o.unsafeSubscribeFn).firstOrElse(default))

  override def zip[U](other: Observable[U]): Self[I,(T, U)] =
    liftToSelf(o => Observable.unsafeCreate[T](o.unsafeSubscribeFn).zip(other))

  override def combineLatest[U](other: Observable[U]): Self[I,(T, U)] =
    liftToSelf(o => Observable.unsafeCreate[T](o.unsafeSubscribeFn).combineLatest(other))

  override def combineLatestDelayError[U](other: Observable[U]): Self[I,(T, U)] =
    liftToSelf(o => Observable.unsafeCreate[T](o.unsafeSubscribeFn).combineLatestDelayError(other))

  override def max[U >: T](implicit ev: Ordering[U]): Self[I,U] =
    liftToSelf(o => Observable.unsafeCreate[T](o.unsafeSubscribeFn).max(ev))

  override def maxBy[U](f: (T) => U)(implicit ev: Ordering[U]): Self[I,T] =
    liftToSelf(o => Observable.unsafeCreate[T](o.unsafeSubscribeFn).maxBy(f))

  override def min[U >: T](implicit ev: Ordering[U]): Self[I,U] =
    liftToSelf(o => Observable.unsafeCreate[T](o.unsafeSubscribeFn).min(ev))

  override def minBy[U](f: (T) => U)(implicit ev: Ordering[U]): Self[I,T] =
    liftToSelf(o => Observable.unsafeCreate[T](o.unsafeSubscribeFn).minBy(f))

  override def sum[U >: T](implicit ev: Numeric[U]): Self[I,U] =
    liftToSelf(o => Observable.unsafeCreate[T](o.unsafeSubscribeFn).sum(ev))

  override def distinct: Self[I,T] =
    liftToSelf(o => Observable.unsafeCreate[T](o.unsafeSubscribeFn).distinct)

  override def distinct[U](fn: (T) => U): Self[I,T] =
    liftToSelf(o => Observable.unsafeCreate[T](o.unsafeSubscribeFn).distinct(fn))

  override def distinctUntilChanged: Self[I,T] =
    liftToSelf(o => Observable.unsafeCreate[T](o.unsafeSubscribeFn).distinctUntilChanged)

  override def distinctUntilChanged[U](fn: (T) => U): Self[I,T] =
    liftToSelf(o => Observable.unsafeCreate[T](o.unsafeSubscribeFn).distinctUntilChanged(fn))

  override def subscribeOn(s: Scheduler): Self[I,T] =
    liftToSelf(o => Observable.unsafeCreate[T](o.unsafeSubscribeFn).subscribeOn(s))

  override def dump(prefix: String, out: PrintStream = System.out): Self[I,T] =
    liftToSelf(o => Observable.unsafeCreate[T](o.unsafeSubscribeFn).dump(prefix, out))

  override def repeat: Self[I,T] =
    liftToSelf(o => Observable.unsafeCreate[T](o.unsafeSubscribeFn).repeat)

  override def asyncBoundary(overflowStrategy: OverflowStrategy): Self[I,T] =
    liftToSelf(o => Observable.unsafeCreate[T](o.unsafeSubscribeFn).asyncBoundary(overflowStrategy))

  override def asyncBoundary[U >: T](overflowStrategy: Evicted, onOverflow: (Long) => U): Self[I,U] =
    liftToSelf(o => Observable.unsafeCreate[T](o.unsafeSubscribeFn).asyncBoundary(overflowStrategy, onOverflow))

  override def whileBusyDropEvents: Self[I,T] =
    liftToSelf(o => Observable.unsafeCreate[T](o.unsafeSubscribeFn).whileBusyDropEvents)

  override def whileBusyDropEvents[U >: T](onOverflow: (Long) => U): Self[I,U] =
    liftToSelf(o => Observable.unsafeCreate[T](o.unsafeSubscribeFn).whileBusyDropEvents(onOverflow))

  override def whileBusyBuffer[U >: T](overflowStrategy: Synchronous): Self[I,U] =
    liftToSelf(o => Observable.unsafeCreate[T](o.unsafeSubscribeFn).whileBusyBuffer(overflowStrategy))

  override def whileBusyBuffer[U >: T](overflowStrategy: Evicted, onOverflow: (Long) => U): Self[I,U] =
    liftToSelf(o => Observable.unsafeCreate[T](o.unsafeSubscribeFn).whileBusyBuffer(overflowStrategy, onOverflow))

  override def onErrorRecoverWith[U >: T](pf: PartialFunction[Throwable, Observable[U]]): Self[I,U] =
    liftToSelf(o => Observable.unsafeCreate[T](o.unsafeSubscribeFn).onErrorRecoverWith(pf))

  override def onErrorFallbackTo[U >: T](that: => Observable[U]): Self[I,U] =
    liftToSelf(o => Observable.unsafeCreate[T](o.unsafeSubscribeFn).onErrorFallbackTo(that))

  override def onErrorRetryUnlimited: Self[I,T] =
    liftToSelf(o => Observable.unsafeCreate[T](o.unsafeSubscribeFn).onErrorRetryUnlimited)

  override def onErrorRetry(maxRetries: Long): Self[I,T] =
    liftToSelf(o => Observable.unsafeCreate[T](o.unsafeSubscribeFn).onErrorRetry(maxRetries))

  override def onErrorRetryIf(p: (Throwable) => Boolean): Self[I,T] =
    liftToSelf(o => Observable.unsafeCreate[T](o.unsafeSubscribeFn).onErrorRetryIf(p))

  override def timeout(timeout: FiniteDuration): Self[I,T] =
    liftToSelf(o => Observable.unsafeCreate[T](o.unsafeSubscribeFn).timeout(timeout))

  override def timeout[U >: T](timeout: FiniteDuration, backup: Observable[U]): Self[I,U] =
    liftToSelf(o => Observable.unsafeCreate[T](o.unsafeSubscribeFn).timeout(timeout, backup))

  override def lift[U](f: (Observable[T]) => Observable[U]): Self[I,U] =
    liftToSelf(o => Observable.unsafeCreate[T](o.unsafeSubscribeFn).lift(f))

  override def groupBy[K](keySelector: (T) => K): Self[I,GroupedObservable[K, T]] =
    liftToSelf(o => Observable.unsafeCreate[T](o.unsafeSubscribeFn).groupBy(keySelector))

  override def groupBy[K](keyBufferSize: Int, keySelector: (T) => K): Self[I,GroupedObservable[K, T]] =
    liftToSelf(o => Observable.unsafeCreate[T](o.unsafeSubscribeFn).groupBy(keyBufferSize, keySelector))

  override def ignoreElements: Self[I, Nothing] =
    liftToSelf(o => Observable.unsafeCreate[T](o.unsafeSubscribeFn).ignoreElements)

  override def zipWithIndex: Self[I, (T, Long)] =
    liftToSelf(o => Observable.unsafeCreate[T](o.unsafeSubscribeFn).zipWithIndex)
}
