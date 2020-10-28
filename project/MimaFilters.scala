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

  lazy val changesFor_3_2_3 = Seq(
    // Upgraded JCTools to 3.0.0
    exclude[IncompatibleMethTypeProblem]("monix.execution.internal.collection.queues.FromMessagePassingQueue#Java8SPMC.this"),
    exclude[IncompatibleMethTypeProblem]("monix.execution.internal.collection.queues.FromCircularQueue#Java7.this"),
    exclude[IncompatibleMethTypeProblem]("monix.execution.internal.collection.queues.FromCircularQueue#MPMC.this"),
    exclude[IncompatibleMethTypeProblem]("monix.execution.internal.collection.queues.FromCircularQueue#Java8SPSC.this"),
    exclude[IncompatibleMethTypeProblem]("monix.execution.internal.collection.queues.FromCircularQueue.apply"),
    exclude[IncompatibleMethTypeProblem]("monix.execution.internal.collection.queues.FromCircularQueue.this"),
    exclude[IncompatibleMethTypeProblem]("monix.execution.internal.collection.queues.FromMessagePassingQueue.apply"),
    exclude[IncompatibleMethTypeProblem]("monix.execution.internal.collection.queues.FromMessagePassingQueue.this"),
    exclude[IncompatibleMethTypeProblem]("monix.execution.internal.collection.queues.FromMessagePassingQueue#Java8SPSC.this"),
    exclude[IncompatibleMethTypeProblem]("monix.execution.internal.collection.queues.FromMessagePassingQueue.apply"),
    exclude[IncompatibleMethTypeProblem]("monix.execution.internal.collection.queues.FromCircularQueue.apply"),
    exclude[IncompatibleMethTypeProblem]("monix.execution.internal.collection.queues.FromMessagePassingQueue#MPMC.this"),
    exclude[MissingTypesProblem]("monix.execution.internal.collection.queues.QueueDrain"),
    exclude[IncompatibleMethTypeProblem]("monix.execution.internal.collection.queues.FromCircularQueue#Java8MPSC.this"),
    exclude[IncompatibleMethTypeProblem]("monix.execution.internal.collection.queues.FromMessagePassingQueue#Java8MPSC.this"),
    exclude[IncompatibleMethTypeProblem]("monix.execution.internal.collection.queues.FromMessagePassingQueue#Java7.this"),
    exclude[IncompatibleMethTypeProblem]("monix.execution.internal.collection.queues.FromCircularQueue#Java8SPMC.this"),
    exclude[IncompatibleMethTypeProblem]("monix.reactive.observers.buffers.ConcurrentQueue#FromMessagePassingQueue.this"),
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
    exclude[DirectMissingMethodProblem]("monix.eval.Task#Async.this")
  )
}
