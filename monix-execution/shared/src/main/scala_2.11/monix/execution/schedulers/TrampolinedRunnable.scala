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

package monix.execution.schedulers

import scala.concurrent.OnCompleteRunnable

/** A marker for callbacks that can be batched and executed
  * locally (on the current thread) by means of a trampoline
  * (if the execution context / scheduler allows it).
  *
  * Idea was taken from the `scala.concurrent.Future`
  * implementation. Credit should be given where due.
  *
  * DO NOT use unless you know what you're doing.
  */
trait TrampolinedRunnable extends Runnable with OnCompleteRunnable
