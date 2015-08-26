/*
 * Copyright (c) 2014-2015 by its authors. Some rights reserved.
 * See the project homepage at: http://www.monifu.org
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

package monifu.reactive.observables

import java.io.PrintStream
import monifu.concurrent.Scheduler
import monifu.concurrent.cancelables.BooleanCancelable
import monifu.reactive.OverflowStrategy.{Synchronous, WithSignal}
import monifu.reactive.{Notification, Observable, OverflowStrategy}
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.language.higherKinds

/**
 * An interface to be extended in Observable types that want to preserve
 * the return type when applying operators. For example the result of
 * [[ConnectableObservable.map]] is still a `ConnectableObservable`
 * and this interface represents an utility to do just that.
 */
trait LiftOperators1[+T, Self[+U] <: Observable[U]] { self: Observable[T] =>
  protected def liftToSelf[U](f: Observable[T] => Observable[U]): Self[U]

  override def map[U](f: T => U): Self[U] =
    liftToSelf(_.map(f))
  override def filter(p: (T) => Boolean): Self[T] =
    liftToSelf(_.filter(p))
  override def collect[U](pf: PartialFunction[T, U]): Self[U] =
    liftToSelf(_.collect(pf))
  override def flatMap[U](f: (T) => Observable[U]): Self[U] =
    liftToSelf(_.flatMap(f))
  override def flatMapDelayError[U](f: (T) => Observable[U]): Self[U] =
    liftToSelf(_.flatMapDelayError(f))
  override def concatMap[U](f: (T) => Observable[U]): Self[U] =
    liftToSelf(_.concatMap(f))
  override def concatMapDelayError[U](f: (T) => Observable[U]): Self[U] =
    liftToSelf(_.concatMapDelayError(f))
  override def mergeMap[U](f: (T) => Observable[U]): Self[U] =
    liftToSelf(_.mergeMap(f))
  override def mergeMapDelayErrors[U](f: (T) => Observable[U]): Self[U] =
    liftToSelf(_.mergeMapDelayErrors(f))
  override def flatten[U](implicit ev: <:<[T, Observable[U]]): Self[U] =
    liftToSelf(_.flatten)
  override def flattenDelayError[U](implicit ev: <:<[T, Observable[U]]): Self[U] =
    liftToSelf(_.flattenDelayError)
  override def concat[U](implicit ev: <:<[T, Observable[U]]): Self[U] =
    liftToSelf(_.concat)
  override def concatDelayError[U](implicit ev: <:<[T, Observable[U]]): Self[U] =
    liftToSelf(_.concatDelayError)
  override def merge[U](implicit ev: <:<[T, Observable[U]]): Self[U] =
    liftToSelf(_.merge)
  override def merge[U](overflowStrategy: OverflowStrategy)(implicit ev: <:<[T, Observable[U]]): Self[U] =
    liftToSelf(_.merge(overflowStrategy))
  override def merge[U](overflowStrategy: WithSignal, onOverflow: (Long) => U)(implicit ev: <:<[T, Observable[U]]): Self[U] =
    liftToSelf(_.merge(overflowStrategy, onOverflow))
  override def mergeDelayErrors[U](implicit ev: <:<[T, Observable[U]]): Self[U] =
    liftToSelf(_.mergeDelayErrors)
  override def mergeDelayErrors[U](overflowStrategy: OverflowStrategy)(implicit ev: <:<[T, Observable[U]]): Self[U] =
    liftToSelf(_.mergeDelayErrors(overflowStrategy))
  override def mergeDelayErrors[U](overflowStrategy: WithSignal, onOverflow: (Long) => U)(implicit ev: <:<[T, Observable[U]]): Self[U] =
    liftToSelf(_.mergeDelayErrors(overflowStrategy, onOverflow))
  override def switch[U](implicit ev: <:<[T, Observable[U]]): Self[U] =
    liftToSelf(_.switch)
  override def switchDelayErrors[U](implicit ev: <:<[T, Observable[U]]): Self[U] =
    liftToSelf(_.switchDelayErrors)
  override def flatMapLatest[U](implicit ev: <:<[T, Observable[U]]): Self[U] =
    liftToSelf(_.flatMapLatest)
  override def flatMapLatestDelayErrors[U](implicit ev: <:<[T, Observable[U]]): Self[U] =
    liftToSelf(_.flatMapLatestDelayErrors)
  override def ambWith[U >: T](other: Observable[U]): Self[U] =
    liftToSelf(_.ambWith(other))
  override def defaultIfEmpty[U >: T](default: U): Self[U] =
    liftToSelf(_.defaultIfEmpty(default))
  override def take(n: Long): Self[T] =
    liftToSelf(_.take(n))
  override def take(timespan: FiniteDuration): Self[T] =
    liftToSelf(_.take(timespan))
  override def takeRight(n: Int): Self[T] =
    liftToSelf(_.takeRight(n))
  override def drop(n: Int): Self[T] =
    liftToSelf(_.drop(n))
  override def dropByTimespan(timespan: FiniteDuration): Self[T] =
    liftToSelf(_.dropByTimespan(timespan))
  override def dropWhile(p: (T) => Boolean): Self[T] =
    liftToSelf(_.dropWhile(p))
  override def dropWhileWithIndex(p: (T, Int) => Boolean): Self[T] =
    liftToSelf(_.dropWhileWithIndex(p))
  override def takeWhile(p: (T) => Boolean): Self[T] =
    liftToSelf(_.takeWhile(p))
  override def takeWhileNotCanceled(c: BooleanCancelable): Self[T] =
    liftToSelf(_.takeWhileNotCanceled(c))
  override def count(): Self[Long] =
    liftToSelf(_.count())
  override def buffer(count: Int): Self[Seq[T]] =
    liftToSelf(_.buffer(count))
  override def buffer(count: Int, skip: Int): Self[Seq[T]] =
    liftToSelf(_.buffer(count, skip))
  override def buffer(timespan: FiniteDuration): Self[Seq[T]] =
    liftToSelf(_.buffer(timespan))
  override def buffer(timespan: FiniteDuration, maxSize: Int): Self[Seq[T]] =
    liftToSelf(_.buffer(timespan, maxSize))
  override def window(count: Int): Self[Observable[T]] =
    liftToSelf(_.window(count))
  override def window(count: Int, skip: Int): Self[Observable[T]] =
    liftToSelf(_.window(count, skip))
  override def window(timespan: FiniteDuration): Self[Observable[T]] =
    liftToSelf(_.window(timespan))
  override def window(timespan: FiniteDuration, maxCount: Int): Self[Observable[T]] =
    liftToSelf(_.window(timespan, maxCount))
  override def throttleLast(period: FiniteDuration): Self[T] =
    liftToSelf(_.throttleLast(period))
  override def throttleFirst(interval: FiniteDuration): Self[T] =
    liftToSelf(_.throttleFirst(interval))
  override def throttleWithTimeout(timeout: FiniteDuration): Self[T] =
    liftToSelf(_.throttleWithTimeout(timeout))
  override def sample(delay: FiniteDuration): Self[T] =
    liftToSelf(_.sample(delay))
  override def sample(initialDelay: FiniteDuration, delay: FiniteDuration): Self[T] =
    liftToSelf(_.sample(initialDelay, delay))
  override def sample[U](sampler: Observable[U]): Self[T] =
    liftToSelf(_.sample(sampler))
  override def sampleRepeated(delay: FiniteDuration): Self[T] =
    liftToSelf(_.sampleRepeated(delay))
  override def sampleRepeated(initialDelay: FiniteDuration, delay: FiniteDuration): Self[T] =
    liftToSelf(_.sampleRepeated(initialDelay, delay))
  override def sampleRepeated[U](sampler: Observable[U]): Self[T] =
    liftToSelf(_.sampleRepeated(sampler))
  override def debounce(timeout: FiniteDuration): Self[T] =
    liftToSelf(_.debounce(timeout))
  override def echoOnce(timeout: FiniteDuration): Self[T] =
    liftToSelf(_.echoOnce(timeout))
  override def echoRepeated(timeout: FiniteDuration): Self[T] =
    liftToSelf(_.echoRepeated(timeout))
  override def delaySubscription(future: Future[_]): Self[T] =
    liftToSelf(_.delaySubscription(future))
  override def delaySubscription(timespan: FiniteDuration): Self[T] =
    liftToSelf(_.delaySubscription(timespan))
  override def foldLeft[R](initial: R)(op: (R, T) => R): Self[R] =
    liftToSelf(_.foldLeft(initial)(op))
  override def reduce[U >: T](op: (U, U) => U): Self[U] =
    liftToSelf(_.reduce(op))
  override def scan[R](initial: R)(op: (R, T) => R): Self[R] =
    liftToSelf(_.scan(initial)(op))
  override def flatScan[R](initial: R)(op: (R, T) => Observable[R]): Self[R] =
    liftToSelf(_.flatScan(initial)(op))
  override def flatScanDelayError[R](initial: R)(op: (R, T) => Observable[R]): Self[R] =
    liftToSelf(_.flatScanDelayError(initial)(op))
  override def doOnComplete(cb: => Unit): Self[T] =
    liftToSelf(_.doOnComplete(cb))
  override def doWork(cb: (T) => Unit): Self[T] =
    liftToSelf(_.doWork(cb))
  override def doOnStart(cb: (T) => Unit): Self[T] =
    liftToSelf(_.doOnStart(cb))
  override def doOnCanceled(cb: => Unit): Self[T] =
    liftToSelf(_.doOnCanceled(cb))
  override def doOnError(cb: (Throwable) => Unit): Self[T] =
    liftToSelf(_.doOnError(cb))
  override def find(p: (T) => Boolean): Self[T] =
    liftToSelf(_.find(p))
  override def exists(p: (T) => Boolean): Self[Boolean] =
    liftToSelf(_.exists(p))
  override def isEmpty: Self[Boolean] =
    liftToSelf(_.isEmpty)
  override def nonEmpty: Self[Boolean] =
    liftToSelf(_.nonEmpty)
  override def forAll(p: (T) => Boolean): Self[Boolean] =
    liftToSelf(_.forAll(p))
  override def complete: Self[Nothing] =
    liftToSelf(_.complete)
  override def error: Self[Throwable] =
    liftToSelf(_.error)
  override def endWithError(error: Throwable): Self[T] =
    liftToSelf(_.endWithError(error))
  override def +:[U >: T](elem: U): Self[U] =
    liftToSelf(_.+:(elem))
  override def startWith[U >: T](elems: U*): Self[U] =
    liftToSelf(_.startWith(elems:_*))
  override def :+[U >: T](elem: U): Self[U] =
    liftToSelf(_.:+(elem))
  override def endWith[U >: T](elems: U*): Self[U] =
    liftToSelf(_.endWith(elems:_*))
  override def ++[U >: T](other: => Observable[U]): Self[U] =
    liftToSelf(_.++(other))
  override def head: Self[T] =
    liftToSelf(_.head)
  override def tail: Self[T] =
    liftToSelf(_.tail)
  override def last: Self[T] =
    liftToSelf(_.last)
  override def headOrElse[B >: T](default: => B): Self[B] =
    liftToSelf(_.headOrElse(default))
  override def firstOrElse[U >: T](default: => U): Self[U] =
    liftToSelf(_.firstOrElse(default))
  override def zip[U](other: Observable[U]): Self[(T, U)] =
    liftToSelf(_.zip(other))
  override def combineLatest[U](other: Observable[U]): Self[(T, U)] =
    liftToSelf(_.combineLatest(other))
  override def combineLatestDelayError[U](other: Observable[U]): Self[(T, U)] =
    liftToSelf(_.combineLatestDelayError(other))
  override def max[U >: T](implicit ev: Ordering[U]): Self[U] =
    liftToSelf(_.max(ev))
  override def maxBy[U](f: (T) => U)(implicit ev: Ordering[U]): Self[T] =
    liftToSelf(_.maxBy(f))
  override def min[U >: T](implicit ev: Ordering[U]): Self[U] =
    liftToSelf(_.min(ev))
  override def minBy[U](f: (T) => U)(implicit ev: Ordering[U]): Self[T] =
    liftToSelf(_.minBy(f))
  override def sum[U >: T](implicit ev: Numeric[U]): Self[U] =
    liftToSelf(_.sum(ev))
  override def distinct: Self[T] =
    liftToSelf(_.distinct)
  override def distinct[U](fn: (T) => U): Self[T] =
    liftToSelf(_.distinct(fn))
  override def distinctUntilChanged: Self[T] =
    liftToSelf(_.distinctUntilChanged)
  override def distinctUntilChanged[U](fn: (T) => U): Self[T] =
    liftToSelf(_.distinctUntilChanged(fn))
  override def subscribeOn(s: Scheduler): Self[T] =
    liftToSelf(_.subscribeOn(s))
  override def materialize: Self[Notification[T]] =
    liftToSelf(_.materialize)
  override def dump(prefix: String, out: PrintStream = System.out): Self[T] =
    liftToSelf(_.dump(prefix, out))
  override def repeat: Self[T] =
    liftToSelf(_.repeat)
  override def asyncBoundary(overflowStrategy: OverflowStrategy): Self[T] =
    liftToSelf(_.asyncBoundary(overflowStrategy))
  override def asyncBoundary[U >: T](overflowStrategy: WithSignal, onOverflow: (Long) => U): Self[U] =
    liftToSelf(_.asyncBoundary(overflowStrategy, onOverflow))
  override def whileBusyDropEvents: Self[T] =
    liftToSelf(_.whileBusyDropEvents)
  override def whileBusyDropEvents[U >: T](onOverflow: (Long) => U): Self[U] =
    liftToSelf(_.whileBusyDropEvents(onOverflow))
  override def whileBusyBuffer[U >: T](overflowStrategy: Synchronous): Self[U] =
    liftToSelf(_.whileBusyBuffer(overflowStrategy))
  override def whileBusyBuffer[U >: T](overflowStrategy: WithSignal, onOverflow: (Long) => U): Self[U] =
    liftToSelf(_.whileBusyBuffer(overflowStrategy, onOverflow))
  override def onErrorRecoverWith[U >: T](pf: PartialFunction[Throwable, Observable[U]]): Self[U] =
    liftToSelf(_.onErrorRecoverWith(pf))
  override def onErrorFallbackTo[U >: T](that: => Observable[U]): Self[U] =
    liftToSelf(_.onErrorFallbackTo(that))
  override def onErrorRetryUnlimited: Self[T] =
    liftToSelf(_.onErrorRetryUnlimited)
  override def onErrorRetry(maxRetries: Long): Self[T] =
    liftToSelf(_.onErrorRetry(maxRetries))
  override def onErrorRetryIf(p: (Throwable) => Boolean): Self[T] =
    liftToSelf(_.onErrorRetryIf(p))
  override def timeout(timeout: FiniteDuration): Self[T] =
    liftToSelf(_.timeout(timeout))
  override def timeout[U >: T](timeout: FiniteDuration, backup: Observable[U]): Self[U] =
    liftToSelf(_.timeout(timeout, backup))
  override def lift[U](f: (Observable[T]) => Observable[U]): Self[U] =
    liftToSelf(_.lift(f))
  override def groupBy[K](keySelector: (T) => K): Self[GroupedObservable[K, T]] =
    liftToSelf(_.groupBy(keySelector))
  override def groupBy[K](keyBufferSize: Int, keySelector: (T) => K): Self[GroupedObservable[K, T]] =
    liftToSelf(_.groupBy(keyBufferSize, keySelector))
}
