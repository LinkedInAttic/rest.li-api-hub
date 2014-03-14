package com.linkedin.restsearch.plugins

import play.api.Play
import play.api.Play.current

class PluginUtil[T](clazz: Class[T], name: String, enabledByDefault: Boolean = true) {

  private val enabledKey = name + ".plugin.enabled"
  private val fullName = name + "-plugin"

  def this(clazz: Class[T], name: String) = this(clazz, name, true)

  def getInstance: T = {
    if (isEnabled) {
      current.plugin(clazz).getOrElse(
        throw new IllegalStateException("Could not find %s in this application. You need to add %s (or a subclass of it) to conf/play.plugins"
          .format(fullName, clazz.getName))
      )
    } else {
      throw new IllegalStateException("%s is not enabled. If you wish to enable it, set %s to true in your config"
        .format(fullName, enabledKey))
    }
  }

  def getInstanceOpt: Option[T] = current.plugin(clazz)

  def isEnabled: Boolean = Play.configuration.getBoolean(enabledKey).getOrElse(enabledByDefault)
}