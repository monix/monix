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

package monix.execution.atomic.boxes;

import monix.execution.misc.UnsafeAccess;
import scala.MatchError;

public final class Factory {
    public static BoxedObject newBoxedObject(Object initial, BoxPaddingStrategy padding) {
        switch (padding) {
            case NO_PADDING:
                if (UnsafeAccess.SUPPORTS_GET_AND_SET)
                    return new monix.execution.atomic.boxes.normalJava8.BoxedObject(initial);
                else
                    return new monix.execution.atomic.boxes.normalJava7.BoxedObject(initial);

            case LEFT_64:
                if (UnsafeAccess.SUPPORTS_GET_AND_SET)
                    return new monix.execution.atomic.boxes.left64Java8.BoxedObject(initial);
                else
                    return new monix.execution.atomic.boxes.left64Java7.BoxedObject(initial);

            case RIGHT_64:
                if (UnsafeAccess.SUPPORTS_GET_AND_SET)
                    return new monix.execution.atomic.boxes.right64Java8.BoxedObject(initial);
                else
                    return new monix.execution.atomic.boxes.right64Java7.BoxedObject(initial);

            case LEFT_RIGHT_128:
                if (UnsafeAccess.SUPPORTS_GET_AND_SET)
                    return new monix.execution.atomic.boxes.leftRight128Java8.BoxedObject(initial);
                else
                    return new monix.execution.atomic.boxes.leftRight128Java7.BoxedObject(initial);

            case LEFT_128:
                if (UnsafeAccess.SUPPORTS_GET_AND_SET)
                    return new monix.execution.atomic.boxes.left128Java8.BoxedObject(initial);
                else
                    return new monix.execution.atomic.boxes.left128Java7.BoxedObject(initial);

            case RIGHT_128:
                if (UnsafeAccess.SUPPORTS_GET_AND_SET)
                    return new monix.execution.atomic.boxes.right128Java8.BoxedObject(initial);
                else
                    return new monix.execution.atomic.boxes.right128Java7.BoxedObject(initial);

            case LEFT_RIGHT_256:
                if (UnsafeAccess.SUPPORTS_GET_AND_SET)
                    return new monix.execution.atomic.boxes.leftRight256Java8.BoxedObject(initial);
                else
                    return new monix.execution.atomic.boxes.leftRight256Java7.BoxedObject(initial);

            default:
                throw new MatchError(padding);
        }
    }

    public static BoxedInt newBoxedInt(int initial, BoxPaddingStrategy padding) {
        switch (padding) {
            case NO_PADDING:
                if (UnsafeAccess.SUPPORTS_GET_AND_SET)
                    return new monix.execution.atomic.boxes.normalJava8.BoxedInt(initial);
                else
                    return new monix.execution.atomic.boxes.normalJava7.BoxedInt(initial);

            case LEFT_64:
                if (UnsafeAccess.SUPPORTS_GET_AND_SET)
                    return new monix.execution.atomic.boxes.left64Java8.BoxedInt(initial);
                else
                    return new monix.execution.atomic.boxes.left64Java7.BoxedInt(initial);

            case RIGHT_64:
                if (UnsafeAccess.SUPPORTS_GET_AND_SET)
                    return new monix.execution.atomic.boxes.right64Java8.BoxedInt(initial);
                else
                    return new monix.execution.atomic.boxes.right64Java7.BoxedInt(initial);

            case LEFT_RIGHT_128:
                if (UnsafeAccess.SUPPORTS_GET_AND_SET)
                    return new monix.execution.atomic.boxes.leftRight128Java8.BoxedInt(initial);
                else
                    return new monix.execution.atomic.boxes.leftRight128Java7.BoxedInt(initial);

            case LEFT_128:
                if (UnsafeAccess.SUPPORTS_GET_AND_SET)
                    return new monix.execution.atomic.boxes.left128Java8.BoxedInt(initial);
                else
                    return new monix.execution.atomic.boxes.left128Java7.BoxedInt(initial);

            case RIGHT_128:
                if (UnsafeAccess.SUPPORTS_GET_AND_SET)
                    return new monix.execution.atomic.boxes.right128Java8.BoxedInt(initial);
                else
                    return new monix.execution.atomic.boxes.right128Java7.BoxedInt(initial);

            case LEFT_RIGHT_256:
                if (UnsafeAccess.SUPPORTS_GET_AND_SET)
                    return new monix.execution.atomic.boxes.leftRight256Java8.BoxedInt(initial);
                else
                    return new monix.execution.atomic.boxes.leftRight256Java7.BoxedInt(initial);

            default:
                throw new MatchError(padding);
        }
    }

    public static BoxedLong newBoxedLong(long initial, BoxPaddingStrategy padding) {
        switch (padding) {
            case NO_PADDING:
                if (UnsafeAccess.SUPPORTS_GET_AND_SET)
                    return new monix.execution.atomic.boxes.normalJava8.BoxedLong(initial);
                else
                    return new monix.execution.atomic.boxes.normalJava7.BoxedLong(initial);

            case LEFT_64:
                if (UnsafeAccess.SUPPORTS_GET_AND_SET)
                    return new monix.execution.atomic.boxes.left64Java8.BoxedLong(initial);
                else
                    return new monix.execution.atomic.boxes.left64Java7.BoxedLong(initial);

            case RIGHT_64:
                if (UnsafeAccess.SUPPORTS_GET_AND_SET)
                    return new monix.execution.atomic.boxes.right64Java8.BoxedLong(initial);
                else
                    return new monix.execution.atomic.boxes.right64Java7.BoxedLong(initial);

            case LEFT_RIGHT_128:
                if (UnsafeAccess.SUPPORTS_GET_AND_SET)
                    return new monix.execution.atomic.boxes.leftRight128Java8.BoxedLong(initial);
                else
                    return new monix.execution.atomic.boxes.leftRight128Java7.BoxedLong(initial);

            case LEFT_128:
                if (UnsafeAccess.SUPPORTS_GET_AND_SET)
                    return new monix.execution.atomic.boxes.left128Java8.BoxedLong(initial);
                else
                    return new monix.execution.atomic.boxes.left128Java7.BoxedLong(initial);

            case RIGHT_128:
                if (UnsafeAccess.SUPPORTS_GET_AND_SET)
                    return new monix.execution.atomic.boxes.right128Java8.BoxedLong(initial);
                else
                    return new monix.execution.atomic.boxes.right128Java7.BoxedLong(initial);

            case LEFT_RIGHT_256:
                if (UnsafeAccess.SUPPORTS_GET_AND_SET)
                    return new monix.execution.atomic.boxes.leftRight256Java8.BoxedLong(initial);
                else
                    return new monix.execution.atomic.boxes.leftRight256Java7.BoxedLong(initial);

            default:
                throw new MatchError(padding);
        }
    }
}
