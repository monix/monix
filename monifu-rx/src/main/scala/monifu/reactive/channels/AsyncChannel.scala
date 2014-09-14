/*
 * Copyright (c) 2014 by its authors. Some rights reserved. 
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
 
package monifu.reactive.channels

import monifu.reactive.BufferPolicy
import monifu.reactive.BufferPolicy.Unbounded
import monifu.reactive.subjects.AsyncSubject
import scala.concurrent.ExecutionContext

/**
 * Represents a [[monifu.reactive.Channel Channel]] that uses an underlying 
 * [[monifu.reactive.subjects.AsyncSubject AsyncSubject]].
 */
final class AsyncChannel[T] private (policy: BufferPolicy, ec: ExecutionContext)
  extends SubjectChannel(AsyncSubject[T]()(ec), policy)(ec)

object AsyncChannel {
  /**
   * Builds a [[monifu.reactive.Channel Channel]] that uses an underlying
   * [[monifu.reactive.subjects.AsyncSubject AsyncSubject]].
   */
  def apply[T](bufferPolicy: BufferPolicy = Unbounded)
      (implicit ec: ExecutionContext): AsyncChannel[T] = {
    new AsyncChannel[T](bufferPolicy, ec)
  }
}