/*
 * Copyright (c) 2014-2017 by The Monix Project Developers.
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

/** `ActorSubject` is a `Subject` that processes messages sent via `onNext` to
  * update a value that is observed.
  *
  * @see `Subject`
  *
  * Example:
  *
  *     sealed trait StackMessage
  *
  *     case class  Push[T](x: T) extends StackMessage
  *     case object Pop           extends StackMessage
  *
  *     val stack = ActorSubject[StackMessage, List[Int]](List.empty) {
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
final class ActorSubject[M, A](initial: A, pf: PartialFunction[(A, M), A]) extends Observer[M] {
  val observer = PublishSubject[M]()

  // adhere to the observer contract
  def onNext(m: M) = observer.onNext(m)
  def onComplete = observer.onComplete
  def onError(ex: Throwable) = observer.onError(ex)

  // however, ignore the observable side of our subject, and use this instead...
  val observable = initial +: observer.scan(initial) {
    (a, m) => if (pf isDefinedAt (a, m)) pf((a, m)) else a // maybe onError here?
  }
}

object ActorSubject {
  import scala.language.implicitConversions
  import scala.language.higherKinds
  
  /** Create a new `ActorSubject`.
    */
  def apply[M, A](initial: A)(pf: PartialFunction[(A, M), A]) = new ActorSubject(initial, pf)

  /** Implicitly coerces an `ActorSubject` to an `Observable`.
    */
  implicit def toObservable[M, A](subject: ActorSubject[M, A]) = subject.observable
}
