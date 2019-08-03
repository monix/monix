import com.typesafe.tools.mima.core.ProblemFilters.exclude
import com.typesafe.tools.mima.core._

object MimaFilters {
  lazy val changesFor_3_0_0: Seq[ProblemFilter] = Seq(
    // Breaking changes for https://github.com/monix/monix/pull/960
    exclude[ReversedMissingMethodProblem]("monix.execution.Scheduler.features"),
    // Local changes :-(
    exclude[IncompatibleResultTypeProblem]("monix.execution.misc.Local.defaultContext"),
    exclude[IncompatibleResultTypeProblem]("monix.execution.misc.Local.defaultContext"),
    exclude[IncompatibleResultTypeProblem]("monix.execution.misc.Local#Context.mkIsolated"),
    // Internals
    exclude[MissingClassProblem]("monix.eval.Task$DoOnFinish"),
    exclude[MissingClassProblem]("monix.eval.internal.TaskConnection$TrampolinedWithConn"),
    exclude[DirectMissingMethodProblem]("monix.eval.internal.TaskConnection.trampolineCallback"),
    exclude[FinalMethodProblem]("monix.execution.Callback#Base.run")
  )
}
