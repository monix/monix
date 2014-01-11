# Atomic Reference

**NOTE** - document is incorrect !!!

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
[Atomic[T]](https://github.com/alexandru/scala-atomic/blob/master/src/main/scala/scala/concurrent/atomic/Atomic.scala)
trait.

```scala
import scala.concurrent.atomic._

val refInt: Atomic[Int] = Atomic(0)
val refLong: Atomic[Long] = Atomic(0L)
val refString: Atomic[String] = Atomic("hello")
```

### Working with Numbers

One really common use-case for atomic references are for numbers to
which you need to add or subtract. To this purpose
`j.u.c.a.AtomicInteger` and `j.u.c.a.AtomicLong` have an
`incrementAndGet` helper. However Ints and Longs aren't the only type
you normally need. How about `Float` and `Double`? How about
`BigDecimal` and `BigInt`?

In Scala, thanks to the
[Numeric[T]](http://www.scala-lang.org/api/current/index.html#scala.math.Numeric)
type-class, we can do this:

```scala 
scala> import scala.concurrent.atomic._

scala> val ref = Atomic(BigInt(1))
ref: scala.concurrent.atomic.AtomicAnyRef[scala.math.BigInt] = Atomic(1)

scala> ref.incrementAndGet
res0: scala.math.BigInt = 2

scala> ref.incrementAndGet(BigInt("329084291234234"))
res1: scala.math.BigInt = 329084291234236

scala> val ref = Atomic("hello")
ref: scala.concurrent.atomic.AtomicAnyRef[String] = Atomic(hello)

scala> ref.incrementAndGet
<console>:12: error: could not find implicit value for parameter num: Numeric[String]
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
primitives because
[Autoboxing/Unboxing](http://docs.oracle.com/javase/tutorial/java/data/autoboxing.html)
gets involved. And then there's the efficiency issue. By using an
AtomicReference, you'll end up with extra boxing/unboxing going on.

`Float` can be stored inside an `AtomicInteger` by using Java's
`Float.floatToIntBits` and `Float.intBitstoFloat`. `Double` can be
stored inside an `AtomicLong` by using Java's
`Double.doubleToLongBits` and `Double.longBitsToDouble`. `Char`,
`Byte` and `Short` can be stored inside an `AtomicInteger` as well,
with special care to handle overflows correctly.

```scala
scala> import scala.concurrent.atomic._

scala> val ref = Atomic(0.0)
ref: scala.concurrent.atomic.AtomicDouble = Atomic(0.0)

scala> ref.compareAndSet(0.0, 100.0)
res0: Boolean = true

scala> ref.incrementAndGet
res1: Double = 101.0

scala> val ref = Atomic('a')
ref: scala.concurrent.atomic.AtomicChar = Atomic(a)

scala> ref.incrementAndGet
res2: Char = b

scala> ref.incrementAndGet
res3: Char = c
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
ref: AtomicAnyRef[immutable.Queue[String]] = Atomic(Queue())

scala> ref.transformAndGet(_.enqueue("hello"))
res: immutable.Queue[String] = Queue(hello)

scala> ref.transformAndGet(_.enqueue("world"))
res: immutable.Queue[String] = Queue(hello, world)
```

### Efficiency

Atomic references are low-level primitives for concurrency and because
of that any extra overhead is unacceptable. 

For example having a common `Atomic[T]` interface implies
boxing/unboxing of primitives. However, because of Scala's wonderful
[@specialized](http://www.scala-lang.org/api/current/index.html#scala.specialized)
annotation we can avoid it.

Increments/decrements are done by going through the
[Numeric[T]](http://www.scala-lang.org/api/current/index.html#scala.math.Numeric)
provided implicit, but only for `AnyRef` types, such as BigInt and
BigDecimal. For primitives the logic has been optimized to bypass
`Numeric[T]`.

As a result, the implementation for primitives is not DRY, with code
duplication all over the place. But that's a useful trade-off.

## TODO

- [ ] AtomicArray
- [ ] pull request on Scala's master, hopefully followed by approval