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

/** Base package for the Monix library.
  *
  * The core is being split in the following sub-packages:
  *
  *  - '''[[monix.execution]]''' exposes lower level primitives for dealing
  *    with asynchronous execution, corresponding to the `monix-execution`
  *    sub-project
  *  - '''[[monix.eval]]''' is for dealing with evaluation of results,
  *    thus exposing [[monix.eval.Task Task]] and [[monix.eval.Coeval Coeval]],
  *    corresponding to the `monix-eval` sub-project
  *  - '''[[monix.reactive]]''' exposes the [[monix.reactive.Observable Observable]]
  *    pattern, corresponding to the `monix-reactive` sub-project
  *  - '''[[monix.tail]]''' exposes [[monix.tail.Iterant Iterant]]
  *    the reasonable, pull based, streaming abstraction
  */
package object monix {}
