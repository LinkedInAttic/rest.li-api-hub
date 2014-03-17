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

package com.linkedin.restsearch.dataloader

import com.linkedin.data.template.StringMap
import com.linkedin.restsearch.{Dataset,Service}
import com.linkedin.restsearch.fetcher.{SuccessfulScrape, FailedScrape, IdlFetcher}
import scala.collection.mutable
import scala.collection.JavaConversions._
import play.api.Logger
import com.linkedin.restsearch.template.utils.Conversions._

/**
 * Wrapper for a data loader (usually should wrap ZkDatasetLoader) and
 * crawls services for idl (.restspec.json) and data schemas (.pdsc) after d2 data had been loaded
 * from the wrapped loader.
 *
 * @author jbetz
 *
 *
 */
class CrawlingDatasetLoaderProxy(underlying: DatasetLoader, crawler: IdlFetcher) extends DatasetLoader {

  override def loadDataset(isStartup: Boolean): Dataset =  {
    val dataset = underlying.loadDataset(isStartup)
    if(!dataset.hasServiceErrors) {
      dataset.setServiceErrors(new StringMap())
    }

    crawl(dataset.servicesMap, mapAsScalaMap(dataset.getServiceErrors))
    dataset
  }

  private def crawl(services: Map[String, Service], errors: mutable.Map[String, String]) {
    Logger.info("Crawling service docgen for idl...")

    services.values filter { s => s.hasKey && s.hasPath } foreach { service =>
      crawler.fetch(service) match {
        case (scrape: SuccessfulScrape) => {
          service.setResourceSchema(scrape.resourceSchema)
          val dataSchemas = scrape.dataSchemas.mapValues(_.toString)
          service.setModels(new StringMap(mapAsJavaMap(dataSchemas)))
        }
        case (failure: FailedScrape) => {
          if(failure.ex.getMessage == null) {
            Logger.error("Failed to crawl service " + service.getKey() + " exception: ", failure.ex)
            errors.put(service.getKey, failure.ex.getStackTraceString)
          } else {
            Logger.error("Failed to crawl service " + service.getKey() + " reason: " + failure.toString)
            errors.put(service.getKey, failure.toString)
          }
        }
      }
    }
  }
}
