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

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

/**
 * INTERNAL API — used in the implementation of
 * `monix.execution.atomic.Atomic`.
 *
 * Being internal it can always change between minor versions,
 * providing no backwards compatibility guarantees and is only public
 * because Java does not provide the capability of marking classes as
 * "internal" to a package and all its sub-packages.
 */
final class Right64JavaXBoxedInt extends Right64JavaXBoxedIntImpl {
  public volatile long p1, p2, p3, p4, p5, p6, p7 = 7;
  public long sum() { return p1 + p2 + p3 + p4 + p5 + p6 + p7; }

  Right64JavaXBoxedInt(int initialValue) {
    super(initialValue);
  }
}

/**
 * INTERNAL API — used in the implementation of
 * `monix.execution.atomic.Atomic`.
 *
 * Being internal it can always change between minor versions,
 * providing no backwards compatibility guarantees and is only public
 * because Java does not provide the capability of marking classes as
 * "internal" to a package and all its sub-packages.
 */
abstract class Right64JavaXBoxedIntImpl implements BoxedInt {
  public volatile int value;

  private static final AtomicIntegerFieldUpdater<Right64JavaXBoxedIntImpl> UPDATER =
    AtomicIntegerFieldUpdater.newUpdater(Right64JavaXBoxedIntImpl.class, "value");

  Right64JavaXBoxedIntImpl(int initialValue) {
    this.value = initialValue;
  }

  public int volatileGet() {
    return value;
  }

  public void volatileSet(int update) {
    value = update;
  }

  public void lazySet(int update) {
    UPDATER.lazySet(this, update);
  }

  public boolean compareAndSet(int current, int update) {
    return UPDATER.compareAndSet(this, current, update);
  }

  public int getAndSet(int update) {
    return UPDATER.getAndSet(this, update);
  }

  public int getAndAdd(int delta) {
    return UPDATER.getAndAdd(this, delta);
  }
}