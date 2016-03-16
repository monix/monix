/*
 * Copyright (c) 2014-2016 by its authors. Some rights reserved.
 * See the project homepage at: https://monix.io
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

package monix.types.instances

import _root_.cats.Eval
import monix.reactive.Observable
import monix.types.{Async, Streamable}
import scala.concurrent.duration.FiniteDuration
import scala.language.{higherKinds, implicitConversions}

trait ObservableInstances {
  /** The [[Streamable]] type-class implemented for [[monix.reactive.Observable]]. */
  implicit val observableInstances: Streamable[Observable] with Async[Observable] =
    new Streamable[Observable] with Async[Observable] {
      override def raiseError[A](e: Throwable): Observable[A] =
        Observable.error(e)
      override def pure[A](x: A): Observable[A] =
        Observable.now(x)
      override def fromIterable[A](fa: Iterable[A]): Observable[A] =
        Observable.fromIterable(fa)
      override def pureEval[A](x: Eval[A]): Observable[A] =
        Observable.eval(x.value)
      override def empty[A]: Observable[A] =
        Observable.empty
      override def cons[A](head: A, tail: Eval[Observable[A]]): Observable[A] =
        Observable.cons(head, tail.value)
      override def delayedEval[A](delay: FiniteDuration, a: Eval[A]): Observable[A] =
        Observable.eval(a.value)

      override def concatMap[A, B](fa: Observable[A])(f: (A) => Observable[B]): Observable[B] =
        fa.concatMap(f)
      override def concat[A](ffa: Observable[Observable[A]]): Observable[A] =
        ffa.concat
      override def concatMapDelayError[A, B](fa: Observable[A])(f: (A) => Observable[B]): Observable[B] =
        fa.concatMapDelayError(f)
      override def concatDelayError[A](ffa: Observable[Observable[A]]): Observable[A] =
        ffa.concatDelayError
      override def followWith[A](fa: Observable[A], other: => Observable[A]): Observable[A] =
        fa ++ other
      override def startWith[A](fa: Observable[A])(elems: Seq[A]): Observable[A] =
        fa.startWith(elems)
      override def startWithElem[A](fa: Observable[A])(elem: A): Observable[A] =
        Observable.concat(Observable.now(elem), fa)
      override def endWith[A](fa: Observable[A])(elems: Seq[A]): Observable[A] =
        fa.endWith(elems)
      override def endWithElem[A](fa: Observable[A])(elem: A): Observable[A] =
        Observable.concat(fa, Observable.now(elem))

      override def repeat[A](fa: Observable[A]): Observable[A] =
        fa.repeat
      override def endWithError[A](fa: Observable[A], error: Throwable): Observable[A] =
        fa.endWithError(error)

      override def filter[A](fa: Observable[A])(f: (A) => Boolean): Observable[A] =
        fa.filter(f)
      override def collect[A, B](fa: Observable[A])(pf: PartialFunction[A, B]): Observable[B] =
        fa.collect(pf)

      override def onErrorRecoverWith[A](fa: Observable[A])(pf: PartialFunction[Throwable, Observable[A]]): Observable[A] =
        fa.onErrorRecoverWith(pf)
      override def onErrorRecover[A](fa: Observable[A])(pf: PartialFunction[Throwable, A]): Observable[A] =
        fa.onErrorRecover(pf)
      override def onErrorFallbackTo[A](fa: Observable[A], other: Eval[Observable[A]]): Observable[A] =
        fa.onErrorFallbackTo(other.value)
      override def onErrorRetry[A](fa: Observable[A], maxRetries: Long): Observable[A] =
        fa.onErrorRetry(maxRetries)
      override def onErrorRetryIf[A](fa: Observable[A])(p: (Throwable) => Boolean): Observable[A] =
        fa.onErrorRetryIf(p)
      override def failed[A](fa: Observable[A]): Observable[Throwable] =
        fa.failed

      override def map[A, B](fa: Observable[A])(f: (A) => B): Observable[B] =
        fa.map(f)
      override def take[A](fa: Observable[A], n: Int): Observable[A] =
        fa.take(n)
      override def takeWhile[A](fa: Observable[A])(f: (A) => Boolean): Observable[A] =
        fa.takeWhile(f)
      override def takeLast[A](fa: Observable[A], n: Int): Observable[A] =
        fa.takeLast(n)
      override def drop[A](fa: Observable[A], n: Int): Observable[A] =
        fa.drop(n)
      override def dropWhile[A](fa: Observable[A])(f: (A) => Boolean): Observable[A] =
        fa.dropWhile(f)
      override def dropLast[A](fa: Observable[A], n: Int): Observable[A] =
        fa.dropLast(n)
      override def foldLeftF[A, S](fa: Observable[A], seed: S)(f: (S, A) => S): Observable[S] =
        fa.foldLeftF(seed)(f)

      override def zip2[A1, A2](fa1: Observable[A1], fa2: Observable[A2]): Observable[(A1, A2)] =
        Observable.zip2(fa1, fa2)
      override def zipWith2[A1, A2, R](fa1: Observable[A1], fa2: Observable[A2])(f: (A1, A2) => R): Observable[R] =
        Observable.zipWith2(fa1, fa2)(f)
      override def zip3[A1, A2, A3](fa1: Observable[A1], fa2: Observable[A2], fa3: Observable[A3]): Observable[(A1, A2, A3)] =
        Observable.zip3(fa1,fa2,fa3)
      override def zipWith3[A1, A2, A3, R](fa1: Observable[A1], fa2: Observable[A2], fa3: Observable[A3])(f: (A1, A2, A3) => R): Observable[R] =
        Observable.zipWith3(fa1,fa2,fa3)(f)
      override def zip4[A1, A2, A3, A4](fa1: Observable[A1], fa2: Observable[A2], fa3: Observable[A3], fa4: Observable[A4]): Observable[(A1, A2, A3, A4)] =
        Observable.zip4(fa1,fa2,fa3,fa4)
      override def zipWith4[A1, A2, A3, A4, R](fa1: Observable[A1], fa2: Observable[A2], fa3: Observable[A3], fa4: Observable[A4])(f: (A1, A2, A3, A4) => R): Observable[R] =
        Observable.zipWith4(fa1,fa2,fa3,fa4)(f)
      override def zip5[A1, A2, A3, A4, A5](fa1: Observable[A1], fa2: Observable[A2], fa3: Observable[A3], fa4: Observable[A4], fa5: Observable[A5]): Observable[(A1, A2, A3, A4, A5)] =
        Observable.zip5(fa1,fa2,fa3,fa4,fa5)
      override def zipWith5[A1, A2, A3, A4, A5, R](fa1: Observable[A1], fa2: Observable[A2], fa3: Observable[A3], fa4: Observable[A4], fa5: Observable[A5])(f: (A1, A2, A3, A4, A5) => R): Observable[R] =
        Observable.zipWith5(fa1,fa2,fa3,fa4,fa5)(f)
      override def zip6[A1, A2, A3, A4, A5, A6](fa1: Observable[A1], fa2: Observable[A2], fa3: Observable[A3], fa4: Observable[A4], fa5: Observable[A5], fa6: Observable[A6]): Observable[(A1, A2, A3, A4, A5, A6)] =
        Observable.zip6(fa1,fa2,fa3,fa4,fa5,fa6)
      override def zipWith6[A1, A2, A3, A4, A5, A6, R](fa1: Observable[A1], fa2: Observable[A2], fa3: Observable[A3], fa4: Observable[A4], fa5: Observable[A5], fa6: Observable[A6])(f: (A1, A2, A3, A4, A5, A6) => R): Observable[R] =
        Observable.zipWith6(fa1,fa2,fa3,fa4,fa5,fa6)(f)
      override def zipList[A](sources: Seq[Observable[A]]): Observable[Seq[A]] =
        Observable.zipList(sources:_*)

      override def scan[A, S](fa: Observable[A], seed: S)(f: (S, A) => S): Observable[S] =
        fa.scan(seed)(f)

      override def distinct[A](fa: Observable[A]): Observable[A] =
        fa.distinct
      override def distinctByKey[A, Key](fa: Observable[A])(key: (A) => Key): Observable[A] =
        fa.distinctByKey(key)
      override def distinctUntilChanged[A](fa: Observable[A]): Observable[A] =
        fa.distinctUntilChanged
      override def distinctUntilChangedByKey[A, Key](fa: Observable[A])(key: (A) => Key): Observable[A] =
        fa.distinctUntilChangedByKey(key)
      override def headF[A](fa: Observable[A]): Observable[A] =
        fa.headF
      override def headOrElseF[A](fa: Observable[A], default: Eval[A]): Observable[A] =
        fa.headOrElseF(default.value)
      override def firstOrElseF[A](fa: Observable[A], default: Eval[A]): Observable[A] =
        fa.headOrElseF(default.value)
      override def lastF[A](fa: Observable[A]): Observable[A] =
        fa.lastF
      override def tail[A](fa: Observable[A]): Observable[A] =
        fa.tail

      override def countF[A](fa: Observable[A]): Observable[Long] =
        fa.countF
      override def existsF[A](fa: Observable[A])(p: (A) => Boolean): Observable[Boolean] =
        fa.existsF(p)
      override def findOptF[A](fa: Observable[A])(p: (A) => Boolean): Observable[Option[A]] =
        fa.findF(p).map(Option.apply).headOrElseF(None)
      override def forAllF[A](fa: Observable[A])(p: (A) => Boolean): Observable[Boolean] =
        fa.forAllF(p)
      override def isEmptyF[A](fa: Observable[A]): Observable[Boolean] =
        fa.isEmptyF
      override def nonEmptyF[A](fa: Observable[A]): Observable[Boolean] =
        fa.nonEmptyF
      override def sumF[A: Numeric](fa: Observable[A]): Observable[A] =
        fa.sumF

      override def buffer[A](fa: Observable[A])(count: Int): Observable[Seq[A]] =
        fa.buffer(count)
      override def bufferSkipped[A](fa: Observable[A], count: Int, skip: Int): Observable[Seq[A]] =
        fa.buffer(count, skip)
      override def completed[A](fa: Observable[A]): Observable[A] =
        fa.completed

      override def maxF[A](fa: Observable[A])(implicit A: Ordering[A]): Observable[A] =
        fa.maxF
      override def maxByF[A, B](fa: Observable[A])(f: (A) => B)(implicit B: Ordering[B]): Observable[A] =
        fa.maxByF(f)
      override def minF[A](fa: Observable[A])(implicit A: Ordering[A]): Observable[A] =
        fa.minF
      override def minByF[A, B](fa: Observable[A])(f: (A) => B)(implicit B: Ordering[B]): Observable[A] =
        fa.minByF(f)

      override def firstStartedOf[A](faList: Seq[Observable[A]]): Observable[A] =
        Observable.firstStartedOf(faList:_*)
      override def delayExecution[A](fa: Observable[A], timespan: FiniteDuration): Observable[A] =
        fa.delaySubscription(timespan)
      override def delayExecutionWith[A, B](fa: Observable[A], trigger: Observable[B]): Observable[A] =
        fa.delaySubscriptionWith(trigger)
      override def delayResult[A](fa: Observable[A], timespan: FiniteDuration): Observable[A] =
        fa.delayOnNext(timespan)
      override def delayResultBySelector[A, B](fa: Observable[A])(selector: (A) => Observable[B]): Observable[A] =
        fa.delayOnNextBySelector(selector)

      override def timeout[A](fa: Observable[A], timespan: FiniteDuration): Observable[A] =
        fa.timeoutOnSlowUpstream(timespan)
      override def timeoutTo[A](fa: Observable[A], timespan: FiniteDuration, backup: Eval[Observable[A]]): Observable[A] =
        fa.timeoutOnSlowUpstreamTo(timespan, backup.value)

      override def coflatMap[A, B](fa: Observable[A])(f: (Observable[A]) => B): Observable[B] =
        Observable.eval(f(fa))
      override def coflatten[A](fa: Observable[A]): Observable[Observable[A]] =
        Observable.now(fa)
    }
}

object observable extends ObservableInstances
