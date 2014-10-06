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
 
package monifu.reactive.subjects

import monifu.reactive.{ConnectableObservable, Subject}

/**
 * Represents a [[monifu.reactive.Subject Subject]] that waits for
 * the call to `connect()` before starting to emit elements to its subscriber(s).
 *
 * You can convert any `Subject` into a `ConnectableSubject` by means of
 * [[monifu.reactive.Subject.multicast Subject.multicast]].
 */
trait ConnectableSubject[-I,+O] extends ConnectableObservable[O] with Subject[I, O]
