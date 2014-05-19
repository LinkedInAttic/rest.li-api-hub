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

import scala.collection.JavaConverters._
import com.linkedin.restsearch.Service
import play.api.libs.ws.WS
import play.api.Logger
import org.apache.commons.io.IOUtils
import com.linkedin.restli.internal.server.util.DataMapUtils
import com.linkedin.restli.internal.client.OptionsResponseDecoder
import scala.concurrent.duration.Duration
import java.util.concurrent.TimeUnit
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import com.linkedin.restli.internal.client.OptionsResponseDecoder
import com.linkedin.data.ByteString
import com.linkedin.r2.message.rest.RestResponseBuilder

/**
 * Crawls docgen endpoints on running rest.li servers, collecting the idl and data schemas for the resources they host.
 * @author jbetz
 *
 */
class UrlIdlFetcher extends IdlFetcher {
  override def fetch(service: Service): ScrapeResult = {
    val url = service.getUrl

    val future = WS.url(url).options().map { response =>
      response.status match {
        case 200 => {
          val restResponse = new RestResponseBuilder().setEntity(ByteString.copyString(response.body, "UTF-8")).setStatus(200).build()
          val optionsResponse = new OptionsResponseDecoder().decodeResponse(restResponse).getEntity
          Logger.info("Fetched " + url)
          SuccessfulScrape(
            optionsResponse.getResourceSchemas.values().asScala.head,
            optionsResponse.getDataSchemas.asScala.toMap
          )

        }
        case _ => {
          Logger.info("Error fetching " + url + " status:" + response.status)
          FailedScrape(new Exception("Error in OPTIONS request to " + url + " status: " + response.status))
        }
      }
    }
    future.onFailure {
      case t => FailedScrape(new Exception("Error in OPTIONS request to " + url))
    }

    Await.result(future, Duration(10, TimeUnit.SECONDS))

  }
}
