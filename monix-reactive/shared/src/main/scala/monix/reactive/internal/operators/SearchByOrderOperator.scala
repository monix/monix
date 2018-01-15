/*
 * Copyright (c) 2014-2018 by The Monix Project Developers.
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

package monix.reactive.internal.operators

import cats.Order
import monix.execution.Ack
import monix.execution.Ack.{Continue, Stop}
import monix.execution.misc.NonFatal
import monix.reactive.Observable.Operator
import monix.reactive.observers.Subscriber

/**
  * Common implementation for `minF`, `minByF`, `maxF`, `maxByF`.
  */
private[reactive] abstract class SearchByOrderOperator[A, K]
  (key: A => K)(implicit B: Order[K]) extends Operator[A,A] {

  def shouldCollect(key: K, current: K): Boolean
  
  final def apply(out: Subscriber[A]): Subscriber.Sync[A] =
    new Subscriber.Sync[A] {
      implicit val scheduler = out.scheduler

      private[this] var isDone = false
      private[this] var minValue: A = _
      private[this] var minValueU: K = _
      private[this] var hasValue = false

      def onNext(elem: A): Ack = {
        try {
          if (!hasValue) {
            hasValue = true
            minValue = elem
            minValueU = key(elem)
          } else {
            val m = key(elem)
            if (shouldCollect(m, minValueU)) {
              minValue = elem
              minValueU = m
            }
          }

          Continue
        } catch {
          case ex if NonFatal(ex) =>
            onError(ex)
            Stop
        }
      }

      def onError(ex: Throwable): Unit =
        if (!isDone) {
          isDone = true
          out.onError(ex)
        }

      def onComplete(): Unit =
        if (!isDone) {
          isDone = true
          if (!hasValue)
            out.onComplete()
          else {
            out.onNext(minValue)
            out.onComplete()
          }
        }
    }
}

private[reactive] final class MinOperator[A](implicit A: Order[A])
  extends SearchByOrderOperator[A,A](identity)(A) {
  
  def shouldCollect(key: A, current: A): Boolean =
    A.compare(key, current) < 0
}

private[reactive] final class MinByOperator[A, K](f: A => K)
  (implicit K: Order[K])
  extends SearchByOrderOperator[A, K](f)(K) {

  def shouldCollect(key: K, current: K): Boolean =
    K.compare(key, current) < 0
}

private[reactive] final class MaxOperator[A](implicit A: Order[A])
  extends SearchByOrderOperator[A,A](identity)(A) {

  def shouldCollect(key: A, current: A): Boolean =
    A.compare(key, current) > 0
}

private[reactive] final class MaxByOperator[A, K](f: A => K)
  (implicit K: Order[K])
  extends SearchByOrderOperator[A, K](f)(K) {

  def shouldCollect(key: K, current: K): Boolean =
    K.compare(key, current) > 0
}