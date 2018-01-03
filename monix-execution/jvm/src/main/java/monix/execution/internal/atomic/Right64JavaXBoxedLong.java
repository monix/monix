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

package monix.execution.internal.atomic;

import java.util.concurrent.atomic.AtomicLongFieldUpdater;

final class Right64JavaXBoxedLong extends Right64JavaXBoxedLongImpl {
  public volatile long p1, p2, p3, p4, p5, p6, p7 = 7;
  public long sum() { return p1 + p2 + p3 + p4 + p5 + p6 + p7; }

  Right64JavaXBoxedLong(long initialValue) {
    super(initialValue);
  }
}

abstract class Right64JavaXBoxedLongImpl implements BoxedLong {
  public volatile long value;

  private static final AtomicLongFieldUpdater<Right64JavaXBoxedLongImpl> UPDATER =
    AtomicLongFieldUpdater.newUpdater(Right64JavaXBoxedLongImpl.class, "value");

  Right64JavaXBoxedLongImpl(long initialValue) {
    this.value = initialValue;
  }

  public long volatileGet() {
    return value;
  }

  public void volatileSet(long update) {
    value = update;
  }

  public void lazySet(long update) {
    UPDATER.lazySet(this, update);
  }

  public boolean compareAndSet(long current, long update) {
    return UPDATER.compareAndSet(this, current, update);
  }

  public long getAndSet(long update) {
    return UPDATER.getAndSet(this, update);
  }

  public long getAndAdd(long delta) {
    return UPDATER.getAndAdd(this, delta);
  }
}