import com.typesafe.tools.mima.core.ProblemFilters.exclude
import com.typesafe.tools.mima.core._

object MimaFilters {
  lazy val changesFor_3_0_1: Seq[ProblemFilter] = Seq(
    // Signature changes in internal classes
    exclude[DirectMissingMethodProblem]("monix.execution.internal.Trampoline.*"),
    exclude[DirectMissingMethodProblem]("monix.execution.schedulers.TrampolineExecutionContext#JVMNormalTrampoline.*"),
    exclude[DirectMissingMethodProblem]("monix.execution.schedulers.TrampolineExecutionContext#JVMOptimalTrampoline.*")
  )
  lazy val changesFor_3_0_0__RC5: Seq[ProblemFilter] = Seq(
    // Incompatible signatures should not cause linking problems.
    exclude[IncompatibleSignatureProblem]("monix.reactive.Observable.observableNonEmptyParallel"),
    exclude[IncompatibleSignatureProblem]("monix.tail.internal.IterantZipMap.par"),
    exclude[IncompatibleSignatureProblem]("monix.tail.Iterant.parZipMap"),
    exclude[IncompatibleSignatureProblem]("monix.tail.Iterant.parZip"),
    exclude[IncompatibleSignatureProblem]("monix.catnap.internal.ParallelApplicative.this"),
    exclude[IncompatibleSignatureProblem]("monix.catnap.internal.ParallelApplicative.apply")
  )

  lazy val changesFor_3_0_0__RC4: Seq[ProblemFilter] = Seq(
    // Internals — accidentally exposed
    exclude[MissingClassProblem]("monix.execution.internal.InterceptableRunnable$Wrapped"),
    exclude[MissingClassProblem]("monix.execution.internal.InterceptableRunnable$Delegate"),
    exclude[MissingClassProblem]("monix.execution.internal.InterceptableRunnable$"),
    exclude[MissingClassProblem]("monix.execution.internal.InterceptableRunnable")
  )

  lazy val changesFor_3_0_0__RC3: Seq[ProblemFilter] = Seq(
    // https://github.com/monix/monix/pull/971
    // Should not be a problem, but I'm not absolutely sure
    exclude[MissingTypesProblem]("monix.execution.exceptions.APIContractViolationException"),
    // Breaking changes for https://github.com/monix/monix/pull/960
    // Should only be a problem for Scala 2.11
    exclude[ReversedMissingMethodProblem]("monix.execution.Scheduler.features"),
    // Internals
    exclude[MissingClassProblem]("monix.eval.Task$DoOnFinish"),
    exclude[MissingClassProblem]("monix.eval.internal.TaskConnection$TrampolinedWithConn"),
    exclude[DirectMissingMethodProblem]("monix.eval.internal.TaskConnection.trampolineCallback"),
    exclude[FinalMethodProblem]("monix.execution.Callback#Base.run"),
    exclude[MissingClassProblem]("monix.eval.internal.TaskForkAndForget$"),
    exclude[MissingClassProblem]("monix.eval.internal.TaskForkAndForget")
  )
}
