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

package monix.execution.atomic.internal;

import java.lang.invoke.VarHandle;

interface VarHandleBoxedLong extends BoxedLong {
  VarHandle valueHandle();

  @Override
  default long volatileGet() {
    return (long) valueHandle().getVolatile(this);
  }

  @Override
  default void volatileSet(long update) {
    valueHandle().setVolatile(this, update);
  }

  @Override
  default void lazySet(long update) {
    valueHandle().setRelease(this, update);
  }

  @Override
  default boolean compareAndSet(long current, long update) {
    return valueHandle().compareAndSet(this, current, update);
  }

  @Override
  default long getAndSet(long update) {
    return (long) valueHandle().getAndSet(this, update);
  }

  @Override
  default long getAndAdd(long delta) {
    return (long) valueHandle().getAndAdd(this, delta);
  }
}
