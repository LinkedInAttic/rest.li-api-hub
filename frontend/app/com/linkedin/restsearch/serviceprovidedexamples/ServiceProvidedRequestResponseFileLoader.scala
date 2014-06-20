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

package com.linkedin.restsearch.serviceprovidedexamples

import com.linkedin.restsearch.{ServiceProvidedRequestResponseList, ServiceProvidedRequestResponse}
import com.linkedin.restli.internal.server.util.DataMapUtils
import play.api.Play
import play.api.Play.current

/**
 * Loads request-responses from a file.
 *
 * @author kparikh
 */
class ServiceProvidedRequestResponseFileLoader(filename: String) extends ServiceProvidedRequestResponseLoader {

  def loadExamples(): collection.Map[String, ServiceProvidedRequestResponse] = {
    // Source http://stackoverflow.com/a/16299543
    val serviceProvidedExamplesList = DataMapUtils.read(Play.application.resourceAsStream(filename).get,
      classOf[ServiceProvidedRequestResponseList])
    val result = collection.mutable.HashMap.empty[String, ServiceProvidedRequestResponse]
    for (i <- 0 to serviceProvidedExamplesList.getServiceProvidedRequestResponseList.size - 1) {
      val serviceProvidedExample = serviceProvidedExamplesList.getServiceProvidedRequestResponseList.get(i)
      result(serviceProvidedExample.getServiceIdentifier) = serviceProvidedExample
    }
    return result
  }

}
