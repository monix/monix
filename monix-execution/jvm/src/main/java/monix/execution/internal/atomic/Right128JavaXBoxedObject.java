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

import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

final class Right128JavaXBoxedObject extends Right128JavaXBoxedObjectImpl {
  public volatile long p1, p2, p3, p4, p5, p6, p7, p8 = 7;
  public volatile long p9, p10, p11, p12, p13, p14, p15 = 7;
  public long sum() {
    return p1 + p2 + p3 + p4 + p5 + p6 + p7 + p8 +
      p9 + p10 + p11 + p12 + p13 + p14 + p15;
  }

  Right128JavaXBoxedObject(Object initialValue) {
    super(initialValue);
  }
}

abstract class Right128JavaXBoxedObjectImpl implements BoxedObject {
  public volatile Object value;

  private static final AtomicReferenceFieldUpdater<Right128JavaXBoxedObjectImpl, Object> UPDATER =
    AtomicReferenceFieldUpdater.newUpdater(Right128JavaXBoxedObjectImpl.class, Object.class, "value");

  Right128JavaXBoxedObjectImpl(Object initialValue) {
    this.value = initialValue;
  }

  public Object volatileGet() {
    return value;
  }

  public void volatileSet(Object update) {
    value = update;
  }

  public void lazySet(Object update) {
    UPDATER.lazySet(this, update);
  }

  public boolean compareAndSet(Object current, Object update) {
    return UPDATER.compareAndSet(this, current, update);
  }

  public Object getAndSet(Object update) {
    return UPDATER.getAndSet(this, update);
  }
}