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

package monix.reactive.observers.buffers;

import monix.execution.Ack;
import scala.concurrent.Future;

abstract class CommonBufferPad0 {
  volatile long p00, p01, p02, p03, p04, p05, p06, p07;
  volatile long q00, q01, q02, q03, q04, q05, q06, q07;
}

abstract class CommonBufferUpstreamComplete extends CommonBufferPad0 {
  // To be modified only in onError / onComplete
  protected volatile boolean upstreamIsComplete = false;
}

abstract class CommonBufferPad1 extends CommonBufferUpstreamComplete {
  volatile long p10, p11, p12, p13, p14, p15, p16, p17;
  volatile long q10, q11, q12, q13, q14, q15, q16, q17;
}

abstract class CommonBufferDownstreamComplete extends CommonBufferPad1 {
  // To be modified only by consumer
  protected volatile boolean downstreamIsComplete = false;
}

abstract class CommonBufferPad2 extends CommonBufferDownstreamComplete {
  volatile long p20, p21, p22, p23, p24, p25, p26, p27;
  volatile long q20, q21, q22, q23, q24, q25, q26, q27;
}

abstract class CommonBufferErrorThrown extends CommonBufferPad2 {
  // To be modified only in onError, before upstreamIsComplete
  protected Throwable errorThrown = null;
}

abstract class CommonBufferPad3 extends CommonBufferErrorThrown {
  volatile long p30, p31, p32, p33, p34, p35, p36, p37;
  volatile long q30, q31, q32, q33, q34, q35, q36, q37;
}

abstract class CommonBufferLastAck extends CommonBufferPad3 {
  /**
   * Value used in order to apply back-pressure in the run-loop.
   * It stores the last `Future[Ack]` value whenever the loop
   * ends due to not having any more events to process.
   *
   * NOTE: value isn't synchronized, so to ensure its visibility
   * it needs to be stored before `itemsToPush` gets decremented
   * in the consumer loop and it needs to be read after the
   * producer increments `itemsToPush`.
   */
  protected Future<Ack> lastIterationAck;
}

/**
 * Common (protected) members between several `BufferedSubscriber`
 * implementations, with cache-line padding applied to avoid the
 * false-sharing problem.
 */
abstract class CommonBufferMembers extends CommonBufferLastAck {
  volatile long p40, p41, p42, p43, p44, p45, p46, p47;
  volatile long q40, q41, q42, q43, q44, q45, q46, q47;
}