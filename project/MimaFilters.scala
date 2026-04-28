import com.typesafe.tools.mima.core.ProblemFilters.exclude
import com.typesafe.tools.mima.core._

object MimaFilters {

  // ── Per-sub-project filter buckets ──────────────────────────────────────

  object Monix {
    lazy val all: Seq[ProblemFilter] = Seq.empty
  }

  object MonixInternalJCTools {
    lazy val all: Seq[ProblemFilter] = Seq.empty
  }

  object MonixExecutionAtomic {
    lazy val all: Seq[ProblemFilter] = Seq.empty
  }

  object MonixExecution {
    lazy val changesFor_3_0_1: Seq[ProblemFilter] = Seq(
      exclude[DirectMissingMethodProblem]("monix.execution.internal.Trampoline.*"),
      exclude[DirectMissingMethodProblem](
        "monix.execution.schedulers.TrampolineExecutionContext#JVMNormalTrampoline.*"
      ),
      exclude[DirectMissingMethodProblem](
        "monix.execution.schedulers.TrampolineExecutionContext#JVMOptimalTrampoline.*"
      )
    )

    lazy val changesFor_3_3_0: Seq[ProblemFilter] = Seq(
      exclude[IncompatibleMethTypeProblem](
        "monix.execution.internal.collection.queues.FromMessagePassingQueue#Java8SPMC.this"
      ),
      exclude[IncompatibleMethTypeProblem]("monix.execution.internal.collection.queues.FromCircularQueue#Java7.this"),
      exclude[IncompatibleMethTypeProblem]("monix.execution.internal.collection.queues.FromCircularQueue#MPMC.this"),
      exclude[IncompatibleMethTypeProblem](
        "monix.execution.internal.collection.queues.FromCircularQueue#Java8SPSC.this"
      ),
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
      exclude[IncompatibleMethTypeProblem](
        "monix.execution.internal.collection.queues.FromCircularQueue#Java8MPSC.this"
      ),
      exclude[IncompatibleMethTypeProblem](
        "monix.execution.internal.collection.queues.FromMessagePassingQueue#Java8MPSC.this"
      ),
      exclude[IncompatibleMethTypeProblem](
        "monix.execution.internal.collection.queues.FromMessagePassingQueue#Java7.this"
      ),
      exclude[IncompatibleMethTypeProblem](
        "monix.execution.internal.collection.queues.FromCircularQueue#Java8SPMC.this"
      ),
      exclude[MissingTypesProblem]("monix.execution.Scheduler$Extensions"),
      exclude[MissingClassProblem]("monix.execution.internal.forkJoin.package"),
      exclude[MissingClassProblem]("monix.execution.internal.forkJoin.package$"),
      exclude[MissingClassProblem]("monix.execution.internal.forkJoin.package$ForkJoinPool$"),
      exclude[MissingClassProblem]("monix.execution.schedulers.ExecuteExtensions"),
      exclude[DirectMissingMethodProblem]("monix.execution.CancelableFuture#Async*"),
      exclude[DirectMissingMethodProblem]("monix.execution.CancelableFuture#Pure*"),
      exclude[DirectMissingMethodProblem]("monix.execution.internal.Platform.fusionMaxStackDepth"),
      exclude[DirectMissingMethodProblem]("monix.execution.internal.Platform.fusionMaxStackDepth")
    )

    lazy val changesFor_3_4_0: Seq[ProblemFilter] = Seq(
      exclude[MissingClassProblem]("monix.execution.internal.forkJoin.package"),
      exclude[MissingClassProblem]("monix.execution.internal.forkJoin.package$"),
      exclude[MissingClassProblem]("monix.execution.internal.forkJoin.package$ForkJoinPool$"),
      exclude[MissingClassProblem]("monix.execution.misc.compat"),
      exclude[MissingClassProblem]("monix.execution.misc.compat$"),
      exclude[MissingClassProblem]("monix.execution.schedulers.AdaptedThreadPoolExecutorMixin")
    )

    lazy val changesFor_3_5_0: Seq[ProblemFilter] = Seq(
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
      exclude[MissingClassProblem]("monix.execution.internal.InternalApi"),
      exclude[MissingClassProblem]("monix.execution.internal.atomic.BoxPaddingStrategy"),
      exclude[MissingClassProblem]("monix.execution.internal.atomic.BoxedInt"),
      exclude[MissingClassProblem]("monix.execution.internal.atomic.BoxedLong"),
      exclude[MissingClassProblem]("monix.execution.internal.atomic.BoxedObject"),
      exclude[MissingClassProblem]("monix.execution.internal.atomic.Factory"),
      exclude[MissingClassProblem]("monix.execution.internal.atomic.UnsafeAccess"),
      exclude[MissingClassProblem]("monix.execution.internal.collection.queues.FromCircularQueue$Java7"),
      exclude[MissingClassProblem]("monix.execution.internal.collection.queues.FromMessagePassingQueue$Java7"),
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
      exclude[DirectMissingMethodProblem]("monix.execution.AsyncQueue.<init>$default$3"),
      exclude[IncompatibleResultTypeProblem]("monix.execution.cancelables.AssignableCancelable.alreadyCanceled"),
      exclude[IncompatibleResultTypeProblem]("monix.execution.cancelables.BooleanCancelable.alreadyCanceled"),
      // TrampolineExecutionContext signature tweaks (internal API)
      exclude[IncompatibleMethTypeProblem](
        "monix.execution.schedulers.TrampolineExecutionContext#JVMNormalTrampoline.startLoop"
      ),
      exclude[IncompatibleMethTypeProblem](
        "monix.execution.schedulers.TrampolineExecutionContext#JVMOptimalTrampoline.startLoop"
      ),
      exclude[IncompatibleMethTypeProblem]("monix.execution.schedulers.TrampolineExecutionContext.this")
    )

    lazy val all: Seq[ProblemFilter] =
      Seq(changesFor_3_0_1, changesFor_3_3_0, changesFor_3_4_0, changesFor_3_5_0).flatten
  }

  object MonixCatnap {
    lazy val changesFor_3_2_0: Seq[ProblemFilter] = Seq(
      exclude[IncompatibleResultTypeProblem]("monix.catnap.internal.ParallelApplicative.apply")
    )

    lazy val changesFor_3_5_0: Seq[ProblemFilter] = Seq(
      exclude[DirectMissingMethodProblem]("monix.catnap.ConcurrentChannel#ChanConsumer.this"),
      exclude[DirectMissingMethodProblem]("monix.catnap.ConcurrentChannel#ChanProducer.this"),
      exclude[IncompatibleResultTypeProblem]("monix.catnap.cancelables.AssignableCancelableF.alreadyCanceled"),
      exclude[IncompatibleResultTypeProblem]("monix.catnap.cancelables.BooleanCancelableF.alreadyCanceled")
    )

    lazy val all: Seq[ProblemFilter] = Seq(changesFor_3_2_0, changesFor_3_5_0).flatten
  }

  object MonixEval {
    lazy val changesFor_3_2_0: Seq[ProblemFilter] = Seq(
      exclude[MissingClassProblem]("monix.eval.internal.TaskGather*")
    )

    lazy val changesFor_3_3_0: Seq[ProblemFilter] = Seq(
      exclude[MissingTypesProblem]("monix.eval.CoevalInstancesLevel0"),
      exclude[MissingTypesProblem]("monix.eval.Coeval$DeprecatedExtensions"),
      exclude[MissingTypesProblem]("monix.eval.Coeval$"),
      exclude[MissingClassProblem]("monix.eval.internal.CoevalDeprecated$Companion"),
      exclude[MissingClassProblem]("monix.eval.internal.CoevalDeprecated$Extensions"),
      exclude[MissingClassProblem]("monix.eval.internal.CoevalDeprecated"),
      exclude[MissingClassProblem]("monix.eval.internal.CoevalDeprecated$"),
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
      exclude[DirectMissingMethodProblem]("monix.eval.Coeval#FlatMap.copy"),
      exclude[DirectMissingMethodProblem]("monix.eval.Coeval#FlatMap.this"),
      exclude[DirectMissingMethodProblem]("monix.eval.Coeval#Map.index"),
      exclude[IncompatibleMethTypeProblem]("monix.eval.Coeval#Map.copy"),
      exclude[IncompatibleResultTypeProblem]("monix.eval.Coeval#Map.copy$default$3"),
      exclude[IncompatibleMethTypeProblem]("monix.eval.Coeval#Map.this"),
      exclude[IncompatibleMethTypeProblem]("monix.eval.Coeval#Map.apply"),
      exclude[DirectMissingMethodProblem]("monix.eval.Coeval#FlatMap.apply")
    )

    lazy val changesFor_3_5_0: Seq[ProblemFilter] = Seq(
      exclude[DirectMissingMethodProblem]("monix.eval.internal.TaskMapBoth#Register.sendSignal")
    )

    lazy val all: Seq[ProblemFilter] = Seq(changesFor_3_2_0, changesFor_3_3_0, changesFor_3_5_0).flatten
  }

  object MonixTail {
    lazy val changesFor_3_5_0: Seq[ProblemFilter] = Seq(
      exclude[DirectMissingMethodProblem]("monix.tail.internal.IterantZipMap#Loop.processOneASeqB"),
      exclude[DirectMissingMethodProblem]("monix.tail.IterantBuilders#Apply.suspend$extension")
    )

    lazy val all: Seq[ProblemFilter] = Seq(changesFor_3_5_0).flatten
  }

  object MonixReactive {
    lazy val changesFor_3_3_0: Seq[ProblemFilter] = Seq(
      exclude[IncompatibleMethTypeProblem](
        "monix.reactive.observers.buffers.ConcurrentQueue#FromMessagePassingQueue.this"
      ),
      exclude[MissingClassProblem]("monix.reactive.internal.operators.TakeLastOperator")
    )

    lazy val changesFor_3_5_0: Seq[ProblemFilter] = Seq(
      exclude[DirectMissingMethodProblem]("monix.reactive.subjects.ConcurrentSubject.async"),
      exclude[MissingClassProblem]("monix.reactive.internal.operators.CollectWhileOperator"),
      exclude[MissingClassProblem]("monix.reactive.internal.operators.CollectWhileOperator$")
    )

    lazy val all: Seq[ProblemFilter] = Seq(changesFor_3_3_0, changesFor_3_5_0).flatten
  }

  object MonixJava {
    lazy val all: Seq[ProblemFilter] = Seq.empty
  }
}
