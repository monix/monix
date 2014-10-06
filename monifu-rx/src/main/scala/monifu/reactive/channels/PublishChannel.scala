/*
 * Copyright (c) 2014 by its authors. Some rights reserved.
 * See the project homepage at
 *
 *     http://www.monifu.org/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  	http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
 
package monifu.reactive.channels

import monifu.concurrent.Scheduler
import monifu.reactive.BufferPolicy
import monifu.reactive.BufferPolicy.Unbounded
import monifu.reactive.subjects.PublishSubject

/**
 * Represents a [[monifu.reactive.Channel Channel]] that uses an underlying
 * [[monifu.reactive.subjects.PublishSubject PublishSubject]].
 */
final class PublishChannel[T] private (policy: BufferPolicy, s: Scheduler)
  extends SubjectChannel(PublishSubject[T]()(s), policy)(s)

object PublishChannel {
  /**
   * Builds a [[monifu.reactive.Channel Channel]] that uses an underlying
   * [[monifu.reactive.subjects.PublishSubject PublishSubject]].
   */
  def apply[T](bufferPolicy: BufferPolicy = Unbounded)
      (implicit s: Scheduler): PublishChannel[T] = {
    new PublishChannel[T](bufferPolicy, s)
  }
}
