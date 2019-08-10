import com.typesafe.tools.mima.core.ProblemFilters.exclude
import com.typesafe.tools.mima.core._

object MimaFilters {
  lazy val changesFor_3_0_0: Seq[ProblemFilter] = Seq(
    // https://github.com/monix/monix/pull/971
    // Should not be a problem, but I'm not absolutely sure
    exclude[MissingTypesProblem]("monix.execution.exceptions.APIContractViolationException"),
    // Breaking changes for https://github.com/monix/monix/pull/960
    exclude[ReversedMissingMethodProblem]("monix.execution.Scheduler.features"),
    // Local changes :-(
    exclude[IncompatibleResultTypeProblem]("monix.execution.misc.Local.defaultContext"),
    exclude[IncompatibleResultTypeProblem]("monix.execution.misc.Local.defaultContext"),
    exclude[IncompatibleResultTypeProblem]("monix.execution.misc.Local#Context.mkIsolated"),
    exclude[DirectMissingMethodProblem]("monix.execution.misc.Local.bindClear"),
    exclude[DirectMissingMethodProblem]("monix.execution.misc.Local.bind"),
    exclude[MissingClassProblem]("monix.execution.misc.Local$Macros"),
    // Internals
    exclude[MissingClassProblem]("monix.eval.Task$DoOnFinish"),
    exclude[MissingClassProblem]("monix.eval.internal.TaskConnection$TrampolinedWithConn"),
    exclude[DirectMissingMethodProblem]("monix.eval.internal.TaskConnection.trampolineCallback"),
    exclude[FinalMethodProblem]("monix.execution.Callback#Base.run"),
    exclude[MissingClassProblem]("monix.eval.internal.TaskForkAndForget$"),
    exclude[MissingClassProblem]("monix.eval.internal.TaskForkAndForget")
  )
}
