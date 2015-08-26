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

import language.higherKinds
import java.io.PrintStream
import monifu.concurrent.Scheduler
import monifu.concurrent.cancelables.BooleanCancelable
import monifu.reactive.OverflowStrategy.{Synchronous, WithSignal}
import monifu.reactive.{Notification, Observable, OverflowStrategy}
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

/**
 * An interface to be extended in Observable types that want to preserve
 * the return type when applying operators. For example the result of
 * [[monifu.reactive.Subject.map Subject.map]]
 * is still a `Subject` and this interface represents
 * an utility to do just that.
 */
trait LiftOperators2[I, +T, Self[A,+B] <: Observable[B]] { self: Observable[T] =>
  protected def liftToSelf[U](f: Observable[T] => Observable[U]): Self[I,U]

  override def map[U](f: T => U): Self[I,U] =
    liftToSelf(_.map(f))
  override def filter(p: (T) => Boolean): Self[I,T] =
    liftToSelf(_.filter(p))
  override def collect[U](pf: PartialFunction[T, U]): Self[I,U] =
    liftToSelf(_.collect(pf))
  override def flatMap[U](f: (T) => Observable[U]): Self[I,U] =
    liftToSelf(_.flatMap(f))
  override def flatMapDelayError[U](f: (T) => Observable[U]): Self[I,U] =
    liftToSelf(_.flatMapDelayError(f))
  override def concatMap[U](f: (T) => Observable[U]): Self[I,U] =
    liftToSelf(_.concatMap(f))
  override def concatMapDelayError[U](f: (T) => Observable[U]): Self[I,U] =
    liftToSelf(_.concatMapDelayError(f))
  override def mergeMap[U](f: (T) => Observable[U]): Self[I,U] =
    liftToSelf(_.mergeMap(f))
  override def mergeMapDelayErrors[U](f: (T) => Observable[U]): Self[I,U] =
    liftToSelf(_.mergeMapDelayErrors(f))
  override def flatten[U](implicit ev: <:<[T, Observable[U]]): Self[I,U] =
    liftToSelf(_.flatten)
  override def flattenDelayError[U](implicit ev: <:<[T, Observable[U]]): Self[I,U] =
    liftToSelf(_.flattenDelayError)
  override def concat[U](implicit ev: <:<[T, Observable[U]]): Self[I,U] =
    liftToSelf(_.concat)
  override def concatDelayError[U](implicit ev: <:<[T, Observable[U]]): Self[I,U] =
    liftToSelf(_.concatDelayError)
  override def merge[U](implicit ev: <:<[T, Observable[U]]): Self[I,U] =
    liftToSelf(_.merge)
  override def merge[U](overflowStrategy: OverflowStrategy)(implicit ev: <:<[T, Observable[U]]): Self[I,U] =
    liftToSelf(_.merge(overflowStrategy))
  override def merge[U](overflowStrategy: WithSignal, onOverflow: (Long) => U)(implicit ev: <:<[T, Observable[U]]): Self[I,U] =
    liftToSelf(_.merge(overflowStrategy, onOverflow))
  override def mergeDelayErrors[U](implicit ev: <:<[T, Observable[U]]): Self[I,U] =
    liftToSelf(_.mergeDelayErrors)
  override def mergeDelayErrors[U](overflowStrategy: OverflowStrategy)(implicit ev: <:<[T, Observable[U]]): Self[I,U] =
    liftToSelf(_.mergeDelayErrors(overflowStrategy))
  override def mergeDelayErrors[U](overflowStrategy: WithSignal, onOverflow: (Long) => U)(implicit ev: <:<[T, Observable[U]]): Self[I,U] =
    liftToSelf(_.mergeDelayErrors(overflowStrategy, onOverflow))
  override def switch[U](implicit ev: <:<[T, Observable[U]]): Self[I,U] =
    liftToSelf(_.switch)
  override def switchDelayErrors[U](implicit ev: <:<[T, Observable[U]]): Self[I,U] =
    liftToSelf(_.switchDelayErrors)
  override def flatMapLatest[U](implicit ev: <:<[T, Observable[U]]): Self[I,U] =
    liftToSelf(_.flatMapLatest)
  override def flatMapLatestDelayErrors[U](implicit ev: <:<[T, Observable[U]]): Self[I,U] =
    liftToSelf(_.flatMapLatestDelayErrors)
  override def ambWith[U >: T](other: Observable[U]): Self[I,U] =
    liftToSelf(_.ambWith(other))
  override def defaultIfEmpty[U >: T](default: U): Self[I,U] =
    liftToSelf(_.defaultIfEmpty(default))
  override def take(n: Long): Self[I,T] =
    liftToSelf(_.take(n))
  override def take(timespan: FiniteDuration): Self[I,T] =
    liftToSelf(_.take(timespan))
  override def takeRight(n: Int): Self[I,T] =
    liftToSelf(_.takeRight(n))
  override def drop(n: Int): Self[I,T] =
    liftToSelf(_.drop(n))
  override def dropByTimespan(timespan: FiniteDuration): Self[I,T] =
    liftToSelf(_.dropByTimespan(timespan))
  override def dropWhile(p: (T) => Boolean): Self[I,T] =
    liftToSelf(_.dropWhile(p))
  override def dropWhileWithIndex(p: (T, Int) => Boolean): Self[I,T] =
    liftToSelf(_.dropWhileWithIndex(p))
  override def takeWhile(p: (T) => Boolean): Self[I,T] =
    liftToSelf(_.takeWhile(p))
  override def takeWhileNotCanceled(c: BooleanCancelable): Self[I,T] =
    liftToSelf(_.takeWhileNotCanceled(c))
  override def count(): Self[I,Long] =
    liftToSelf(_.count())
  override def buffer(count: Int): Self[I,Seq[T]] =
    liftToSelf(_.buffer(count))
  override def buffer(count: Int, skip: Int): Self[I,Seq[T]] =
    liftToSelf(_.buffer(count, skip))
  override def buffer(timespan: FiniteDuration): Self[I,Seq[T]] =
    liftToSelf(_.buffer(timespan))
  override def buffer(timespan: FiniteDuration, maxSize: Int): Self[I,Seq[T]] =
    liftToSelf(_.buffer(timespan, maxSize))
  override def window(count: Int): Self[I,Observable[T]] =
    liftToSelf(_.window(count))
  override def window(count: Int, skip: Int): Self[I,Observable[T]] =
    liftToSelf(_.window(count, skip))
  override def window(timespan: FiniteDuration): Self[I,Observable[T]] =
    liftToSelf(_.window(timespan))
  override def window(timespan: FiniteDuration, maxCount: Int): Self[I,Observable[T]] =
    liftToSelf(_.window(timespan, maxCount))
  override def throttleLast(period: FiniteDuration): Self[I,T] =
    liftToSelf(_.throttleLast(period))
  override def throttleFirst(interval: FiniteDuration): Self[I,T] =
    liftToSelf(_.throttleFirst(interval))
  override def throttleWithTimeout(timeout: FiniteDuration): Self[I,T] =
    liftToSelf(_.throttleWithTimeout(timeout))
  override def sample(delay: FiniteDuration): Self[I,T] =
    liftToSelf(_.sample(delay))
  override def sample(initialDelay: FiniteDuration, delay: FiniteDuration): Self[I,T] =
    liftToSelf(_.sample(initialDelay, delay))
  override def sample[U](sampler: Observable[U]): Self[I,T] =
    liftToSelf(_.sample(sampler))
  override def sampleRepeated(delay: FiniteDuration): Self[I,T] =
    liftToSelf(_.sampleRepeated(delay))
  override def sampleRepeated(initialDelay: FiniteDuration, delay: FiniteDuration): Self[I,T] =
    liftToSelf(_.sampleRepeated(initialDelay, delay))
  override def sampleRepeated[U](sampler: Observable[U]): Self[I,T] =
    liftToSelf(_.sampleRepeated(sampler))
  override def debounce(timeout: FiniteDuration): Self[I,T] =
    liftToSelf(_.debounce(timeout))
  override def echoOnce(timeout: FiniteDuration): Self[I,T] =
    liftToSelf(_.echoOnce(timeout))
  override def echoRepeated(timeout: FiniteDuration): Self[I,T] =
    liftToSelf(_.echoRepeated(timeout))
  override def delaySubscription(future: Future[_]): Self[I,T] =
    liftToSelf(_.delaySubscription(future))
  override def delaySubscription(timespan: FiniteDuration): Self[I,T] =
    liftToSelf(_.delaySubscription(timespan))
  override def foldLeft[R](initial: R)(op: (R, T) => R): Self[I,R] =
    liftToSelf(_.foldLeft(initial)(op))
  override def reduce[U >: T](op: (U, U) => U): Self[I,U] =
    liftToSelf(_.reduce(op))
  override def scan[R](initial: R)(op: (R, T) => R): Self[I,R] =
    liftToSelf(_.scan(initial)(op))
  override def flatScan[R](initial: R)(op: (R, T) => Observable[R]): Self[I,R] =
    liftToSelf(_.flatScan(initial)(op))
  override def flatScanDelayError[R](initial: R)(op: (R, T) => Observable[R]): Self[I,R] =
    liftToSelf(_.flatScanDelayError(initial)(op))
  override def doOnComplete(cb: => Unit): Self[I,T] =
    liftToSelf(_.doOnComplete(cb))
  override def doWork(cb: (T) => Unit): Self[I,T] =
    liftToSelf(_.doWork(cb))
  override def doOnStart(cb: (T) => Unit): Self[I,T] =
    liftToSelf(_.doOnStart(cb))
  override def doOnCanceled(cb: => Unit): Self[I,T] =
    liftToSelf(_.doOnCanceled(cb))
  override def doOnError(cb: (Throwable) => Unit): Self[I,T] =
    liftToSelf(_.doOnError(cb))
  override def find(p: (T) => Boolean): Self[I,T] =
    liftToSelf(_.find(p))
  override def exists(p: (T) => Boolean): Self[I,Boolean] =
    liftToSelf(_.exists(p))
  override def isEmpty: Self[I,Boolean] =
    liftToSelf(_.isEmpty)
  override def nonEmpty: Self[I,Boolean] =
    liftToSelf(_.nonEmpty)
  override def forAll(p: (T) => Boolean): Self[I,Boolean] =
    liftToSelf(_.forAll(p))
  override def complete: Self[I,Nothing] =
    liftToSelf(_.complete)
  override def error: Self[I,Throwable] =
    liftToSelf(_.error)
  override def endWithError(error: Throwable): Self[I,T] =
    liftToSelf(_.endWithError(error))
  override def +:[U >: T](elem: U): Self[I,U] =
    liftToSelf(_.+:(elem))
  override def startWith[U >: T](elems: U*): Self[I,U] =
    liftToSelf(_.startWith(elems:_*))
  override def :+[U >: T](elem: U): Self[I,U] =
    liftToSelf(_.:+(elem))
  override def endWith[U >: T](elems: U*): Self[I,U] =
    liftToSelf(_.endWith(elems:_*))
  override def ++[U >: T](other: => Observable[U]): Self[I,U] =
    liftToSelf(_.++(other))
  override def head: Self[I,T] =
    liftToSelf(_.head)
  override def tail: Self[I,T] =
    liftToSelf(_.tail)
  override def last: Self[I,T] =
    liftToSelf(_.last)
  override def headOrElse[B >: T](default: => B): Self[I,B] =
    liftToSelf(_.headOrElse(default))
  override def firstOrElse[U >: T](default: => U): Self[I,U] =
    liftToSelf(_.firstOrElse(default))
  override def zip[U](other: Observable[U]): Self[I,(T, U)] =
    liftToSelf(_.zip(other))
  override def combineLatest[U](other: Observable[U]): Self[I,(T, U)] =
    liftToSelf(_.combineLatest(other))
  override def combineLatestDelayError[U](other: Observable[U]): Self[I,(T, U)] =
    liftToSelf(_.combineLatestDelayError(other))
  override def max[U >: T](implicit ev: Ordering[U]): Self[I,U] =
    liftToSelf(_.max(ev))
  override def maxBy[U](f: (T) => U)(implicit ev: Ordering[U]): Self[I,T] =
    liftToSelf(_.maxBy(f))
  override def min[U >: T](implicit ev: Ordering[U]): Self[I,U] =
    liftToSelf(_.min(ev))
  override def minBy[U](f: (T) => U)(implicit ev: Ordering[U]): Self[I,T] =
    liftToSelf(_.minBy(f))
  override def sum[U >: T](implicit ev: Numeric[U]): Self[I,U] =
    liftToSelf(_.sum(ev))
  override def distinct: Self[I,T] =
    liftToSelf(_.distinct)
  override def distinct[U](fn: (T) => U): Self[I,T] =
    liftToSelf(_.distinct(fn))
  override def distinctUntilChanged: Self[I,T] =
    liftToSelf(_.distinctUntilChanged)
  override def distinctUntilChanged[U](fn: (T) => U): Self[I,T] =
    liftToSelf(_.distinctUntilChanged(fn))
  override def subscribeOn(s: Scheduler): Self[I,T] =
    liftToSelf(_.subscribeOn(s))
  override def materialize: Self[I,Notification[T]] =
    liftToSelf(_.materialize)
  override def dump(prefix: String, out: PrintStream = System.out): Self[I,T] =
    liftToSelf(_.dump(prefix, out))
  override def repeat: Self[I,T] =
    liftToSelf(_.repeat)
  override def asyncBoundary(overflowStrategy: OverflowStrategy): Self[I,T] =
    liftToSelf(_.asyncBoundary(overflowStrategy))
  override def asyncBoundary[U >: T](overflowStrategy: WithSignal, onOverflow: (Long) => U): Self[I,U] =
    liftToSelf(_.asyncBoundary(overflowStrategy, onOverflow))
  override def whileBusyDropEvents: Self[I,T] =
    liftToSelf(_.whileBusyDropEvents)
  override def whileBusyDropEvents[U >: T](onOverflow: (Long) => U): Self[I,U] =
    liftToSelf(_.whileBusyDropEvents(onOverflow))
  override def whileBusyBuffer[U >: T](overflowStrategy: Synchronous): Self[I,U] =
    liftToSelf(_.whileBusyBuffer(overflowStrategy))
  override def whileBusyBuffer[U >: T](overflowStrategy: WithSignal, onOverflow: (Long) => U): Self[I,U] =
    liftToSelf(_.whileBusyBuffer(overflowStrategy, onOverflow))
  override def onErrorRecoverWith[U >: T](pf: PartialFunction[Throwable, Observable[U]]): Self[I,U] =
    liftToSelf(_.onErrorRecoverWith(pf))
  override def onErrorFallbackTo[U >: T](that: => Observable[U]): Self[I,U] =
    liftToSelf(_.onErrorFallbackTo(that))
  override def onErrorRetryUnlimited: Self[I,T] =
    liftToSelf(_.onErrorRetryUnlimited)
  override def onErrorRetry(maxRetries: Long): Self[I,T] =
    liftToSelf(_.onErrorRetry(maxRetries))
  override def onErrorRetryIf(p: (Throwable) => Boolean): Self[I,T] =
    liftToSelf(_.onErrorRetryIf(p))
  override def timeout(timeout: FiniteDuration): Self[I,T] =
    liftToSelf(_.timeout(timeout))
  override def timeout[U >: T](timeout: FiniteDuration, backup: Observable[U]): Self[I,U] =
    liftToSelf(_.timeout(timeout, backup))
  override def lift[U](f: (Observable[T]) => Observable[U]): Self[I,U] =
    liftToSelf(_.lift(f))
  override def groupBy[K](keySelector: (T) => K): Self[I,GroupedObservable[K, T]] =
    liftToSelf(_.groupBy(keySelector))
  override def groupBy[K](keyBufferSize: Int, keySelector: (T) => K): Self[I,GroupedObservable[K, T]] =
    liftToSelf(_.groupBy(keyBufferSize, keySelector))
}
