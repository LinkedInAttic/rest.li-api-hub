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