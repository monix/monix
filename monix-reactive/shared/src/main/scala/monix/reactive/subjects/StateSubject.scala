/*
 * Copyright (c) 2014-2017 by The Tonix Project Developers.
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

package monix.reactive.subjects

import monix.reactive.observers._

/** `StateSubject` is a `Subject` that processes all values it observes as
  * transformations to a value that is - in turn - observed by others.
  *
  * @see `Subject`
  *
  * Example:
  *
  *     sealed trait StackTransform
  *
  *     case class  Push[T](x: T) extends StackTransform
  *     case object Pop           extends StackTransform
  *
  *     val stack = StateSubject[StackTransform, List[Int]](List.empty) {
  *       case (xs, Push(x: Int)) => x :: xs
  *       case (xs, Pop)          => xs drop 1
  *     }
  *
  *     val watcher = stack foreach (println _)
  *
  *     stack onNext Push(1)
  *     stack onNext Push(2)
  *     stack onNext Push(3)
  *     stack onNext Pop
  *     stack onNext Pop
  *
  * The above should print out:
  *
  *     List()
  *     List(1)
  *     List(2, 1)
  *     List(3, 2, 1)
  *     List(2, 1)
  *     List(1)
  */
final class StateSubject[T, A](initial: A, pf: PartialFunction[(A, T), A]) extends Subject[T, A] {
  val observer = PublishSubject[T]()

  // adhere to the observer contract
  def onNext(m: T) = observer.onNext(m)
  def onComplete = observer.onComplete
  def onError(ex: Throwable) = observer.onError(ex)

  // adhere to Subject trait
  def size = observer.size

  // however, ignore the observable side of our subject, and use this instead...
  val observable = initial +: observer.scan(initial) {
    (a, m) => if (pf.isDefinedAt((a, m))) pf((a, m)) else a // maybe onError here?
  }

  // subscribe to the transformed result
  def unsafeSubscribeFn(s: Subscriber[A]) = observable.subscribe(s)
}

object StateSubject {
  import scala.language.implicitConversions
  
  /** Create a new `StateSubject`.
    */
  def apply[T, A](initial: A)(pf: PartialFunction[(A, T), A]) = new StateSubject(initial, pf)
}
