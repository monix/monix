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

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

final class Left128JavaXBoxedInt extends LeftPadding120 implements BoxedInt {
  public volatile int value;

  private static final AtomicIntegerFieldUpdater<Left128JavaXBoxedInt> UPDATER =
    AtomicIntegerFieldUpdater.newUpdater(Left128JavaXBoxedInt.class, "value");

  Left128JavaXBoxedInt(int initialValue) {
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