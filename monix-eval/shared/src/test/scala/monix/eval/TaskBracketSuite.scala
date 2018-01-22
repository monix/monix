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

package monix.eval

import java.io._

private[eval] object TaskBracketSuite extends BaseTestSuite {

  def readFile(file: File): Task[String] = {
    val acquire = Task.eval(new BufferedReader(
      new InputStreamReader(new FileInputStream(file), "utf-8"))
    )

    acquire.bracket { in =>
      // Usage part
      Task.eval {
        // Yes, not FP; side-effects are suspended though
        var line: String = null
        val buff = new StringBuilder()
        do {
          line = in.readLine()
          if (line != null) buff.append(line)
        } while (line != null)
        buff.toString()
      }
    } { in =>
      // The release part
      Task.eval(in.close())
    }
  }


  test("works") { implicit sc =>

    val file = Task(new File(""))

    file.bracket { a =>
      Task.eval(a.getAbsoluteFile)
    } { file =>
      Task.eval(file.delete())
    }
  }
}
