/*
 * Copyright (c) 2014-2017 by The Monix Project Developers.
 * See the project homepage at: https://monix.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import com.typesafe.tools.mima.core._
import com.typesafe.tools.mima.core.ProblemFilters.exclude

/** Backwards compatibility is preserved for public APIs only, not for
  * package-private APIs.
  *
  * These filters out package-private binary incompatible changes.
  */
object MimaFilters {
  /** `monix-execution` <= 2.2.4 */
  lazy val executionChangesFor_2_2_4 = Seq(
    exclude[IncompatibleMethTypeProblem]("monix.execution.cancelables.CompositeCancelable.this")
  )

  /** `monix-eval` <= 2.2.4 */
  lazy val evalChangesFor_2_2_4 = Seq(
    // Related to issue: https://github.com/monix/monix/issues/313
    exclude[DirectMissingMethodProblem]("monix.eval.internal.TaskFromFuture.apply")
  )

  /** `monix-eval` 2.3.0 */
  lazy val evalChangesFor_2_3_0 = Seq(
    exclude[DirectMissingMethodProblem]("monix.eval.Task.internalStartTrampolineRunLoop"),
    exclude[MissingClassProblem]("monix.eval.Coeval$BindSuspend"),
    exclude[MissingClassProblem]("monix.eval.Coeval$BindSuspend$"),
    exclude[DirectMissingMethodProblem]("monix.eval.Task#MemoizeSuspend.execute"),
    exclude[MissingTypesProblem]("monix.eval.Task$Context$"),
    exclude[DirectMissingMethodProblem]("monix.eval.Task#Eval.f"),
    exclude[DirectMissingMethodProblem]("monix.eval.Coeval.trampoline")
  )

  /** `monix-reactive` <= 2.2.4 */
  lazy val reactiveChangesFor_2_2_4 = Seq(
    exclude[MissingTypesProblem]("monix.reactive.internal.operators.ConcatMapObservable$FlatMapState$WaitComplete$"),
    exclude[DirectMissingMethodProblem]("monix.reactive.internal.operators.ConcatMapObservable#FlatMapState#WaitComplete.apply"),
    exclude[DirectMissingMethodProblem]("monix.reactive.internal.operators.ConcatMapObservable#FlatMapState#WaitComplete.copy"),
    exclude[DirectMissingMethodProblem]("monix.reactive.internal.operators.ConcatMapObservable#FlatMapState#WaitComplete.this"),
    exclude[MissingTypesProblem]("monix.reactive.internal.operators.MapTaskObservable$FlatMapState$WaitComplete$"),
    exclude[DirectMissingMethodProblem]("monix.reactive.internal.operators.MapTaskObservable#FlatMapState#WaitComplete.apply"),
    exclude[DirectMissingMethodProblem]("monix.reactive.internal.operators.MapTaskObservable#FlatMapState#WaitComplete.copy"),
    exclude[DirectMissingMethodProblem]("monix.reactive.internal.operators.MapTaskObservable#FlatMapState#WaitComplete.this"),
    exclude[MissingClassProblem]("monix.reactive.internal.operators.MapAsyncParallelObservable$MapAsyncParallelSubscriber")
  )

  /** `monix-reactive` 2.3.0 */
  lazy val reactiveChangesFor_2_3_0 = Seq(
    exclude[DirectMissingMethodProblem]("monix.reactive.observers.buffers.AbstractBackPressuredBufferedSubscriber.secondaryQueue"),
    exclude[DirectMissingMethodProblem]("monix.reactive.observers.buffers.AbstractBackPressuredBufferedSubscriber.primaryQueue"),
    exclude[DirectMissingMethodProblem]("monix.reactive.observers.buffers.ConcurrentQueue.unbounded"),
    exclude[MissingClassProblem]("monix.reactive.Consumer$CompleteConsumer$"),
    exclude[MissingClassProblem]("monix.reactive.Consumer$HeadOptionConsumer"),
    exclude[MissingClassProblem]("monix.reactive.Consumer$CancelledConsumer$"),
    exclude[MissingClassProblem]("monix.reactive.Consumer$LoadBalanceConsumer$"),
    exclude[MissingClassProblem]("monix.reactive.Consumer$FromObserverConsumer"),
    exclude[MissingClassProblem]("monix.reactive.Consumer$LoadBalanceConsumer$IndexedSubscriber"),
    exclude[MissingClassProblem]("monix.reactive.Consumer$LoadBalanceConsumer"),
    exclude[MissingClassProblem]("monix.reactive.Consumer$ForeachConsumer"),
    exclude[MissingClassProblem]("monix.reactive.Consumer$HeadConsumer"),
    exclude[MissingClassProblem]("monix.reactive.Consumer$MapAsyncConsumer"),
    exclude[MissingClassProblem]("monix.reactive.Consumer$FoldLeftAsyncConsumer"),
    exclude[MissingClassProblem]("monix.reactive.Consumer$LoadBalanceConsumer$State"),
    exclude[MissingClassProblem]("monix.reactive.Consumer$ForeachAsyncConsumer"),
    exclude[MissingClassProblem]("monix.reactive.Consumer$FirstNotificationConsumer"),
    exclude[MissingClassProblem]("monix.reactive.Consumer$MapConsumer"),
    exclude[MissingClassProblem]("monix.reactive.Consumer$LoadBalanceConsumer$Available"),
    exclude[MissingClassProblem]("monix.reactive.Consumer$LoadBalanceConsumer$Waiting"),
    exclude[MissingClassProblem]("monix.reactive.Consumer$LoadBalanceConsumer$Available$"),
    exclude[MissingClassProblem]("monix.reactive.Consumer$LoadBalanceConsumer$Waiting$"),
    exclude[MissingClassProblem]("monix.reactive.Consumer$FoldLeftConsumer"),
    exclude[MissingClassProblem]("monix.reactive.Consumer$LoadBalanceConsumer$IndexedSubscriber$"),
    exclude[MissingClassProblem]("monix.reactive.Consumer$ContraMapConsumer"),
    exclude[MissingClassProblem]("monix.reactive.Consumer$CreateConsumer"),
    exclude[MissingClassProblem]("monix.reactive.Consumer$LoadBalanceConsumer$AsyncQueue"),
    exclude[MissingClassProblem]("monix.reactive.Consumer$RaiseErrorConsumer"),
    exclude[DirectMissingMethodProblem]("monix.reactive.internal.builders.ReactiveObservable.this"),
    exclude[MissingTypesProblem]("monix.reactive.internal.rstreams.SubscriberAsReactiveSubscriber"),
    exclude[DirectMissingMethodProblem]("monix.reactive.internal.rstreams.SubscriberAsReactiveSubscriber.onError"),
    exclude[DirectMissingMethodProblem]("monix.reactive.internal.rstreams.SubscriberAsReactiveSubscriber.onSubscribe"),
    exclude[DirectMissingMethodProblem]("monix.reactive.internal.rstreams.SubscriberAsReactiveSubscriber.onComplete"),
    exclude[DirectMissingMethodProblem]("monix.reactive.internal.rstreams.SubscriberAsReactiveSubscriber.onNext"),
    exclude[DirectMissingMethodProblem]("monix.reactive.internal.rstreams.SubscriberAsReactiveSubscriber.this"),
    exclude[MissingClassProblem]("monix.reactive.internal.rstreams.SyncSubscriberAsReactiveSubscriber$")
  )

  lazy val evalChangesFor_3_0_0 = Seq(
    exclude[MissingClassProblem]("monix.eval.TaskInstances$TypeClassInstances"),
    exclude[IncompatibleResultTypeProblem]("monix.eval.Task.nondeterminism"),
    exclude[IncompatibleResultTypeProblem]("monix.eval.TaskInstances.nondeterminism"),
    exclude[ReversedMissingMethodProblem]("monix.eval.TaskInstances.catsAsync"),
    exclude[InheritedNewAbstractMethodProblem]("monix.eval.TaskInstances1.catsEffect"),
    exclude[MissingClassProblem]("monix.eval.Coeval$TypeClassInstances"),
    exclude[DirectMissingMethodProblem]("monix.eval.Task.typeClassInstances"),
    exclude[DirectMissingMethodProblem]("monix.eval.Coeval.typeClassInstances"),

    exclude[IncompatibleResultTypeProblem]("monix.eval.Coeval#Once.runAttempt"),
    exclude[MissingClassProblem]("monix.eval.Coeval$Attempt"),
    exclude[IncompatibleResultTypeProblem]("monix.eval.Coeval#Always.runAttempt"),
    exclude[MissingTypesProblem]("monix.eval.Coeval$Error"),
    exclude[IncompatibleResultTypeProblem]("monix.eval.Coeval#Error.runAttempt"),
    exclude[IncompatibleResultTypeProblem]("monix.eval.Coeval.runAttempt"),
    exclude[MissingTypesProblem]("monix.eval.Coeval$Now"),
    exclude[IncompatibleResultTypeProblem]("monix.eval.Coeval#Now.runAttempt"),
    exclude[IncompatibleResultTypeProblem]("monix.eval.internal.CoevalRunLoop.start"),
    exclude[MissingClassProblem]("monix.eval.Coeval$Attempt$")
  )

  lazy val reactiveChangesFor_3_0_0 = Seq(
    exclude[DirectMissingMethodProblem]("monix.reactive.Observable.typeClassInstances"),
    exclude[MissingClassProblem]("monix.reactive.Observable$TypeClassInstances")
  )
}
