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

package monix.execution.annotations

import scala.annotation.StaticAnnotation

/**
  * An annotation meant to warn users on unsafe functions.
  *
  * "Unsafe" can include behavior such as:
  *
  *  - breaking of referential transparency, see [[UnsafeBecauseImpure]]
  *  - blocking the underlying thread, see [[UnsafeBecauseBlocking]]
  *  - exposing a protocol of communication that has to be used
  *    as specified or otherwise the behavior can be undefined,
  *    see [[UnsafeProtocol]]
  */
class Unsafe(val reason: String) extends StaticAnnotation

/** An annotation meant to warn users on functions that are
  * breaking referential transparency.
  *
  * Such operations are unsafe in a context where purity is
  * expected. Note however that most data types defined in
  * `monix.execution` are impure.
  */
class UnsafeBecauseImpure extends Unsafe("impure")

/** An annotation meant to warn users on functions that are
  * triggering blocking operations.
  *
  * Blocking threads is unsafe because:
  *
  *  - the user has to be aware of the configuration of the underlying
  *    thread-pool, which should be preferably unbounded, or otherwise
  *    it can suffer from thread starvation
  *  - it's not supported on top of JavaScript
  *
  * Prefer to avoid blocking operations.
  */
class UnsafeBecauseBlocking extends Unsafe("blocking")

/** An annotation meant to warn users on functions that are
  * using an error prone protocol.
  *
  * An example of such a protocol is the one defined at
  * [[http://reactive-streams.org/ reactive-streams.org]],
  * being unsafe because its safe usage requires deep knowledge
  * of it, having lots of cases in which the compiler does not
  * and cannot help, leading to undefined behavior if not
  * careful.
  *
  * Only use such functions if familiar with the underlying
  * protocol.
  */
class UnsafeProtocol extends Unsafe("protocol")
