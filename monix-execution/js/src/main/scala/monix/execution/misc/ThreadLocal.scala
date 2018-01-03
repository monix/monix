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

package monix.execution.misc

/** Cross-platform equivalent for `java.lang.ThreadLocal`,
  * for specifying thread-local variables.
  *
  * These variables differ from their normal counterparts in that each
  * thread that accesses one (via its [[ThreadLocal#get]] or
  * [[ThreadLocal#set]] method) has its own, independently initialized
  * copy of the variable.
  *
  * @param initial is the initial value that this thread-local
  *        reference returns on [[ThreadLocal#get reads]] in case
  *        the current thread hasn't [[ThreadLocal#set written]]
  *        any values yet.
  */
final class ThreadLocal[A] private (val initial: A) {
  private[this] var tl = initial

  /** Returns the value in the current thread's copy of this
    * thread-local variable. If the variable has no value for the
    * current thread, it is initialized with the
    * [[ThreadLocal.initial initial]] value specified in the
    * constructor.
    *
    * @return the current thread's value of this thread-local
    */
  def get(): A = tl

  /** Sets the current thread's copy of this thread-local variable
    * to the specified value.
    *
    * @param value the value to be stored in the current
    *        thread's copy of this thread-local.
    */
  def set(value: A): Unit = tl = value

  /** Removes the current thread's value for this thread-local
    * variable. If this thread-local variable is subsequently
    * [[ThreadLocal.get read]] by the current thread, its value will be
    * reinitialized by its [[ThreadLocal.initial initial]] value.
    */
  def reset(): Unit = tl = initial
}

object ThreadLocal {
  /** Builds a [[ThreadLocal]] reference initialized with `null`. */
  def apply[A <: AnyRef](): ThreadLocal[A] =
    new ThreadLocal[A](null.asInstanceOf[A])

  /** Builds a [[ThreadLocal]] reference.
    *
    * @param initial is the initial value that this thread-local
    *        reference returns on [[ThreadLocal.get reads]] in case
    *        the current thread hasn't [[ThreadLocal#set written]]
    *        any values yet.
    */
  def apply[A](initial: A): ThreadLocal[A] =
    new ThreadLocal[A](initial)
}