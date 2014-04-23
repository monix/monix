# Schedulers

<img src="assets/monifu.png" align="right" />

Monifu's Schedulers and [Cancelables](./cancelables.md) are inspired by .NET's
[Reactive Extensions](https://rx.codeplex.com/) and Netflix's
[RxJava](https://github.com/Netflix/RxJava) and are cross-compiled to
[Scala.js](scala-js.org) for also targeting JavaScript runtimes.

## Status Quo

Sometimes you need to schedule units of computation to execute
asynchronously, like on a different thread, or in a different
thread-pool. Towards this purpose we can use an
[ExecutionContext](http://www.scala-lang.org/api/current/#scala.concurrent.ExecutionContext)
from Scala's standard library, as in:

```scala
import scala.concurrent.ExecutionContext.Implicits.global

global.execute(new Runnable {
  def run(): Unit = {
    println("Executing asynchronously ...")
  }
})
```

An `ExecutionContext` is too limited, having the following problems:

1. It cannot execute things with a given delay
2. It cannot execute units of work periodically (e.g. once every
   second)
3. The `execute()` method doesn't return a token you could use to
   cancel the pending execution of a task

Developers using Akka do have a
[nicer interface](http://doc.akka.io/docs/akka/current/scala/scheduler.html)
that solve the above problems in the form of
[akka.actor.Scheduler](http://doc.akka.io/api/akka/current/index.html#akka.actor.Scheduler),
so you can do this:

```scala
val task: akka.actor.Cancellable =
  ActorSystem("default").scheduler.scheduleOnce(1.second) {
    println("Executing asynchronously ...")
  }

// canceling it before execution happens
task.cancel()
```

There are problems with the above approach - Akka's Scheduler is an
integral part of Akka's actors system and their usage implies a
dependency on Akka, which is a pretty heavy dependency and there's no
good reason for that, Cancelables are useful outside the context of
Schedulers or Akka and in terms of the API, as you'll see, we can do better.

Another approach is to use a
[ScheduledExecutorService](http://docs.oracle.com/javase/7/docs/api/java/util/concurrent/ScheduledExecutorService.html)
from Java's standard library and is fairly capable and standard, however the API is not idiomatic Scala,
with the results returned being of type
[j.u.c.ScheduledFuture](http://docs.oracle.com/javase/7/docs/api/java/util/concurrent/ScheduledFuture.html),
which are pretty heavy and have nothing to do with Scala's Futures and
again, this API can surely use improvement.


## Monifu's Scheduler

A
[monifu.concurrent.Scheduler](../monifu-core/src/shared/scala/monifu/concurrent/Scheduler.scala)
is a super-set of a
[scala.concurrent.ExecutionContext](http://www.scala-lang.org/api/current/#scala.concurrent.ExecutionContext),
that is as I've said, inspired by [Rx.Scheduler](https://github.com/Netflix/RxJava/wiki/Scheduler).

### Initialization

On top of the JVM, to get an instance is easy:
```scala
val s = monifu.concurrent.Scheduler.computation
```

This is in fact an instance of
[ConcurrentScheduler](../monifu-core/src/main/scala/monifu/concurrent/schedulers/ConcurrentScheduler.scala),
the only provided implementation at the moment of writing (because it
is sufficient for most needs), with `Scheduler.computation` being a
default instance that internally uses
[ExecutionContext.Implicits.global](http://www.scala-lang.org/api/current/#scala.concurrent.ExecutionContext$$Implicits$)
for executing tasks and a
[j.u.c.ScheduledThreadPoolExecutor](http://docs.oracle.com/javase/7/docs/api/java/util/concurrent/ScheduledThreadPoolExecutor.html)
instance for scheduling delayed or periodic tasks.

You can construct your own instances like so:

```scala
import monifu.concurrent.Scheduler
import monifu.concurrent.schedulers._

// from an ExecutionContext ...
val s = Scheduler.fromContext(ExecutionContext.Implicits.global)

// or from a Java ExecutorService
import java.util.concurrent.Executors
val s = Scheduler.fromExecutorService(Executors.newCachedThreadPool())
```

`ConcurrentScheduler` uses by default a single, global
`j.u.c.ScheduledThreadPoolExecutor` instance for all created
instances, since this executor does nothing else but to do scheduling
and is not in charge of actually executing the tasks. But if you don't
like this default and want to use your own configured scheduled
executor, you can do so like this:

```scala
import monifu.concurrent.schedulers._
import java.util.concurrent.Executors
import concurrent.ExecutionContext

val s = ConcurrentScheduler(
  schedulerService = Executors.newSingleThreadScheduledExecutor(),
  ec = ExecutionContext.Implicits.global
)
```

### Usage 

#### Immediate execution

To schedule a unit of work for immediate execution:

```scala
import monifu.concurrent.Scheduler.{computation => s}

val task = s.scheduleOnce {
  println("Hello world")
}

// in case we want to cancel the pending execution, in case
// it hasn't executed yet
task.cancel()
```

As you can see, the returned token is a
[Cancelable](../monifu-core/src/shared/scala/monifu/concurrent/Cancelable.scala)
that can be used to cancel the pending execution.

#### Delayed execution

You can also schedule units of computation with a delay, specified as
a `concurrent.duration.FiniteDuration`:

```scala
import monifu.concurrent.Scheduler.{computation => s}

val task = s.scheduleOnce(1.second, {
  println("Hello world")
})

// in case we want to cancel the pending execution
task.cancel()
```

The above schedules a computation to run after 1 second since now. The
returned token is also a
[Cancelable](../monifu-core/src/shared/scala/monifu/concurrent/Cancelable.scala)
that can be used to cancel the pending execution.

#### Repeated execution

We can also schedule things to run periodically:

```scala
import monifu.concurrent.Scheduler.{computation => s}

val task = s.scheduleRepeated(1.second, 2.second, {
  println("Hello world")
})

// in case we want to cancel the execution of this periodic task
task.cancel()
```

The above will print `"Hello world"` after 1 second (the initial
delay) and then every 2 seconds after that. Successive executions of
a task scheduled with `scheduleRepeated()` do NOT overlap and are
scheduled to run after the specified `delay`.

#### Recursive re-scheduling - tasks that re-schedule themselves

The `Scheduler` also provides a pair of methods that is more general.
First is the `scheduleRecursive()`, my favorite:

```scala
import monifu.concurrent.Scheduler.{computation => s}
import monifu.concurrent.atomic.Atomic

val counter = Atomic(0)

val task = s.scheduleRecursive(1.second, 2.seconds, { reschedule =>
  val c = counter.incrementAndGet
  println(s"Counter: $c")

  if (c < 10)
    reschedule()
})


// in case you want to cancel it prematurely:
// task.cancel()
```

When you run the above it will print:

```
Counter: 1
Counter: 2
Counter: 3
Counter: 4
Counter: 5
Counter: 6
Counter: 7
Counter: 8
Counter: 9
Counter: 10
```

And then it will stop, by itself. This is a more general variant of
`scheduleRepeated` and in fact if you look at
[its implementation](../monifu-core/src/shared/scala/monifu/concurrent/Scheduler.scala),
`scheduleRepeated` is implemented in terms of `scheduleRecursive`,
something like this:

```scala
def scheduleRepeated(initialDelay: FiniteDuration, delay: FiniteDuration, action: => Unit): Cancelable =
  scheduleRecursive(initialDelay, delay, { reschedule =>
    action
    reschedule()
  })
```

The callback passed to `scheduleRecursive` takes one parameter which
is a function that allows you to schedule the next periodic execution
in the chain and you can stop doing so at any time.

But that's not general enough. The `Scheduler` also provides a pair of
methods, that take as parameter the current `Scheduler` instance and
that must return a `Cancelable`. Here's how the above example would
look like:

```scala
import monifu.concurrent.Scheduler
import monifu.concurrent.Scheduler.{computation => s}
import monifu.concurrent.atomic.Atomic

val counter = Atomic(0)

def loop(scheduler: Scheduler): Cancelable = {
  val c = counter.incrementAndGet
  println(s"Counter: $c")

  if (c < 10)
    scheduler.schedule(2.seconds, loop)
  else
    Cancelable.empty
}

val task = s.schedule(1.seconds, loop)

// in case you want to cancel it prematurely:
// task.cancel()
```

The effect of the above would be the same as with the
`scheduleRecursive` or the `scheduleRepeated` examples.

### Real-world use-case: Futures with Timeouts

We love doing things asynchronously, by means of
[Futures/Promises](http://docs.scala-lang.org/overviews/core/futures.html),
right?

One problem that happens in the wild is that you often need to trigger
some sort of error in case a `Future` isn't completed in a reasonable
amount of time.
[Await.result](http://www.scala-lang.org/files/archive/nightly/docs/library/index.html#scala.concurrent.Await$)
does have an `atMost` parameter, specifying the maximum wait time -
but in case you haven't heard, blocking threads is a really bad
practice, unless you really, really know what you're doing. And the
equivalent asynchronous `await` from
[Scala-Async](https://github.com/scala/async) doesn't have an `atMost`
timeout parameter.

So what about a combinator that we could use to complete a `Future` in
case some maximum wait time is exceeded?

```scala
def withTimeout[T](f: Future[T], atMost: FiniteDuration)(implicit s: Scheduler): Future[T] = {
  // catching the exception here, for non-useless stack traces
  val err = Try(throw new TimeoutException)
  val promise = Promise[T]()
  val task = s.scheduleOnce(atMost, promise.tryComplete(err))

  f.onComplete { case r =>
    // canceling task to prevent memory leaks
    task.cancel()
    promise.tryComplete(r)
  }

  promise.future
}
```

This is such a useful combinator that it's provided as an extension
method for futures in Monifu's
[FutureExtensions](../monifu-core/src/shared/scala/monifu/concurrent/extensions.scala)
and so you can use it directly without wheel reinvention:

```scala
import monifu.concurrent._
// the method takes an implicit Scheduler, so we need this:
import monifu.concurrent.Scheduler.Implicits.computation

val futureWithTimeout = myFuture.withTimeout(1.second)
```

Actually, since we are on the subject, you also have `delayedResult`
as an extension of the `Future` companion object, also available in
[FutureCompanionExtensions](../monifu-core/src/shared/scala/monifu/concurrent/extensions.scala),
who's implementation is something like:

```scala
def delayedResult[T](delay: FiniteDuration)(result: => T)(implicit s: Scheduler): Future[T] = {
  val p = Promise[T]()
  s.scheduleOnce(delay, p.success(result))
  p.future
}
```

To use these utilities, you only need to `import concurrent.monifu._`.

### Real-world use-case: Keep-Alive connection until Future completes

A colleague had the following problem: Heroku's router
[times-out the request](https://devcenter.heroku.com/articles/request-timeout)
in case no byte has been sent to the client in the initial 30 seconds
and each byte sent transmitted thereafter resets a rolling 55 seconds
window.

And so in case you have a long running processing that returns a
`Future[T]` (with Play Framework 2.x), one quick and dirty solution is
to construct an
[Enumerator](http://www.playframework.com/documentation/2.2.x/Enumerators)
that sends blanks over the wire until the `Future` is complete. And
we can even do this in a generic/reusable way:

```scala
import concurrent._
import concurrent.duration._
import monifu.concurrent.Scheduler.Implicits.computation
import play.api.libs.iteratee.{Iteratee, Enumerator, Concurrent}
import scala.util.{Failure, Success}

def withKeepAlive[T](keepAlive: T, period: FiniteDuration)(cb: => Future[T])(implicit s: Scheduler): Enumerator[T] = {
  val lock = new AnyRef
  var isCompleted = false
 
  Concurrent.unicast[T](onStart = chan => {
    cb.onComplete {
      case Success(output) =>
        lock.synchronized { isCompleted = true }
        chan.push(output)
        chan.end()
 
      case Failure(error) =>
        lock.synchronized { isCompleted = true }
        chan.end(error)
    }
 
    s.scheduleRecursive(period, period, { reschedule =>
      lock.synchronized {
        if (!isCompleted) {
          chan.push(keepAlive)
          reschedule()
        }
      }
    })
  })
}
```

Testing it is straightforward:

```scala
import monifu.concurrent.extensions._

def runTest(implicit s: Scheduler) = {
  val enum = futureWithKeepAlive(300.millis, ".") {
    Future.delayedResult(5.seconds)(" Hello world!")
  }
  
  enum.run(Iteratee.foreach[String](x => print(x)))
}
```

## Targeting Javascript runtimes with Scala.js

Monifu is cross-compiled to [Scala.js](http://www.scala-js.org/) and
as such you can target Javascript runtimes with it.

The available `Scheduler` implementation is
[AsyncScheduler](../monifu-core-js/src/main/scala/monifu/concurrent/schedulers/AsyncScheduler.scala)
and is based on Javascript's `global.setTimeout` for scheduling. It's
a full-fledged `Scheduler` implementation and you can use it as such:

```scala
val s = monifu.concurrent.Scheduler.async

var counter = 0

val task = s.scheduleRecursive(1.second, 2.seconds, { reschedule =>
  counter += 1
  println(s"Counter: $counter")

  if (c < 10)
    reschedule()
})
```

## Further Reading

Also see the document on [Cancelables](./cancelables.md).

# TODO

- provide a Scheduler.newThread implementation 
- provide a Scheduler.trampoline implementation, for batched execution
  of tasks on the current thread (risky, so I'm still debating the
  necessity for it)
