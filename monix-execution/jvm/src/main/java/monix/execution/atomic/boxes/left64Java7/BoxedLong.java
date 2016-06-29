/*
 * Copyright (c) 2016 by its authors. Some rights reserved.
 * See the project homepage at: https://sincron.org
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

package monix.execution.atomic.boxes.left64Java7;

import monix.execution.atomic.boxes.common.LeftPadding48;
import monix.execution.misc.UnsafeAccess;
import java.lang.reflect.Field;

public final class BoxedLong extends LeftPadding48
        implements monix.execution.atomic.boxes.BoxedLong {

    public volatile long value;
    public static final long OFFSET;
    static {
        try {
            Field field = BoxedLong.class.getDeclaredField("value");
            OFFSET = UnsafeAccess.UNSAFE.objectFieldOffset(field);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public BoxedLong(long initialValue) {
        this.value = initialValue;
    }

    public long volatileGet() {
        return value;
    }

    public void volatileSet(long update) {
        value = update;
    }

    public void lazySet(long update) {
        UnsafeAccess.UNSAFE.putOrderedLong(this, OFFSET, update);
    }

    public boolean compareAndSet(long current, long update) {
        return UnsafeAccess.UNSAFE.compareAndSwapLong(this, OFFSET, current, update);
    }

    public long getAndSet(long update) {
        long current = value;
        while (!UnsafeAccess.UNSAFE.compareAndSwapLong(this, OFFSET, current, update))
            current = value;
        return current;
    }
}