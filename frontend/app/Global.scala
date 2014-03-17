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

import play.api._

object Global extends GlobalSettings {

  override def onStart(app: Application) {
    Logger.info("Application has started")
  }

  override def onStop(app: Application) {
    Logger.info("Application shutdown...")
  }

}
/*object Global {

  // Handle the trailing "/"
  override def onRouteRequest(request: RequestHeader) = super.onRouteRequest(request).orElse {
    Option(request.path).filter(_.endsWith("/"))
      .flatMap( p => super.onRouteRequest(
        request.copy(path = p.dropRight(1),
          uri = p.dropRight(1) +  request.uri.stripPrefix(p))
        )
      )
  }
}*/