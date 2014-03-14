package com.linkedin.restsearch.plugins

import play.{Logger, Plugin}
import com.linkedin.restsearch.server.SnapshotLoader

class SnapshotInitPlugin(app: play.Application) extends Plugin {
  val snapshotLoader = new SnapshotLoader()

  override def onStart() {
    Logger.info("Rest-search zookeeper snapshot plugin starting...")
    try
    {
      snapshotLoader.run(true)
    } catch {
      case e: Throwable => Logger.error("Error initializing zookeeper snapshot", e)
    }
    Logger.info("Rest-search zookeeper snapshot plugin started")
  }

  override def onStop() {
    Logger.info("Rest-search zookeeper snapshot plugin stopped")
  }
}

object SnapshotInitPlugin {
  private val pluginUtil = new PluginUtil[SnapshotInitPlugin](classOf[SnapshotInitPlugin], "zkSnapshotInit")
  def getInstance: SnapshotInitPlugin = pluginUtil.getInstance
  def isEnabled: Boolean = pluginUtil.isEnabled
}