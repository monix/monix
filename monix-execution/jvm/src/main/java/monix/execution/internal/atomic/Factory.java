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

import scala.MatchError;

public final class Factory {
  public static BoxedObject newBoxedObject(Object initial, BoxPaddingStrategy padding, boolean allowUnsafe, boolean allowJava8Intrinsics) {
    boolean useJava7Unsafe = allowUnsafe && UnsafeAccess.IS_AVAILABLE;
    boolean useJava8Unsafe = useJava7Unsafe && allowJava8Intrinsics && UnsafeAccess.HAS_JAVA8_INTRINSICS;

    switch (padding) {
      case NO_PADDING:
        if (useJava8Unsafe)
          return new NormalJava8BoxedObject(initial);
        else if (useJava7Unsafe)
          return new NormalJava7BoxedObject(initial);
        else
          return new NormalJavaXBoxedObject(initial);

      case LEFT_64:
        if (useJava8Unsafe)
          return new Left64Java8BoxedObject(initial);
        else if (useJava7Unsafe)
          return new Left64Java7BoxedObject(initial);
        else
          return new Left64JavaXBoxedObject(initial);

      case RIGHT_64:
        if (useJava8Unsafe)
          return new Right64Java8BoxedObject(initial);
        else if (useJava7Unsafe)
          return new Right64Java7BoxedObject(initial);
        else
          return new Right64JavaXBoxedObject(initial);

      case LEFT_RIGHT_128:
        if (useJava8Unsafe)
          return new LeftRight128Java8BoxedObject(initial);
        else if (useJava7Unsafe)
          return new LeftRight128Java7BoxedObject(initial);
        else
          return new LeftRight128JavaXBoxedObject(initial);

      case LEFT_128:
        if (useJava8Unsafe)
          return new Left128Java8BoxedObject(initial);
        else if (useJava7Unsafe)
          return new Left128Java7BoxedObject(initial);
        else
          return new Left128JavaXBoxedObject(initial);

      case RIGHT_128:
        if (useJava8Unsafe)
          return new Right128Java8BoxedObject(initial);
        else if (useJava7Unsafe)
          return new Right128Java7BoxedObject(initial);
        else
          return new Right128JavaXBoxedObject(initial);

      case LEFT_RIGHT_256:
        if (useJava8Unsafe)
          return new LeftRight256Java8BoxedObject(initial);
        else if (useJava7Unsafe)
          return new LeftRight256Java7BoxedObject(initial);
        else
          return new LeftRight256JavaXBoxedObject(initial);

      default:
        throw new MatchError(padding);
    }
  }

  public static BoxedInt newBoxedInt(int initial, BoxPaddingStrategy padding, boolean allowUnsafe, boolean allowJava8Intrinsics) {
    boolean useJava7Unsafe = allowUnsafe && UnsafeAccess.IS_AVAILABLE;
    boolean useJava8Unsafe = useJava7Unsafe && allowJava8Intrinsics && UnsafeAccess.HAS_JAVA8_INTRINSICS;

    switch (padding) {
      case NO_PADDING:
        if (useJava8Unsafe)
          return new NormalJava8BoxedInt(initial);
        else if (useJava7Unsafe)
          return new NormalJava7BoxedInt(initial);
        else
          return new NormalJavaXBoxedInt(initial);

      case LEFT_64:
        if (useJava8Unsafe)
          return new Left64Java8BoxedInt(initial);
        else if (useJava7Unsafe)
          return new Left64Java7BoxedInt(initial);
        else
          return new Left64JavaXBoxedInt(initial);

      case RIGHT_64:
        if (useJava8Unsafe)
          return new Right64Java8BoxedInt(initial);
        else if (useJava7Unsafe)
          return new Right64Java7BoxedInt(initial);
        else
          return new Right64JavaXBoxedInt(initial);

      case LEFT_RIGHT_128:
        if (useJava8Unsafe)
          return new LeftRight128Java8BoxedInt(initial);
        else if (useJava7Unsafe)
          return new LeftRight128Java7BoxedInt(initial);
        else
          return new LeftRight128JavaXBoxedInt(initial);

      case LEFT_128:
        if (useJava8Unsafe)
          return new Left128Java8BoxedInt(initial);
        else if (useJava7Unsafe)
          return new Left128Java7BoxedInt(initial);
        else
          return new Left128JavaXBoxedInt(initial);

      case RIGHT_128:
        if (useJava8Unsafe)
          return new Right128Java8BoxedInt(initial);
        else if (useJava7Unsafe)
          return new Right128Java7BoxedInt(initial);
        else
          return new Right128JavaXBoxedInt(initial);

      case LEFT_RIGHT_256:
        if (useJava8Unsafe)
          return new LeftRight256Java8BoxedInt(initial);
        else if (useJava7Unsafe)
          return new LeftRight256Java7BoxedInt(initial);
        else
          return new LeftRight256JavaXBoxedInt(initial);

      default:
        throw new MatchError(padding);
    }
  }

  public static BoxedLong newBoxedLong(long initial, BoxPaddingStrategy padding, boolean allowUnsafe, boolean allowJava8Intrinsics) {
    boolean useJava7Unsafe = allowUnsafe && UnsafeAccess.IS_AVAILABLE;
    boolean useJava8Unsafe = useJava7Unsafe && allowJava8Intrinsics && UnsafeAccess.HAS_JAVA8_INTRINSICS;

    switch (padding) {
      case NO_PADDING:
        if (useJava8Unsafe)
          return new NormalJava8BoxedLong(initial);
        else if (useJava7Unsafe)
          return new NormalJava7BoxedLong(initial);
        else
          return new NormalJavaXBoxedLong(initial);

      case LEFT_64:
        if (useJava8Unsafe)
          return new Left64Java8BoxedLong(initial);
        else if (useJava7Unsafe)
          return new Left64Java7BoxedLong(initial);
        else
          return new Left64JavaXBoxedLong(initial);

      case RIGHT_64:
        if (useJava8Unsafe)
          return new Right64Java8BoxedLong(initial);
        else if (useJava7Unsafe)
          return new Right64Java7BoxedLong(initial);
        else
          return new Right64JavaXBoxedLong(initial);

      case LEFT_RIGHT_128:
        if (useJava8Unsafe)
          return new LeftRight128Java8BoxedLong(initial);
        else if (useJava7Unsafe)
          return new LeftRight128Java7BoxedLong(initial);
        else
          return new LeftRight128JavaXBoxedLong(initial);

      case LEFT_128:
        if (useJava8Unsafe)
          return new Left128Java8BoxedLong(initial);
        else if (useJava7Unsafe)
          return new Left128Java7BoxedLong(initial);
        else
          return new Left128JavaXBoxedLong(initial);

      case RIGHT_128:
        if (useJava8Unsafe)
          return new Right128Java8BoxedLong(initial);
        else if (useJava7Unsafe)
          return new Right128Java7BoxedLong(initial);
        else
          return new Right128JavaXBoxedLong(initial);

      case LEFT_RIGHT_256:
        if (useJava8Unsafe)
          return new LeftRight256Java8BoxedLong(initial);
        else if (useJava7Unsafe)
          return new LeftRight256Java7BoxedLong(initial);
        else
          return new LeftRight256JavaXBoxedLong(initial);

      default:
        throw new MatchError(padding);
    }
  }
}
