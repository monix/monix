/*
 * Copyright (c) 2014-2022 Monix Contributors.
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

package monix.execution.atomic
package internal

/**
  * 
  * @define atomicGetDesc Get the current value persisted by this atomic
  *  reference. 
  * 
  *  This is a "volatile read", with all visibility guarantees that come
  *  from that.
  * 
  * @define atomicSetDesc Stores the given value in the atomic reference.
  * 
  *  This is a "volatile write", with all visibility guarantees that come
  *  with it. For garbage-collecting purposes, but without the strong
  *  visibility guarantees, you might want to use [[lazySet]].
  * 
  * @define atomicGetAndSetDesc Sets the given value and returns
  *  the value visible just prior to the update.
  * 
  *  This is an atomic operation that's equivalent to:
  *  {{{
  *   import monix.execution.atomic._
  *   import scala.annotation.tailrec
  * 
  *   &#x40;tailrec 
  *   def getAndSet[A](ref: Atomic[A], update: A): A = {
  *    val current = ref.get()
  *    if (!compareAndSet(current, update))
  *      getAndSet(ref, update)
  *    else
  *      current
  *   }
  *  }}}
  * 
  *  NOTE — on top of the JVM this operation is a platform intrinsic,
  *  meaning that it's more efficient than a `compareAndSet`-driven loop.
  *  Therefore it isn't just a shortcut. If you can describe your logic
  *  in terms of `getAndSet`, instead of `compareAndSet`, then you should,
  *  but you'll also find this to be more challenging 😉
  * 
  * @define atomicGetAndSetParam the value that's going to be stored in 
  *  the atomic reference once the transaction succeeds.
  * 
  * @define atomicGetAndSetReturn the old value that was visible in the
  *  atomic reference, prior to the succeeding update.
  * 
  * @define atomicLazySetDesc Eventually sets to the given value.
  * 
  *  Equivalent with [[set]], but on top of the JVM this operation isn't a
  *  volatile write, and thus has more relaxed visibility guarantees. For
  *  nullifying values, in order to allow garbage collection, this method is
  *  more efficient and should be prefered. But only if you know what you're
  *  doing, otherwise just use normal [[set]].
  * 
  * @define compareAndSetDesc Does a compare-and-set operation on the 
  *  current value. For more info, checkout the related
  *  [[https://en.wikipedia.org/wiki/Compare-and-swap Compare-and-swap Wikipedia page]].
  * 
  *  It's an atomic, worry-free, but low-level operation. Use only if
  *  you know what you're doing.
  * 
  *  Sample: {{{
  *   import monix.execution.atomic._
  *   import scala.collection.immutable.Queue
  * 
  *   class ConcurrentQueue[A] private (ref: Atomic[Queue[A]]) {
  *     def enqueue(value: A): Unit = {
  *       val current = ref.get()
  *       val update = current.enqueue(value)
  *       if (!ref.compareAndSet(current, update))
  *         enqueue(value) // transaction failed, retry!
  *     }
  * 
  *     def dequeue(): Option[A] = {
  *       val current = ref.get()
  *       if (current.isEmpty)
  *         None
  *       else {
  *         val (elem, update) = current.dequeue
  *         if (!ref.compareAndSet(current, update))
  *           dequeue() // transaction failed, retry!
  *         else
  *           Some(elem)
  *       }
  *     }
  *   }
  *  }}}
  * 
  * @define atomicBestPractices BEST PRACTICES: 
  * 
  *  (1) state stored in this atomic reference MUST BE immutable, or 
  *  treated as such. Storing mutable data in an atomic reference
  *  doesn't work — unless you understand Java's memory model, and if
  *  you have to wonder, then you don't.
  *
  *  (2) in `compareAndSet`-driven loops, transformation/update functions
  *  get repeated, so those update functions MUST BE pure. If you have
  *  to trigger side-effects, then trigger them only AFTER the update
  *  has succeeded, not before or during the update. Failure to do so
  *  risks repeating those side-effects more than once. Working with
  *  an atomic reference is very unlike working with plain locks /
  *  / mutexes / `synchronize` blocks.
  * 
  *  (3) the operation is not based on Java's `Object#equals` or Scala's `==`.
  *  For primitives, value equality is going to be used, but reference
  *  equality is used for anything else, so ensure that the `expect` reference
  *  is the same as the one received via `get()`.
  * 
  * @define atomicCASExpectParam is the value you expect to be persisted when
  *  the operation happens.
  * 
  * @define atomicCASUpdateParam will be the new value, should the
  *  (reference) equality check for `expect` succeeds.
  * 
  * @define atomicCASReturn either `true` in case the operation succeeded or
  *  `false` otherwise. If this returns `false`, it means that another 
  *  concurrent transaction won the race and updated the atomic reference 
  *  first, so you probably need to repeat the transaction.
  *
  * @define atomicTransformExtractDesc Updates this atomic reference in a
  *  transaction, modeled with  a `compareAndSet`-driven loop.
  *  This "transformation" specifies a custom extractor.
  * 
  *  Sample: {{{
  *    import monix.execution.atomic._
  *    import scala.collection.immutable.Queue
  *         
  *    final class ConcurrentQueue[A] private (state: AtomicRef[Queue[A]]) {
  *      def enqueue(value: A): Unit = 
  *        state.transform(_.enqueue(value))
  *  
  *      def dequeue(): Option[A] =
  *        state.transformAndExtract { queue =>
  *          if (queue.isEmpty)
  *            (None, queue)
  *          else
  *            (Some(queue.dequeue), queue)
  *        }
  *    }
  *  }}}
  * 
  *  @see [[getAndTransform]] and [[transformAndGet]].
  *        
  * @define atomicTransformBestPractices Best practices ...
  *  
  *  (1) This method will loop until the transaction succeeds in replacing
  *  the current value. If the atomic reference is contended by multiple
  *  actors trying to update the underlying value, then the transaction
  *  gets repeated until it succeeds. Therefore the given function 
  *  MUST BE pure (i.e. MUST NOT do any I/O).
  *  
  *  (2) State stored in this atomic reference must be an immutable
  *  data-structure, or at least an effectively mutable one. If you
  *  mutate the state stored in this atomic reference, working with it
  *  is useless, as it cannot provide any transactional or visibility
  *  guarantees. The only updates that you should allow are the updates
  *  managed via this atomic reference.
  *         
  * @define atomicTransformExtractParamF is a function that receives the 
  *  current value as input and
  *  returns a tuple, representing (1) what the transaction yields 
  *  after it succeeds and (2) what the update is. Note that this
  *  function mirrors `S => (A, S)`, which is the signature of the
  *  "state monad" 😉
  * 
  * @define atomicTransformExtractReturn whatever was extracted by your 
  *  function, once the operation succeeds.
  * 
  * @define atomicTransformAndGetDesc Updates this atomic reference in a
  *  transaction, modeled with  a `compareAndSet`-driven loop.
  *  Returns the updated value.
  * 
  *  Sample: 
  *  {{{
  *    import monix.execution.atomic._
  *         
  *    final class CountDown private (state: AtomicLong) {
  *      def next(): Boolean = {
  *        val n = state.transformAndGet(n => math.max(n - 1, 0))
  *        n > 0
  *      }
  *    }
  *  }}}
  * 
  *  @see [[getAndTransform]] and [[transformAndExtract]].
  * 
  * @define atomicTransformParam is a function that receives the current 
  *  value as input and returns the `update` which is the new value that 
  *  should be persisted. WARN — function could be called multiple times 
  *  and should be pure!
  * 
  * @define atomicTransformAndGetReturn returns the updated value, once
  *  the transaction succeeds.
  *
  * @define atomicGetAndTransformDesc Updates this atomic reference in a
  *  transaction, modeled with a `compareAndSet`-driven loop.
  *  Returns the value visible prior to the update.
  * 
  *  Sample: 
  *  {{{
  *    import monix.execution.atomic._
  *         
  *    final class CountDown private (state: AtomicLong, n: Int) {
  *      def next(): Boolean = {
  *        val i = state.getAndTransform(i => math.min(n, i + 1))
  *        i < n
  *      }
  *    }
  *  }}}
  * 
  *  @see [[transformAndGet]] and [[transformAndExtract]].
  * 
  * @define atomicGetAndTransformReturn the old value, that was visible
  *  just prior to when the update happened.
  * 
  * @define atomicTransformDesc Updates this atomic reference in a 
  *  transaction, modeled with a `compareAndSet`-driven loop.
  * 
  *  Similar with [[getAndTransform]], [[transformAndGet]], or 
  *  [[transformAndExtract]], except this version doesn't return anything.
  *  To use whenever we only care about the side-effect.
  * 
  *  Sample: 
  *  {{{
  *    import monix.execution.atomic._
  *         
  *    final class Counter private (state: AtomicLong) {
  *      def mark(i: Int = 1): Unit = state.transform(_ + i)
  *      def get(): Long = state.get()   
  *    }
  *  }}}
  */
private[atomic] trait AtomicDocs { this: Atomic[_] => }
