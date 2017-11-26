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

import monix.execution.Ack.Continue
import monix.execution.exceptions.DummyException
import monix.reactive.Observer

object AsyncSubjectSuite extends BaseSubjectSuite {
  sealed trait Message

  final case class  Push[T](x: T) extends Message
  final case object Pop           extends Message

  test("accept messages and update state value") { implicit s =>
    var stack = ???

    val subject = ActorSubject(List.empty[Int]) {
      case (xs, Push(x: Int)) => x :: xs
      case (xs, Pop)          => xs drop 1
    }

    val observer = subject foreach (stack = _)

    subject onNext Push(1)
    subject onNext Push(2)
    subject onNext Push(3)

    subject onNext Pop
    subject onNext Pop
    subject onNext Pop

    s.tick
    assertEquals(stack, List())
    s.tick
    assertEquals(stack, List(1))
    s.tick
    assertEquals(stack, List(2,1))
    s.tick
    assertEquals(stack, List(3,2,1))
    s.tick
    assertEquals(stack, List(2,1))
    s.tick
    assertEquals(stack, List(1))
    s.tick
    assertEquals(stack, List())

    subject.onComplete()
  }
}
