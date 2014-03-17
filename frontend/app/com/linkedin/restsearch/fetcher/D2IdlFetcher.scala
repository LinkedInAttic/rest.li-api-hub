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

package com.linkedin.restsearch.fetcher

import scala.collection.JavaConversions._
import play.api.Logger
import com.linkedin.restsearch.Service

//import com.linkedin.playplugins.restliplugin.s.client.RestliPlugin
import com.linkedin.restli.client.{OptionsRequestBuilder, RestliRequestOptions, OptionsRequest, RestLiResponseException}
import java.net.URI
import com.linkedin.restli.common.OptionsResponse
import java.util.Collections

/**
 * Crawls docgen endpoints on running rest.li servers, collecting the idl and data schemas for the resources they host.
 * @author jbetz
 *
 */
class D2IdlFetcher extends IdlFetcher {
  override def fetch(service: Service): ScrapeResult = {
    val serviceName = service.getKey
    val servicePath = service.getPath

    val displayUrl = "d2://" + serviceName

    FailedScrape(new Exception("CrawlingIdlFetcher is disabled."))
    /*try {
      val request = new OptionsRequestBuilder(new URI(serviceName).toString, RestliRequestOptions.DEFAULT_OPTIONS).build()
      val optionsResponse: OptionsResponse = RestliPlugin.getInstance.getRestClient.sendRequest(request).getResponseEntity
      Logger.info("Fetched " + displayUrl)
      SuccessfulScrape(
        optionsResponse.getResourceSchemas.values().filter(_.getPath() == servicePath).head,
        optionsResponse.getDataSchemas.toMap
      )
    } catch {
      case remote: RestLiResponseException => {
        Logger.info("Error fetching " + displayUrl + " status:" + remote.getStatus() + " message:" + remote.getMessage)
        FailedScrape(new Exception("Error in OPTIONS request to " + displayUrl + ".  Resulting HTTP status was " + remote.getStatus + " message:" + remote.getMessage))
      }
      case e: Throwable => {
        Logger.info("Error fetching " + displayUrl + " status:" + e.getMessage())
        FailedScrape(new Exception("Error in OPTIONS request to " + displayUrl + ". message:" + e.getMessage))
      }
    }*/
  }
}
