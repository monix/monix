/*
 * Copyright (c) 2014-2016 by its authors. Some rights reserved.
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
import monix.execution.Ack$;
import monix.execution.atomic.AtomicInt;
import monix.execution.atomic.PaddingStrategy;
import org.jctools.queues.MessagePassingQueue;
import scala.concurrent.Future;

abstract class CommonBufferPad0 {
  volatile long p00, p01, p02, p03, p04, p05, p06, p07;
}

abstract class CommonBufferUpstreamComplete extends CommonBufferPad0 {
  // To be modified only in onError / onComplete
  protected volatile boolean upstreamIsComplete = false;
}

abstract class CommonBufferPad1 extends CommonBufferUpstreamComplete {
  volatile long p10, p11, p12, p13, p14, p15, p16, p17;
}

abstract class CommonBufferDownstreamComplete extends CommonBufferPad1 {
  // To be modified only by consumer
  protected volatile boolean downstreamIsComplete = false;
}

abstract class CommonBufferPad2 extends CommonBufferDownstreamComplete {
  volatile long p20, p21, p22, p23, p24, p25, p26, p27;
}

abstract class CommonBufferErrorThrown extends CommonBufferPad2 {
  // To be modified only in onError, before upstreamIsComplete
  protected Throwable errorThrown = null;
}

abstract class CommonBufferPad3 extends CommonBufferErrorThrown {
  volatile long p30, p31, p32, p33, p34, p35, p36, p37;
}

abstract class CommonBufferItemsPushed extends CommonBufferPad3 {
  /*
   * Used to detect whether there are any consumer run-loops
   * active and start new run-loops when needed.
   */
  protected AtomicInt itemsToPush =
    AtomicInt.withPadding(0, PaddingStrategy.NoPadding$.MODULE$);
}

abstract class CommonBufferPad4 extends CommonBufferItemsPushed {
  volatile long p40, p41, p42, p43, p44, p45, p46, p47;
}

abstract class CommonBufferLastAck extends CommonBufferPad4 {
  /*
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

abstract class CommonBufferPad5 extends CommonBufferLastAck {
  volatile long p50, p51, p52, p53, p54, p55, p56, p57;
}

abstract class CommonBufferQueue<A> extends CommonBufferPad5 {
  protected MessagePassingQueue<A> queue;

  protected CommonBufferQueue(MessagePassingQueue<A> ref) {
    this.queue = ref;
  }
}

