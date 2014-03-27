/*
   Copyright (c) 2014 LinkedIn Corp.

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/

package com.linkedin.restsearch.plugins

import play.{Logger, Plugin}
import com.linkedin.restsearch.snapshot.SnapshotLoader

class SnapshotInitPlugin(app: play.Application) extends Plugin {
  val snapshotLoader = new SnapshotLoader()

  override def onStart() {
    Logger.info("Snapshot plugin starting...")
    try
    {
      snapshotLoader.run(true)
    } catch {
      case e: Throwable => Logger.error("Error initializing snapshot", e)
    }
    Logger.info("Snapshot plugin started")
  }

  override def onStop() {
    Logger.info("Snapshot plugin stopped")
  }
}

object SnapshotInitPlugin {
  private val pluginUtil = new PluginUtil[SnapshotInitPlugin](classOf[SnapshotInitPlugin], "snapshotInit")
  def getInstance: SnapshotInitPlugin = pluginUtil.getInstance
  def isEnabled: Boolean = pluginUtil.isEnabled
}