# Atomic Reference

<img src="assets/monifu.png" align="right" />

Scala is awesome at handling concurrecy and parallelism, providing
high-level tools for handling it, however sometimes you need to go
lower level. Java's library provides all the multi-threading
primitives required, however the interfaces of these primitives
sometime leave something to be desired.

One such example are the atomic references provided in
[java.util.concurrent.atomic](http://docs.oracle.com/javase/6/docs/api/java/util/concurrent/atomic/package-summary.html)
package. This project is an attempt at improving these types for daily
usage.

### Providing a Common interface

So you have
[j.u.c.a.AtomicReference<V>](http://docs.oracle.com/javase/6/docs/api/java/util/concurrent/atomic/AtomicReference.html),
[j.u.c.a.AtomicInteger](http://docs.oracle.com/javase/6/docs/api/java/util/concurrent/atomic/AtomicInteger.html),
[j.u.c.a.AtomicLong](http://docs.oracle.com/javase/6/docs/api/java/util/concurrent/atomic/AtomicLong.html)
and
[j.u.c.a.AtomicBoolean](http://docs.oracle.com/javase/6/docs/api/java/util/concurrent/atomic/AtomicLong.html). 
The reason why `AtomicReference<V>` does not suffice is because
compare-and-set works with reference equality, not structural equality
like it happens with primitives. So you cannot simply box an integer
and use it safely, plus you've got the whole boxing/unboxing overhead.

One problem is that all of these classes do not share a common
interface and there's no reason for why they shouldn't. See the
[Atomic[T]](/monifu-core/src/shared/scala/monifu/concurrent/atomic/Atomic.scala), [AtomicNumber[T]](/monifu-core/src/shared/scala/monifu/concurrent/atomic/AtomicNumber.scala) and [BlockableAtomic[T]](/monifu-core/src/main/scala/monifu/concurrent/atomic/BlockableAtomic.scala) traits.

```scala
import monifu.concurrent.atomic.Atomic

val refInt: Atomic[Int] = Atomic(0)
val refLong: Atomic[Long] = Atomic(0L)
val refString: Atomic[String] = Atomic("hello")
```

### Working with Numbers

One really common use-case for atomic references are for numbers to
which you need to add or subtract. To this purpose
`j.u.c.a.AtomicInteger` and `j.u.c.a.AtomicLong` have an
`incrementAndGet` helper. However Ints and Longs aren't the only types
you normally need. How about `Float` and `Double` and `Short`? How about
`BigDecimal` and `BigInt`?

In Scala, thanks to the
[Numeric[T]](http://www.scala-lang.org/api/current/index.html#scala.math.Numeric)
type-class, we can do this:

```scala 
scala> import monifu.concurrent.atomic.Atomic

scala> val ref = Atomic(BigInt(1))

scala> ref.incrementAndGet()
res0: scala.math.BigInt = 2

scala> ref.addAndGet(BigInt("329084291234234"))
res1: scala.math.BigInt = 329084291234236

scala> val ref = Atomic("hello")
ref: monifu.concurrent.atomic.AtomicAny[String] = Atomic(hello)

scala> ref.incrementAndGet()
<console>:12: error: value incrementAndGet is not a member of monifu.concurrent.atomic.AtomicAny[String]
              ref.incrementAndGet
                  ^
```

### Support for Other Primitives (Float, Double, Short, Char, Byte)

Here's a common gotcha with Java's `AtomicReference<V>`:

```scala
scala> import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicReference

scala> val ref = new AtomicReference(0.0)
ref: java.util.concurrent.atomic.AtomicReference[Double] = 0.0

scala> ref.compareAndSet(0.0, 100.0)
res0: Boolean = false
```

Calling `compareAndSet` fails because when using `AtomicReference<V>`
the equality comparisson is done by reference and it doesn't work for
primitives because the process of
[Autoboxing/Unboxing](http://docs.oracle.com/javase/tutorial/java/data/autoboxing.html)
is involved. And then there's the efficiency issue. By using an
AtomicReference, you'll end up with extra boxing/unboxing going on.

`Float` can be stored inside an `AtomicInteger` by using Java's
`Float.floatToIntBits` and `Float.intBitstoFloat`. `Double` can be
stored inside an `AtomicLong` by using Java's
`Double.doubleToLongBits` and `Double.longBitsToDouble`. `Char`,
`Byte` and `Short` can be stored inside an `AtomicInteger` as well,
with special care to handle overflows correctly. All this is done to avoid boxing
for performance reasons.

```scala
scala> import monifu.concurrent.atomic.Atomic

scala> val ref = Atomic(0.0)
ref: monifu.concurrent.atomic.AtomicDouble = Atomic(0.0)

scala> ref.compareAndSet(0.0, 100.0)
res0: Boolean = true

scala> ref.incrementAndGet
res1: Double = 101.0

scala> val ref = Atomic('a')
ref: monifu.concurrent.atomic.AtomicChar = Atomic(a)

scala> ref.incrementAndGet
res2: Char = b

scala> ref.incrementAndGet
res3: Char = c
```

Even when boxing a number inside a generic `AtomicNumberAny` (that makes use of
the `Numeric[T]` type-class from Scala's standard library, using a plain `j.u.c.a.AtomicReference[T]` 
internally), the above gotcha doesn't happen, as the implementation of `AtomicNumberAny` is built
to encounter the autoboxing effects:

```scala
scala> val ref: Atomic[Double] = AtomicNumberAny(0.0)
ref: monifu.concurrent.atomic.Atomic[Double] = monifu.concurrent.atomic.AtomicNumberAny@7b221307

scala> ref.compareAndSet(0, 100)
res0: Boolean = true
```

### Common Pattern: Loops for Transforming the Value

`incrementAndGet` represents just one use-case of a simple and more
general pattern. To push items in a queue for example, one would
normally do something like this in Java:

```scala
import collection.immutable.Queue
import java.util.concurrent.atomic.AtomicReference

def pushElementAndGet[T <: AnyRef, U <: T](ref: AtomicReference[Queue[T]], elem: U): Queue[T] = {
  var continue = true
  var update = null
  
  while (continue) {
    var current: Queue[T] = ref.get()
    var update = current.enqueue(elem)
    continue = !ref.compareAndSet(current, update)
  }
  update  
}
```

This is such a common pattern. Taking a page from the wonderful
[ScalaSTM](http://nbronson.github.io/scala-stm/), with `Atomic` you
can simply do this:

```scala
scala> val ref = Atomic(Queue.empty[String])
ref: AtomicAny[immutable.Queue[String]] = Atomic(Queue())

scala> ref.transformAndGet(_.enqueue("hello"))
res: immutable.Queue[String] = Queue(hello)

scala> ref.transformAndGet(_.enqueue("world"))
res: immutable.Queue[String] = Queue(hello, world)

scala> ref.transformAndExtract(_.dequeue)
res: String = hello

scala> ref.transformAndExtract(_.dequeue)
res: String = world
```

Voilà, you now have a concurrent, thread-safe and non-blocking Queue. You can do this
for whatever persistent data-structure you want.

### Common-pattern: Block the thread until progress is possible

This line of code blocks the thread until the `compareAndSet` operation succeeds.
```scala
ref.waitForCompareAndSet("hello", "world")
```

You can also specify a timeout:
```scala
scala> import concurrent.duration._
import concurrent.duration._

scala> ref.waitForCompareAndSet("hello", "world", 1.second)
java.util.concurrent.TimeoutException
	at monifu.concurrent.atomic.BlockableAtomic$.timeoutCheck(BlockableAtomic.scala:156)
	at monifu.concurrent.atomic.BlockableAtomic$class.waitForCompareAndSet(BlockableAtomic.scala:56)
```

You can also block the thread waiting for a certain value:
```scala
ref.waitForValue("world")
```

And of course you can specify a timeout:
```scala
scala> ref.waitForValue("hello", 1.second)
java.util.concurrent.TimeoutException
	at monifu.concurrent.atomic.BlockableAtomic$.timeoutCheck(BlockableAtomic.scala:156)
```

You can block the thread waiting for a callback receiving the persisted value to become true:
```scala
ref.waitForCondition(_.contains("wor"))
```

And of course you can specify a timeout:
```scala
scala> ref.waitForCondition(1.second)(_.contains("hell"))
java.util.concurrent.TimeoutException
	at monifu.concurrent.atomic.BlockableAtomic$.timeoutCheck(BlockableAtomic.scala:156)
```

All these blocking calls are also interruptible, throwing an `InterruptedException` in case that happened.

## Scala.js support for targetting Javascript

These atomic references are also cross-compiled to [Scala.js](http://www.scala-js.org/) 
for targetting Javascript engines (in `monifu-core-js`), because:

- it's a useful way of boxing mutable variables, in case you need to box
- it's a building block for doing synchronization, so useful for code that you want cross-compiled
- because mutability doesn't take *time* into account and `compareAndSet` does, atomic references and
  `compareAndSet` in particular is also useful in a non-multi-threaded / asynchronous environment 

What isn't supported on top of Scala.js / Javascript:

- blocking methods aren't supported since the semantics aren't possible (fret not, the compiler will not
  let you use them since they are missing from `monifu-core-js`)
- cache padded versions, since they make no sense in Javascript

## Efficiency

Atomic references are low-level primitives for concurrency and because
of that any extra overhead is unacceptable. 

### Boxing / Unboxing 

Working with a common `Atomic[T]` interface implies
boxing/unboxing of primitives. This is why the constructor for atomic references always returns the most
specialized version, as to avoid boxing and unboxing:

```scala
scala> val ref = Atomic(1)
ref: monifu.concurrent.atomic.AtomicInt = AtomicInt(1)

scala> val ref = Atomic(1L)
ref: monifu.concurrent.atomic.AtomicLong = AtomicLong(1)

scala> val ref = Atomic(true)
ref: monifu.concurrent.atomic.AtomicBoolean = AtomicBoolean(true)

scala> val ref = Atomic("")
ref: monifu.concurrent.atomic.AtomicAny[String] = monifu.concurrent.atomic.AtomicAny@1b1ce484
```

Increments/decrements are done by going through the
[Numeric[T]](http://www.scala-lang.org/api/current/index.html#scala.math.Numeric)
provided implicit, but only for `AnyRef` types, such as BigInt and
BigDecimal. For Scala's primitives the logic has been optimized to bypass
`Numeric[T]`.

### Code duplication

All classes are final, to avoid the resolution overhead of virtual methods. The 
[AtomicBuilder](../monifu-core/src/shared/scala/monifu/concurrent/atomic/AtomicBuilder.scala) mechanism
for constructing references, means that you can let the compiler infer the most efficient atomic reference type
for the values you want, also avoiding the overhead associated with polymorphism.

In order to avoid overhead, there's a lot of code-duplication going on, therefore the code itself is not very DRY. Thankfully [we can test it in bulk](../monifu-core/src/test/scala/monifu/concurrent/atomic/), thanks to the shared interfaces.

### Cache-padded versions for avoiding the false sharing problem

In order to reduce cache contention, cache-padded versions for all Atomic classes are provided in the
[monifu.concurrent.atomic.padded](../monifu-core/src/shared/scala/monifu/concurrent/atomic/padded/) package.

For reference on what that means, see:

- http://mail.openjdk.java.net/pipermail/hotspot-dev/2012-November/007309.html
- http://openjdk.java.net/jeps/142

To use the cache-padded versions, you need to import stuff from the `padded` sub-package:

```scala
import monifu.concurrent.atomic.padded.Atomic

val ref = Atomic(1)
```

### sun.misc.Unsafe

Atomic references in `java.util.concurrent.atomic` are actually built on top of functionality provided by `sun.misc.Unsafe`. This package is not part of the public API of Java SE.

In light of the cache-padded versions, the implementations of Monifu's Atomic references has switched from boxing Java's atomic reference classes to using `sun.misc.Unsafe` directly. A helper is now provided in [monifu.misc.Unsafe](../monifu-core/src/main/scala/monifu/misc/Unsafe.scala) to access that functionality (normally you can't instantiate `sun.misc.Directly`). `monifu.misc.Unsafe` should be compatible with Android too. As a result, Monifu's implementation should have no overhead over Java's implementations.