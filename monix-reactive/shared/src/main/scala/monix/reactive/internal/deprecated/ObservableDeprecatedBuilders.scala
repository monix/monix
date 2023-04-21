/*
 * Copyright (c) 2014-2022 Monix Contributors.
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

package monix.reactive.internal.deprecated

import cats.Eval
import cats.effect.IO
import monix.execution.Scheduler
import monix.reactive.{ Observable, OverflowStrategy }

private[reactive] trait ObservableDeprecatedBuilders extends Any {
  /** DEPRECATED — please use [[Observable!.executeAsync .executeAsync]].
    *
    * The reason for the deprecation is the repurposing of the word "fork"
    * in [[monix.eval.Task Task]].
    */
  @deprecated("Please use Observable!.executeAsync", "3.0.0")
  def fork[A](fa: Observable[A]): Observable[A] = {
    // $COVERAGE-OFF$
    fa.executeAsync
    // $COVERAGE-ON$
  }

  /** DEPRECATED — please use [[Observable!.executeOn .executeOn]].
    *
    * The reason for the deprecation is the repurposing of the word "fork"
    * in [[monix.eval.Task Task]].
    */
  @deprecated("Please use Observable!.executeOn", "3.0.0")
  def fork[A](fa: Observable[A], scheduler: Scheduler): Observable[A] =
    fa.executeOn(scheduler)

  /** DEPRECATED — please switch to the [[Observable!.flatten flatten]] method.
    *
    * This deprecation was made because there's no point in having this
    * function described both as a method and as a companion object function.
    * In general in API design we either have both for all functions, or
    * we have to choose.
    *
    * Switch to: `Observable(list).flatten`
    */
  @deprecated("Switch to Observable(list).flatten", "3.0.0")
  def flatten[A](sources: Observable[A]*): Observable[A] = {
    // $COVERAGE-OFF$
    Observable.fromIterable(sources).concat
    // $COVERAGE-ON$
  }

  /** DEPRECATED — please switch to the
    * [[Observable!.flattenDelayErrors flattenDelayErrors]] method.
    *
    * This deprecation was made because there's no point in having this
    * function described both as a method and as a companion object function.
    * In general in API design we either have both for all functions, or
    * we have to choose.
    *
    * Switch to: `Observable(list).flattenDelayErrors`
    */
  @deprecated("Switch to Observable(list).flattenDelayErrors", "3.0.0")
  def flattenDelayError[A](sources: Observable[A]*): Observable[A] = {
    // $COVERAGE-OFF$
    Observable.fromIterable(sources).concatDelayErrors
    // $COVERAGE-ON$
  }

  /** DEPRECATED — please switch to the [[Observable!.merge merge]] method.
    *
    * This deprecation was made because there's no point in having this
    * function described both as a method and as a companion object function.
    * In general in API design we either have both for all functions, or
    * we have to choose.
    *
    * Switch to: `Observable(list).merge`
    */
  @deprecated("Switch to Observable(list).merge", "3.0.0")
  def merge[A](sources: Observable[A]*)(implicit os: OverflowStrategy[A] = OverflowStrategy.Default): Observable[A] = {
    // $COVERAGE-OFF$
    Observable.fromIterable(sources).mergeMap(identity)(os)
    // $COVERAGE-ON$
  }

  /** DEPRECATED — please switch to the [[Observable!.merge merge]] method.
    *
    * This deprecation was made because there's no point in having this
    * function described both as a method and as a companion object function.
    * In general in API design we either have both for all functions, or
    * we have to choose.
    *
    * Switch to: `Observable(list).merge`
    */
  @deprecated("Switch to Observable(list).merge", "3.0.0")
  def mergeDelayError[A](sources: Observable[A]*)(
    implicit os: OverflowStrategy[A] = OverflowStrategy.Default
  ): Observable[A] = {
    // $COVERAGE-OFF$
    Observable.fromIterable(sources).mergeMapDelayErrors(identity)(os)
    // $COVERAGE-ON$
  }

  /** DEPRECATED — please switch to the [[Observable!.concat concat]] method.
    *
    * This deprecation was made because there's no point in having this
    * function described both as a method and as a companion object function.
    * In general in API design we either have both for all functions, or
    * we have to choose.
    *
    * Switch to: `Observable(list).concat`
    */
  @deprecated("Switch to Observable(list).concat", "3.0.0")
  def concat[A](sources: Observable[A]*): Observable[A] = {
    // $COVERAGE-OFF$
    Observable.fromIterable(sources).concatMap[A](identity)
    // $COVERAGE-ON$
  }

  /** DEPRECATED — please switch to the [[Observable!.concatDelayErrors concatDelayErrors]] method.
    *
    * This deprecation was made because there's no point in having this
    * function described both as a method and as a companion object function.
    * In general in API design we either have both for all functions, or
    * we have to choose.
    *
    * Switch to: `Observable(list).concatDelayErrors`
    */
  @deprecated("Switch to Observable(list).concatDelayErrors", "3.0.0")
  def concatDelayError[A](sources: Observable[A]*): Observable[A] = {
    // $COVERAGE-OFF$
    Observable.fromIterable(sources).concatMapDelayErrors[A](identity)
    // $COVERAGE-ON$
  }

  /** DEPRECATED — please switch to the [[Observable!.switch switch]] method.
    *
    * This deprecation was made because there's no point in having this
    * function described both as a method and as a companion object function.
    * In general in API design we either have both for all functions, or
    * we have to choose.
    *
    * Switch to: `Observable(list).switch`
    */
  @deprecated("Switch to Observable(list).switch", "3.0.0")
  def switch[A](sources: Observable[A]*): Observable[A] = {
    // $COVERAGE-OFF$
    Observable.fromIterable(sources).switch
    // $COVERAGE-ON$
  }

  /**
    * DEPRECATED — switch to [[Observable.from]].
    */
  @deprecated("Switch to Observable.from", "3.0.0")
  def fromEval[A](fa: Eval[A]): Observable[A] = {
    // $COVERAGE-OFF$
    Observable.from(fa)
    // $COVERAGE-ON$
  }

  /**
    * DEPRECATED — switch to [[Observable.from]].
    */
  @deprecated("Switch to Observable.from", "3.0.0")
  def fromIO[A](fa: IO[A]): Observable[A] = {
    // $COVERAGE-OFF$
    Observable.from(fa)
    // $COVERAGE-ON$
  }
}
