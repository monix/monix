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

abstract class LeftPadding120 {
    public volatile long p01, p02, p03, p04, p05, p06, p07, p08 = 7;
    public volatile long p09, p10, p11, p12, p13, p14, p15 = 8;
    public long sum() {
        return p01 + p02 + p03 + p04 + p05 + p06 + p07 + p08 +
               p09 + p10 + p11 + p12 + p13 + p14 + p15;
    }
}