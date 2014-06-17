/*
 * Copyright (c) 2014 by its authors. Some rights reserved. 
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
 
package monifu.concurrent

/**
 * Represents a ThreadLocal, a concept that isn't useful on top of a Javascript runtime
 * (since in a JS runtime all variables are thread-local, since it's a single threaded
 * execution model), but having this is useful for cross-compilation purposes.
 */
final class ThreadLocal[T] private (initial: T) {
  private[this] var ref = initial

  def get: T = ref
  def apply(): T = ref
  def update(value: T): Unit = ref = value
  def `:=`(update: T): Unit = ref = update
  def set(update: T): Unit = ref = update
}

object ThreadLocal {
  def apply[T](initialValue: T): ThreadLocal[T] =
    new ThreadLocal(initialValue)
}