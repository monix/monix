# Schedulers & Cancelables

Monifu's Schedulers and Cancelables are inspired by .NET's
[Reactive Extensions](https://rx.codeplex.com/) and Netflix's
[RxJava](https://github.com/Netflix/RxJava) and are cross-compiled to
[Scala.js](scala-js.org) for also targeting Javascript runtimes.

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


