import com.typesafe.tools.mima.core.ProblemFilters.exclude
import com.typesafe.tools.mima.core._

object MimaFilters {
  lazy val changesFor_3_2_0: Seq[ProblemFilter] = Seq(
    // Signature change in internal instance
    exclude[IncompatibleResultTypeProblem]("monix.catnap.internal.ParallelApplicative.apply"),
    exclude[MissingClassProblem]("monix.eval.internal.TaskGather*")
  )

  lazy val changesFor_3_0_1: Seq[ProblemFilter] = Seq(
    // Signature changes in internal classes
    exclude[DirectMissingMethodProblem]("monix.execution.internal.Trampoline.*"),
    exclude[DirectMissingMethodProblem]("monix.execution.schedulers.TrampolineExecutionContext#JVMNormalTrampoline.*"),
    exclude[DirectMissingMethodProblem]("monix.execution.schedulers.TrampolineExecutionContext#JVMOptimalTrampoline.*")
  )

  lazy val changesFor_3_3_0 = Seq(
    // Upgraded JCTools to 3.0.0
    exclude[IncompatibleMethTypeProblem](
      "monix.execution.internal.collection.queues.FromMessagePassingQueue#Java8SPMC.this"
    ),
    exclude[IncompatibleMethTypeProblem]("monix.execution.internal.collection.queues.FromCircularQueue#Java7.this"),
    exclude[IncompatibleMethTypeProblem]("monix.execution.internal.collection.queues.FromCircularQueue#MPMC.this"),
    exclude[IncompatibleMethTypeProblem]("monix.execution.internal.collection.queues.FromCircularQueue#Java8SPSC.this"),
    exclude[IncompatibleMethTypeProblem]("monix.execution.internal.collection.queues.FromCircularQueue.apply"),
    exclude[IncompatibleMethTypeProblem]("monix.execution.internal.collection.queues.FromCircularQueue.this"),
    exclude[IncompatibleMethTypeProblem]("monix.execution.internal.collection.queues.FromMessagePassingQueue.apply"),
    exclude[IncompatibleMethTypeProblem]("monix.execution.internal.collection.queues.FromMessagePassingQueue.this"),
    exclude[IncompatibleMethTypeProblem](
      "monix.execution.internal.collection.queues.FromMessagePassingQueue#Java8SPSC.this"
    ),
    exclude[IncompatibleMethTypeProblem]("monix.execution.internal.collection.queues.FromMessagePassingQueue.apply"),
    exclude[IncompatibleMethTypeProblem]("monix.execution.internal.collection.queues.FromCircularQueue.apply"),
    exclude[IncompatibleMethTypeProblem](
      "monix.execution.internal.collection.queues.FromMessagePassingQueue#MPMC.this"
    ),
    exclude[MissingTypesProblem]("monix.execution.internal.collection.queues.QueueDrain"),
    exclude[IncompatibleMethTypeProblem]("monix.execution.internal.collection.queues.FromCircularQueue#Java8MPSC.this"),
    exclude[IncompatibleMethTypeProblem](
      "monix.execution.internal.collection.queues.FromMessagePassingQueue#Java8MPSC.this"
    ),
    exclude[IncompatibleMethTypeProblem](
      "monix.execution.internal.collection.queues.FromMessagePassingQueue#Java7.this"
    ),
    exclude[IncompatibleMethTypeProblem]("monix.execution.internal.collection.queues.FromCircularQueue#Java8SPMC.this"),
    exclude[IncompatibleMethTypeProblem](
      "monix.reactive.observers.buffers.ConcurrentQueue#FromMessagePassingQueue.this"
    ),
    // Fixed annoying incremental compilation error with Coeval deprecations
    exclude[MissingTypesProblem]("monix.eval.CoevalInstancesLevel0"),
    exclude[MissingTypesProblem]("monix.eval.Coeval$DeprecatedExtensions"),
    exclude[MissingTypesProblem]("monix.eval.Coeval$"),
    exclude[MissingClassProblem]("monix.eval.internal.CoevalDeprecated$Companion"),
    exclude[MissingClassProblem]("monix.eval.internal.CoevalDeprecated$Extensions"),
    exclude[MissingClassProblem]("monix.eval.internal.CoevalDeprecated"),
    exclude[MissingClassProblem]("monix.eval.internal.CoevalDeprecated$"),
    // Fixed observable.takeLast, replaced with TakeLastObservable
    exclude[MissingClassProblem]("monix.reactive.internal.operators.TakeLastOperator"),
    // Dropped Scala 2.11 support
    exclude[MissingTypesProblem]("monix.execution.Scheduler$Extensions"),
    exclude[MissingClassProblem]("monix.execution.internal.forkJoin.package"),
    exclude[MissingClassProblem]("monix.execution.internal.forkJoin.package$"),
    exclude[MissingClassProblem]("monix.execution.internal.forkJoin.package$ForkJoinPool$"),
    exclude[MissingClassProblem]("monix.execution.schedulers.ExecuteExtensions"),
    // Changes in Task model due to Asynchronous Stack Traces
    exclude[DirectMissingMethodProblem]("monix.eval.Task#Context.copy"),
    exclude[DirectMissingMethodProblem]("monix.eval.Task#Context.this"),
    exclude[IncompatibleMethTypeProblem]("monix.eval.Task#Context.apply"),
    exclude[DirectMissingMethodProblem]("monix.eval.Task#Context.apply"),
    exclude[IncompatibleMethTypeProblem]("monix.eval.Task#Map.apply"),
    exclude[IncompatibleMethTypeProblem]("monix.eval.Task#Map.this"),
    exclude[IncompatibleResultTypeProblem]("monix.eval.Task#Map.copy$default$3"),
    exclude[DirectMissingMethodProblem]("monix.eval.Task#Map.index"),
    exclude[IncompatibleMethTypeProblem]("monix.eval.Task#Map.copy"),
    exclude[DirectMissingMethodProblem]("monix.eval.Task#FlatMap.apply"),
    exclude[DirectMissingMethodProblem]("monix.eval.Task#FlatMap.this"),
    exclude[DirectMissingMethodProblem]("monix.eval.Task#FlatMap.copy"),
    exclude[DirectMissingMethodProblem]("monix.eval.Task#Async.apply"),
    exclude[DirectMissingMethodProblem]("monix.eval.Task#Async.copy"),
    exclude[DirectMissingMethodProblem]("monix.eval.Task#Async.this"),
    // Signature changes in internal classes
    exclude[DirectMissingMethodProblem]("monix.execution.CancelableFuture#Async*"),
    exclude[DirectMissingMethodProblem]("monix.execution.CancelableFuture#Pure*"),
    // Changes in Coeval model due to Better Stack Traces
    exclude[DirectMissingMethodProblem]("monix.eval.Coeval#FlatMap.copy"),
    exclude[DirectMissingMethodProblem]("monix.eval.Coeval#FlatMap.this"),
    exclude[DirectMissingMethodProblem]("monix.eval.Coeval#Map.index"),
    exclude[IncompatibleMethTypeProblem]("monix.eval.Coeval#Map.copy"),
    exclude[IncompatibleResultTypeProblem]("monix.eval.Coeval#Map.copy$default$3"),
    exclude[IncompatibleMethTypeProblem]("monix.eval.Coeval#Map.this"),
    exclude[IncompatibleMethTypeProblem]("monix.eval.Coeval#Map.apply"),
    exclude[DirectMissingMethodProblem]("monix.eval.Coeval#FlatMap.apply"),
    // Remove unused fusionMaxStackDepth
    exclude[DirectMissingMethodProblem]("monix.execution.internal.Platform.fusionMaxStackDepth"),
    exclude[DirectMissingMethodProblem]("monix.execution.internal.Platform.fusionMaxStackDepth")
  )

  lazy val changesFor_3_4_0 = Seq(
    // Remove redundant private interfaces after Scala 2.11 removal
    exclude[MissingClassProblem]("monix.execution.internal.forkJoin.package"),
    exclude[MissingClassProblem]("monix.execution.internal.forkJoin.package$"),
    exclude[MissingClassProblem]("monix.execution.internal.forkJoin.package$ForkJoinPool$"),
    exclude[MissingClassProblem]("monix.execution.misc.compat"),
    exclude[MissingClassProblem]("monix.execution.misc.compat$"),
    // Scala 3 / Dotty support
    exclude[MissingClassProblem]("monix.execution.schedulers.AdaptedThreadPoolExecutorMixin")
  )

  lazy val changesFor_3_5_0 = Seq(
    // Atomic API classes moved to monix-execution-atomic sub-artifact (3.5.0 modularisation);
    // monix-execution declares monix-execution-atomic as a compile dependency so all consumers receive
    // the classes transitively — no actual binary break for downstream code.
    exclude[MissingClassProblem]("monix.execution.atomic.Atomic"),
    exclude[MissingClassProblem]("monix.execution.atomic.Atomic$"),
    exclude[MissingClassProblem]("monix.execution.atomic.Atomic$Macros"),
    exclude[MissingClassProblem]("monix.execution.atomic.AtomicAny"),
    exclude[MissingClassProblem]("monix.execution.atomic.AtomicAny$"),
    exclude[MissingClassProblem]("monix.execution.atomic.AtomicBoolean"),
    exclude[MissingClassProblem]("monix.execution.atomic.AtomicBoolean$"),
    exclude[MissingClassProblem]("monix.execution.atomic.AtomicBuilder"),
    exclude[MissingClassProblem]("monix.execution.atomic.AtomicBuilder$"),
    exclude[MissingClassProblem]("monix.execution.atomic.AtomicBuilder$AtomicBooleanBuilder$"),
    exclude[MissingClassProblem]("monix.execution.atomic.AtomicBuilder$AtomicByteBuilder$"),
    exclude[MissingClassProblem]("monix.execution.atomic.AtomicBuilder$AtomicCharBuilder$"),
    exclude[MissingClassProblem]("monix.execution.atomic.AtomicBuilder$AtomicDoubleBuilder$"),
    exclude[MissingClassProblem]("monix.execution.atomic.AtomicBuilder$AtomicFloatBuilder$"),
    exclude[MissingClassProblem]("monix.execution.atomic.AtomicBuilder$AtomicIntBuilder$"),
    exclude[MissingClassProblem]("monix.execution.atomic.AtomicBuilder$AtomicLongBuilder$"),
    exclude[MissingClassProblem]("monix.execution.atomic.AtomicBuilder$AtomicShortBuilder$"),
    exclude[MissingClassProblem]("monix.execution.atomic.AtomicByte"),
    exclude[MissingClassProblem]("monix.execution.atomic.AtomicByte$"),
    exclude[MissingClassProblem]("monix.execution.atomic.AtomicChar"),
    exclude[MissingClassProblem]("monix.execution.atomic.AtomicChar$"),
    exclude[MissingClassProblem]("monix.execution.atomic.AtomicDouble"),
    exclude[MissingClassProblem]("monix.execution.atomic.AtomicDouble$"),
    exclude[MissingClassProblem]("monix.execution.atomic.AtomicFloat"),
    exclude[MissingClassProblem]("monix.execution.atomic.AtomicFloat$"),
    exclude[MissingClassProblem]("monix.execution.atomic.AtomicInt"),
    exclude[MissingClassProblem]("monix.execution.atomic.AtomicInt$"),
    exclude[MissingClassProblem]("monix.execution.atomic.AtomicLong"),
    exclude[MissingClassProblem]("monix.execution.atomic.AtomicLong$"),
    exclude[MissingClassProblem]("monix.execution.atomic.AtomicNumber"),
    exclude[MissingClassProblem]("monix.execution.atomic.AtomicNumberAny"),
    exclude[MissingClassProblem]("monix.execution.atomic.AtomicNumberAny$"),
    exclude[MissingClassProblem]("monix.execution.atomic.AtomicShort"),
    exclude[MissingClassProblem]("monix.execution.atomic.AtomicShort$"),
    exclude[MissingClassProblem]("monix.execution.atomic.Implicits"),
    exclude[MissingClassProblem]("monix.execution.atomic.Implicits$"),
    exclude[MissingClassProblem]("monix.execution.atomic.Implicits$Level1"),
    exclude[MissingClassProblem]("monix.execution.atomic.Implicits$Level2"),
    exclude[MissingClassProblem]("monix.execution.atomic.PaddingStrategy"),
    exclude[MissingClassProblem]("monix.execution.atomic.PaddingStrategy$"),
    exclude[MissingClassProblem]("monix.execution.atomic.PaddingStrategy$Left128$"),
    exclude[MissingClassProblem]("monix.execution.atomic.PaddingStrategy$Left64$"),
    exclude[MissingClassProblem]("monix.execution.atomic.PaddingStrategy$LeftRight128$"),
    exclude[MissingClassProblem]("monix.execution.atomic.PaddingStrategy$LeftRight256$"),
    exclude[MissingClassProblem]("monix.execution.atomic.PaddingStrategy$NoPadding$"),
    exclude[MissingClassProblem]("monix.execution.atomic.PaddingStrategy$Right128$"),
    exclude[MissingClassProblem]("monix.execution.atomic.PaddingStrategy$Right64$"),
    exclude[MissingClassProblem]("monix.execution.atomic.package"),
    exclude[MissingClassProblem]("monix.execution.atomic.package$"),

    // Internal atomic implementation helpers replaced by JDK 17 / VarHandle equivalents;
    // these classes were explicitly marked @InternalApi and never part of the public contract.
    exclude[MissingClassProblem]("monix.execution.internal.InternalApi"),
    exclude[MissingClassProblem]("monix.execution.internal.atomic.BoxPaddingStrategy"),
    exclude[MissingClassProblem]("monix.execution.internal.atomic.BoxedInt"),
    exclude[MissingClassProblem]("monix.execution.internal.atomic.BoxedLong"),
    exclude[MissingClassProblem]("monix.execution.internal.atomic.BoxedObject"),
    exclude[MissingClassProblem]("monix.execution.internal.atomic.Factory"),
    exclude[MissingClassProblem]("monix.execution.internal.atomic.UnsafeAccess"),
    exclude[MissingClassProblem]("monix.execution.internal.collection.queues.FromCircularQueue$Java7"),
    exclude[MissingClassProblem]("monix.execution.internal.collection.queues.FromMessagePassingQueue$Java7"),

    // Macro implementation helpers moved from monix.execution.misc to monix.execution.atomic.internal
    // as part of the atomic sub-module extraction; all were private macro infrastructure.
    exclude[MissingClassProblem]("monix.execution.misc.HygieneUtilMacros"),
    exclude[MissingClassProblem]("monix.execution.misc.HygieneUtilMacros$util$"),
    exclude[MissingClassProblem]("monix.execution.misc.InlineMacros"),
    exclude[MissingClassProblem]("monix.execution.misc.Local$Macros"),
    exclude[MissingClassProblem]("monix.execution.misc.test.TestBox"),
    exclude[MissingClassProblem]("monix.execution.misc.test.TestBox$"),
    exclude[MissingClassProblem]("monix.execution.misc.test.TestBox$Macros"),
    exclude[MissingClassProblem]("monix.execution.misc.test.TestInlineMacros"),
    exclude[MissingClassProblem]("monix.execution.misc.test.TestInlineMacros$"),
    exclude[MissingClassProblem]("monix.execution.misc.test.TestInlineMacros$Macros"),

    // ConcurrentChannel's private inner classes ChanProducer and ChanConsumer used AtomicAny in their
    // constructor signatures; AtomicAny was moved to monix-execution-atomic sub-artifact in 3.5.0.
    // Both classes are declared `private final class` inside ConcurrentChannel — not accessible to
    // external code. The constructor-signature mismatch is a side-effect of the atomic module split.
    exclude[DirectMissingMethodProblem]("monix.catnap.ConcurrentChannel#ChanConsumer.this"),
    exclude[DirectMissingMethodProblem]("monix.catnap.ConcurrentChannel#ChanProducer.this"),

    // TaskMapBoth#Register is a `private final class` inside `private[eval] object TaskMapBoth`
    // (package monix.eval.internal). sendSignal took Callback as parameter; Callback scaffolding was
    // removed in 3.5.0 (same cleanup already filtered above). Purely internal, not public API.
    exclude[DirectMissingMethodProblem]("monix.eval.internal.TaskMapBoth#Register.sendSignal"),

    // IterantZipMap#Loop is a private inner implementation class inside monix.tail.internal.
    // processOneASeqB changed signature due to internal Cats-Effect API alignment.
    exclude[DirectMissingMethodProblem]("monix.tail.internal.IterantZipMap#Loop.processOneASeqB"),

    // ConcurrentSubject.async(Scheduler) remains available to Scala call-sites via the companion-module
    // method on ConcurrentSubject$. The package-qualified source shim no longer gets a Java static
    // forwarder on the outer ConcurrentSubject class, so MiMa reports only that Java-facing forwarder.
    exclude[DirectMissingMethodProblem]("monix.reactive.subjects.ConcurrentSubject.async"),

    // CollectWhileOperator is private[reactive] — inaccessible outside the reactive package.
    exclude[MissingClassProblem]("monix.reactive.internal.operators.CollectWhileOperator"),
    exclude[MissingClassProblem]("monix.reactive.internal.operators.CollectWhileOperator$"),

    // Scala 3-specific: AsyncQueue constructor is private[monix] — external code cannot call it.
    // The synthetic default accessor for the 3rd constructor parameter ($default$3) is exposed
    // differently across Scala 3 versions; this is not callable by downstream users.
    exclude[DirectMissingMethodProblem]("monix.execution.AsyncQueue.<init>$default$3"),

    // Scala 3-specific: IncompatibleResultTypeProblem for alreadyCanceled() in the four cancelable
    // companions is a Scala 3 Mima encoding artifact. In 3.4.0 the Scala 3 compiler encoded the
    // return type as the parent trait Cancelable#Empty; in 3.5.0 it encodes it as the concrete subtype
    // (Bool or BooleanCancelable / BooleanCancelableF). The semantics are identical — the value IS
    // a subtype, so callers see a strictly more specific type, which is binary-compatible.
    exclude[IncompatibleResultTypeProblem]("monix.execution.cancelables.AssignableCancelable.alreadyCanceled"),
    exclude[IncompatibleResultTypeProblem]("monix.execution.cancelables.BooleanCancelable.alreadyCanceled"),
    exclude[IncompatibleResultTypeProblem]("monix.catnap.cancelables.AssignableCancelableF.alreadyCanceled"),
    exclude[IncompatibleResultTypeProblem]("monix.catnap.cancelables.BooleanCancelableF.alreadyCanceled"),

    // Scala 2.13.17+ stopped emitting scala.runtime.AbstractFunctionN as a mixin on case-class
    // companion objects. MiMa surfaces this as MissingTypesProblem on the companion ($) class.
    // This is a pure encoding artifact — no actual binary break for downstream code.

    // monix-execution: Ack.Continue / Ack.Stop are case objects extending Future; their value()
    // and result() methods shift encoding when AbstractFunctionN mixin is removed.
    exclude[DirectMissingMethodProblem]("monix.execution.Ack#Continue.value"),
    exclude[IncompatibleResultTypeProblem]("monix.execution.Ack#Continue.result"),
    exclude[DirectMissingMethodProblem]("monix.execution.Ack#Stop.value"),
    exclude[IncompatibleResultTypeProblem]("monix.execution.Ack#Stop.result"),

    // monix-reactive: AbstractFunctionN encoding artifact on case-class companions
    exclude[MissingTypesProblem]("monix.reactive.Notification$OnError$"),
    exclude[MissingTypesProblem]("monix.reactive.OverflowStrategy$BackPressure$"),
    exclude[MissingTypesProblem]("monix.reactive.OverflowStrategy$ClearBuffer$"),
    exclude[MissingTypesProblem]("monix.reactive.OverflowStrategy$DropNew$"),
    exclude[MissingTypesProblem]("monix.reactive.OverflowStrategy$DropOld$"),
    exclude[MissingTypesProblem]("monix.reactive.OverflowStrategy$Fail$"),
    exclude[MissingTypesProblem]("monix.reactive.internal.operators.ConcatMapObservable$FlatMapState$Active$"),
    exclude[MissingTypesProblem]("monix.reactive.internal.operators.ConcatMapObservable$FlatMapState$WaitComplete$"),
    exclude[MissingTypesProblem]("monix.reactive.internal.operators.ConcatMapObservable$FlatMapState$WaitOnNextChild$"),
    exclude[MissingTypesProblem]("monix.reactive.internal.operators.MapTaskObservable$MapTaskState$Active$"),
    exclude[MissingTypesProblem]("monix.reactive.internal.operators.MapTaskObservable$MapTaskState$WaitComplete$"),
    exclude[MissingTypesProblem](
      "monix.reactive.internal.rstreams.ReactiveSubscriberAsMonixSubscriber$RequestsQueue$ActiveState$"
    ),
    exclude[MissingTypesProblem]("monix.execution.BufferCapacity$Bounded$"),
    exclude[MissingTypesProblem]("monix.execution.BufferCapacity$Unbounded$"),
    exclude[MissingTypesProblem]("monix.execution.ExecutionModel$BatchedExecution$"),
    exclude[MissingTypesProblem]("monix.execution.cancelables.CompositeCancelable$Active$"),
    exclude[MissingTypesProblem]("monix.execution.cancelables.OrderedCancelable$Active$"),
    exclude[MissingTypesProblem]("monix.execution.cancelables.RefCountCancelable$State$"),
    exclude[MissingTypesProblem]("monix.execution.cancelables.SingleAssignCancelable$State$IsActive$"),
    exclude[MissingTypesProblem]("monix.execution.exceptions.DummyException$"),
    // DummyException extends Exception which extends Function1 in 3.4.0 encoding; andThen/compose
    // were inherited from AbstractFunction1 which is no longer mixed in as of Scala 2.13.17+.
    exclude[DirectMissingMethodProblem]("monix.execution.exceptions.DummyException.andThen"),
    exclude[DirectMissingMethodProblem]("monix.execution.exceptions.DummyException.compose"),
    exclude[MissingTypesProblem]("monix.execution.internal.GenericSemaphore$State$"),
    exclude[MissingTypesProblem]("monix.execution.rstreams.ReactivePullStrategy$FixedWindow$"),
    exclude[MissingTypesProblem]("monix.execution.rstreams.SingleAssignSubscription$State$EmptyRequest$"),
    exclude[MissingTypesProblem]("monix.execution.rstreams.SingleAssignSubscription$State$WithSubscription$"),
    exclude[MissingTypesProblem]("monix.execution.schedulers.ReferenceScheduler$WrappedScheduler$"),
    // StartAsyncBatchRunnable$ companion lost AbstractFunctionN mixin; tupled/curried were
    // inherited from AbstractFunction2 which is no longer mixed in as of Scala 2.13.17+.
    exclude[MissingTypesProblem]("monix.execution.schedulers.StartAsyncBatchRunnable$"),
    exclude[DirectMissingMethodProblem]("monix.execution.schedulers.StartAsyncBatchRunnable.tupled"),
    exclude[DirectMissingMethodProblem]("monix.execution.schedulers.StartAsyncBatchRunnable.curried"),
    exclude[MissingTypesProblem]("monix.execution.schedulers.TestScheduler$State$"),
    exclude[MissingTypesProblem]("monix.catnap.CircuitBreaker$Closed$"),
    exclude[MissingTypesProblem]("monix.eval.Coeval$Error$"),
    exclude[MissingTypesProblem]("monix.eval.Task$Options$"),
    exclude[MissingTypesProblem]("monix.eval.internal.ForwardCancelable$Active$"),
    exclude[MissingTypesProblem]("monix.eval.internal.ForwardCancelable$Empty$"),
    // makeReleaseFrame is a method on private inner classes inside monix.eval.internal.TaskBracket;
    // the companion lost AbstractFunctionN mixin, causing the method signature to shift.
    exclude[DirectMissingMethodProblem]("monix.eval.internal.TaskBracket#StartCase.makeReleaseFrame"),
    exclude[DirectMissingMethodProblem]("monix.eval.internal.TaskBracket#StartE.makeReleaseFrame"),
    exclude[MissingTypesProblem]("monix.eval.internal.TaskConnectionComposite$Active$"),
    exclude[MissingTypesProblem]("monix.eval.internal.TaskConnectionRef$IsActive$"),
    exclude[MissingTypesProblem]("monix.eval.tracing.CoevalEvent$StackTrace$"),
    // PrintingOptions is a case class; its companion lost AbstractFunctionN mixin so
    // apply/copy/copy$default$N static forwarders are no longer emitted by scalac 2.13.17+.
    exclude[DirectMissingMethodProblem]("monix.eval.tracing.PrintingOptions.apply"),
    exclude[DirectMissingMethodProblem]("monix.eval.tracing.PrintingOptions.copy"),
    exclude[DirectMissingMethodProblem]("monix.eval.tracing.PrintingOptions.copy$default$1"),
    exclude[DirectMissingMethodProblem]("monix.eval.tracing.PrintingOptions.copy$default$2"),
    exclude[DirectMissingMethodProblem]("monix.eval.tracing.PrintingOptions.copy$default$3"),
    exclude[MissingTypesProblem]("monix.eval.tracing.TaskEvent$StackTrace$"),
    exclude[MissingTypesProblem]("monix.tail.internal.IterantToReactivePublisher$Await$"),
    exclude[MissingTypesProblem]("monix.tail.internal.IterantToReactivePublisher$Interrupt$"),
    exclude[MissingTypesProblem]("monix.tail.internal.IterantToReactivePublisher$Request$"),

    // EmptyBatch.cursor return type widened from EmptyCursor to BatchCursor (covariant widening).
    // MiMa reports both IncompatibleResultTypeProblem (type change) and DirectMissingMethodProblem
    // (old bridge method absent). Safe: widening is covariant and EmptyCursor extends BatchCursor.
    exclude[IncompatibleResultTypeProblem]("monix.tail.batches.EmptyBatch.cursor"),
    exclude[DirectMissingMethodProblem]("monix.tail.batches.EmptyBatch.cursor"),

    // BREAKAGE — unfortunately it's something we must live with
    exclude[DirectMissingMethodProblem]("monix.tail.IterantBuilders#Apply.suspend$extension"),
  )
}
