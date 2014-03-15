package com.linkedin.restsearch.fetcher

import scala.collection.JavaConversions._
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

//import com.linkedin.playplugins.restliplugin.s.client.RestliPlugin

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
          val in = IOUtils.toInputStream(response.body)
          val dataMap = DataMapUtils.readMap(in)
          val optionsResponse = new OptionsResponseDecoder().wrapResponse(dataMap)
          Logger.info("Fetched " + url)
          SuccessfulScrape(
            optionsResponse.getResourceSchemas.values().head,
            optionsResponse.getDataSchemas.toMap
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
