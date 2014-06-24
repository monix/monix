/*
 * Copyright (c) 2014 by its authors. Some rights reserved. 
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
 
package monifu.reactive

import monifu.concurrent.atomic.{Atomic, AtomicBoolean}
import monifu.concurrent.cancelables.BooleanCancelable
import monifu.concurrent.{Cancelable, Scheduler}
import monifu.reactive.Ack.{Cancel, Continue}
import monifu.reactive.BufferPolicy.BackPressured
import monifu.reactive.subjects.{BehaviorSubject, ConnectableSubject, PublishSubject, ReplaySubject}
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{Future, Promise}

/**
 * A `Subject` is a sort of bridge or proxy that acts both as an
 * [[Observer]] and as an [[Observable]] and that must respect the contract of both.
 *
 * Because it is a `Observer`, it can subscribe to an `Observable` and because it is an `Observable`,
 * it can pass through the items it observes by re-emitting them and it can also emit new items.
 *
 * Useful to build multicast Observables or reusable processing pipelines.
 */
trait Subject[-I, +T] extends Observable[T] with Observer[I] { self =>

  override final def scan[R](initial: R)(op: (R, T) => R): Subject[I, R] =
    lift(_.scan(initial)(op))

  override final def flatScan[R](initial: R)(op: (R, T) => Observable[R]): Subject[I, R] =
    lift(_.flatScan(initial)(op))

  override final def dropWhile(p: (T) => Boolean): Subject[I, T] =
    lift(_.dropWhile(p))

  override final def sum[U >: T](implicit ev: Numeric[U]): Subject[I, U] =
    lift(_.sum(ev))

  override final def headOrElse[B >: T](default: => B): Subject[I, B] =
    lift(_.headOrElse(default))

  override final def concat[U](implicit ev: <:<[T, Observable[U]]): Subject[I, U] =
    lift(_.concat)

  override final def reduce[U >: T](op: (U, U) => U): Subject[I, U] =
    lift(_.reduce(op))

  override final def multicast[R](subject: Subject[T, R]): ConnectableSubject[I, R] =
    new ConnectableSubject[I,R] {
      private[this] val notCanceled = Atomic(true)
      implicit val scheduler = self.scheduler

      @volatile private[this] var isConnected = false
      private[this] val connectedPromise = Promise[Ack]()
      private[this] var connectedFuture = connectedPromise.future

      private[this] val cancelAction =
        BooleanCancelable { notCanceled set false }

      private[this] val notConnected = Cancelable {
        self.takeWhile(notCanceled).unsafeSubscribe(subject)
        connectedPromise.success(Continue)
        isConnected = true
      }

      override final def onNext(elem: I): Future[Ack] = {
        if (isConnected)
          self.onNext(elem)
        else {
          connectedFuture = connectedFuture.flatMap {
            case Continue => self.onNext(elem)
            case Cancel => Cancel
          }
          connectedFuture
        }
      }

      override final def onError(ex: Throwable): Unit = {
        if (isConnected)
          self.onError(ex)
        else
          connectedFuture = connectedFuture.flatMap {
            case Continue => self.onError(ex); Cancel
            case Cancel => Cancel
          }
      }

      override final def onComplete() = {
        if (isConnected)
          self.onComplete()
        else
          connectedFuture = connectedFuture.flatMap {
            case Continue => self.onComplete(); Cancel
            case Cancel => Cancel
          }
      }

      override final def connect() = {
        notConnected.cancel()
        cancelAction
      }

      override final def subscribeFn(observer: Observer[R]): Unit =
        subject.unsafeSubscribe(observer)
    }

  override final def publish(): ConnectableSubject[I,T] =
    multicast(PublishSubject())

  override final def replay(): ConnectableSubject[I,T] =
    multicast(ReplaySubject())

  override final def behavior[U >: T](initialValue: U): ConnectableSubject[I,U] =
    multicast(BehaviorSubject(initialValue))

  override final def complete: Subject[I, Nothing] =
    lift(_.complete)

  override final def +:[U >: T](elem: U): Subject[I, U] =
    lift(_.:+(elem))

  override final def mergeMap[U](f: (T) => Observable[U]): Subject[I, U] =
    lift(_.mergeMap(f))

  override final def flatten[U](implicit ev: <:<[T, Observable[U]]): Subject[I, U] =
    lift(_.flatten(ev))

  override final def flatMap[U](f: (T) => Observable[U]): Subject[I, U] =
    lift(_.flatMap(f))

  override final def merge[U](bufferPolicy: BufferPolicy, batchSize: Int)(implicit ev: <:<[T, Observable[U]]) =
    lift(_.merge(bufferPolicy, batchSize))

  override final def concatMap[U](f: (T) => Observable[U]): Subject[I, U] =
    lift(_.concatMap(f))

  override final def find(p: (T) => Boolean): Subject[I, T] =
    lift(_.find(p))

  override final def doOnStart(cb: (T) => Unit): Subject[I, T] =
    lift(_.doOnStart(cb))

  override final def ++[U >: T](other: => Observable[U]): Subject[I, U] =
    lift(_ ++ other)

  override final def foldLeft[R](initial: R)(op: (R, T) => R): Subject[I, R] =
    lift(_.foldLeft(initial)(op))

  override final def concurrent: Subject[I, T] =
    lift(_.concurrent)

  override final def subscribeOn(s: Scheduler): Subject[I, T] =
    lift(_.subscribeOn(s))

  override final def minBy[U](f: (T) => U)(implicit ev: Ordering[U]): Subject[I, T] =
    lift(_.minBy(f)(ev))

  override final def startWith[U >: T](elems: U*): Subject[I, U] =
    lift(_.startWith(elems : _*))

  override final def tail: Subject[I, T] =
    lift(_.tail)

  override final def zip[U](other: Observable[U]): Subject[I, (T, U)] =
    lift(_.zip(other))

  override final def min[U >: T](implicit ev: Ordering[U]): Subject[I, T] =
    lift(_.min(ev))

  override final def materialize: Subject[I, Notification[T]] =
    lift(_.materialize)

  override final def exists(p: (T) => Boolean): Subject[I, Boolean] =
    lift(_.exists(p))

  override final def map[U](f: (T) => U): Subject[I, U] =
    lift(_.map(f))

  override final def head: Subject[I, T] =
    lift(_.head)

  override final def async(policy: BufferPolicy): Subject[I, T] =
    lift(_.async(policy))

  override final def maxBy[U](f: (T) => U)(implicit ev: Ordering[U]): Subject[I, T] =
    lift(_.maxBy(f)(ev))

  override final def firstOrElse[U >: T](default: => U): Subject[I, U] =
    lift(_.firstOrElse(default))

  override final def take(n: Int): Subject[I, T] =
    lift(_.take(n))

  override final def doWork(cb: (T) => Unit): Subject[I, T] =
    lift(_.doWork(cb))

  override final def repeat: Subject[I, T] =
    lift(_.repeat)

  override final def observeOn(s: Scheduler, bufferPolicy: BufferPolicy = BackPressured(1024)): Subject[I,T] =
    lift(_.observeOn(s, bufferPolicy))

  override final def :+[U >: T](elem: U): Subject[I, U] =
    lift(_.:+(elem))

  override final def last: Subject[I, T] =
    lift(_.last)

  override final def distinctUntilChanged[U](fn: (T) => U): Subject[I, T] =
    lift(_.distinctUntilChanged(fn))

  override final def distinctUntilChanged: Subject[I, T] =
    lift(_.distinctUntilChanged)

  override final def max[U >: T](implicit ev: Ordering[U]): Subject[I, U] =
    lift(_.max(ev))

  override final def takeRight(n: Int): Subject[I, T] =
    lift(_.takeRight(n))

  override final def error: Subject[I, Throwable] =
    lift(_.error)

  override final def forAll(p: (T) => Boolean): Subject[I, Boolean] =
    lift(_.forAll(p))

  override final def drop(n: Int): Subject[I, T] =
    lift(_.drop(n))

  override final def endWith[U >: T](elems: U*): Subject[I, U] =
    lift(_.endWith(elems : _*))

  override final def doOnComplete(cb: => Unit): Subject[I, T] =
    lift(_.doOnComplete(cb))

  override final def safe: Subject[I, T] =
    lift(_.safe)

  override final def filter(p: (T) => Boolean): Subject[I, T] =
    lift(_.filter(p))

  override final def distinct[U](fn: (T) => U): Subject[I, T] =
    lift(_.distinct(fn))

  override final def distinct: Subject[I, T] =
    lift(_.distinct)

  override final def dump(prefix: String) =
    lift(_.dump(prefix))

  override final def takeWhile(p: (T) => Boolean): Subject[I, T] =
    lift(_.takeWhile(p))

  override final def takeWhile(isRefTrue: AtomicBoolean): Subject[I, T] =
    lift(_.takeWhile(isRefTrue))

  override final def endWithError(error: Throwable): Subject[I, T] =
    lift(_.endWithError(error))

  override final def ambWith[U >: T](other: Observable[U]): Subject[I, U] =
    lift(_.ambWith(other))

  override final def take(timespan: FiniteDuration): Subject[I, T] =
    lift(_.take(timespan))

  override final def drop(timespan: FiniteDuration): Subject[I, T] =
    lift(_.drop(timespan))

  override final def buffer(count: Int): Subject[I, Seq[T]] =
    lift(_.buffer(count))

  override final def buffer(timespan: FiniteDuration): Subject[I, Seq[T]] =
    lift(_.buffer(timespan))

  override final def defaultIfEmpty[U >: T](default: U): Subject[I, U] =
    lift(_.defaultIfEmpty(default))

  override final def dropWhileWithIndex(p: (T, Int) => Boolean): Subject[I, T] =
    lift(_.dropWhileWithIndex(p))

  override final def lift[U](f: Observable[T] => Observable[U]): Subject[I,U] = {
    new Subject[I,U] {
      implicit val scheduler = self.scheduler

      override final def onNext(elem: I) =
        self.onNext(elem)

      override final def onError(ex: Throwable) =
        self.onError(ex)

      override final def onComplete() =
        self.onComplete()

      private[this] val observableU = {
        val observableT = Observable.create[T](o => self.unsafeSubscribe(o))
        f(observableT)
      }

      override final def subscribeFn(observer: Observer[U]) = {
        observableU.unsafeSubscribe(observer)
      }
    }
  }
}

