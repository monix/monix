/*
 * Copyright (c) 2014-2018 by The Monix Project Developers.
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

package monix.reactive

import java.io.File
import java.nio.file.{Path, Paths}

import monix.eval.{Task, TaskApp}

import scala.concurrent.duration._

object Playground extends TaskApp {
  override def runc: Task[Unit] = {
    def watchingFolder(file: File): Observable[Path] = {
      Observable.fromIterable(file.listFiles().map(f => Paths.get(f.getPath)))
    }

    val file = new File("/Users/alex")
    val obs = watchingFolder(file).onErrorHandleWith { err =>
      watchingFolder(file).delaySubscription(10.seconds)
    }

    obs.dump("O").completedL
  }
}
