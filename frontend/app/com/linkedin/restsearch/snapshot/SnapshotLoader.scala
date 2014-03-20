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

package com.linkedin.restsearch.snapshot

import com.linkedin.restsearch._
import com.linkedin.restsearch.dataloader._
import com.linkedin.restsearch.fetcher._
import com.linkedin.restsearch.search._
import java.util.concurrent._
import java.util.concurrent.atomic.AtomicReference
import play.api.{Logger, Play}
import play.api.Play.current
import scala.collection.JavaConversions._
import SnapshotLoader._
import com.linkedin.data.template.StringMap
import com.linkedin.restsearch.template.utils.Conversions._

object SnapshotLoader {
  val REFRESH_INTERVAL_MINUTES = 15
  val config = Play.application.configuration
}

/**
 * Loads a dataset snapshot (d2, idl and data schema information) on construction and rebuilds a new snapshot on a regular interval.
 * @author jbetz
 *
 */
class SnapshotLoader() extends Runnable {

  private val dataLoadStrategy = config.getString("dataLoadStrategy").getOrElse("crawler")
  private val filesystemCacheDir = config.getString("filesystemCacheDir")

  private val fabric = config.getString("fabric")
  private val loaderClass = config.getString("loaderClass").getOrElse(classOf[UrlListDatasetLoader].getName)
  private val fetcherClass = config.getString("fetcherClass").getOrElse(classOf[UrlIdlFetcher].getName)

  private lazy val loaderInstance = Class.forName(loaderClass).newInstance().asInstanceOf[DatasetLoader]
  private lazy val fetcherInstance = Class.forName(fetcherClass).newInstance().asInstanceOf[IdlFetcher]

  private lazy val crawlingLoader = new CrawlingDatasetLoaderProxy(loaderInstance, fetcherInstance)

  private val datasetLoader = dataLoadStrategy match {
    case "crawler" => crawlingLoader
    case "crawlerFilesystemCached" => {
      if(filesystemCacheDir.isEmpty) {
        throw new IllegalArgumentException("filesystemCacheDir configuration property must be set when using 'crawlerFilesystemCached' dataLoadStrategy")
      } else {
        new FilesystemCachingDataLoaderProxy(crawlingLoader, filesystemCacheDir.get)
      }
    }
    case "resource" => {
      if(filesystemCacheDir.isEmpty) {
        throw new IllegalArgumentException("filesystemCacheDir configuration property must be set to a relative project path when using 'resource' dataLoadStrategy")
      } else {
        new ResourceDataLoader(filesystemCacheDir.get)
      }
    }
  }

  private val loader = datasetLoader

  // TODO: use play scheduler: http://www.playframework.com/documentation/2.1.0/ScalaAkka
  private val scheduledReloader : ScheduledExecutorService = new ScheduledThreadPoolExecutor(1)
  private val snapshot = new AtomicReference[Snapshot]

  def currentSnapshot = snapshot.get

  def run() : Unit = run(false)

  def run(isStartup: Boolean) : Unit = {
    try {
      snapshot.set(buildSnapshot(isStartup))
    }
    catch {
      case e: Throwable => Logger.error("Error building new snapshot", e)
    }
    finally {
      // Prevent this task from stalling due to RuntimeExceptions.
      if(isStartup && snapshot.get().clusters.size == 0) {
        Logger.info("Startup: No data.  Scheduling immediate data reload.")
        scheduledReloader.submit(SnapshotLoader.this)
      } else {
        Logger.info("Scheduling next data reload in " + REFRESH_INTERVAL_MINUTES + " " + TimeUnit.MINUTES)
        scheduledReloader.schedule(SnapshotLoader.this, REFRESH_INTERVAL_MINUTES, TimeUnit.MINUTES)
      }
    }
  }

  private def buildSnapshot(isStartup: Boolean) : Snapshot = {
    val loadedDataset = loader.loadDataset(isStartup)
    filterEmptyServicesAndClusters(loadedDataset)

    val searchIndex = new LuceneSearchIndex()
    searchIndex.index(loadedDataset)
    new Snapshot(loadedDataset, System.currentTimeMillis(), searchIndex)
  }

  private def filterEmptyServicesAndClusters(loadedDataset: Dataset) : Unit = {
    if(!loadedDataset.hasServiceErrors) {
      loadedDataset.setServiceErrors(new StringMap())
    }

    mapAsScalaMap(loadedDataset.getClusters).values foreach { cluster =>
      val (services, errantServices) = cluster.getServices.partition { service =>
        service.getProtocol() == Protocol.CONTENT_SERVICE ||
          (service.hasResourceSchema() && service.getResourceSchema().hasContents)
      }

      trackServiceErrors(errantServices, loadedDataset.getServiceErrors)

      val serviceArray = new ServiceArray()
      serviceArray.addAll(services)
      cluster.setServices(serviceArray)
    }
  }

  private def trackServiceErrors(errantServices: Seq[Service], serviceErrors: StringMap) = {
    errantServices foreach { case errantService =>
      val key = errantService.getKey
      if(!serviceErrors.contains(key)) {
        if(!errantService.hasResourceSchema()) {
          serviceErrors.put(key,
            "HTTP OPTIONS (curli -X OPTIONS d2://"+key+") request to resource URI in " + fabric + " responded with JSON with missing required " +
              "'resourceSchema'.")
        } else if (!errantService.getResourceSchema().hasContents) {
          serviceErrors.put(key,
            "HTTP OPTIONS (curli -X OPTIONS ...) request to resource URI in " + fabric + " responded with JSON with empty 'resourceSchema' " +
              "(it contains no collections, singles, associations or actionSets OR it does but they " +
              "contain no methods, finders or actions).")
        }
      }
      //if(errantService.getClusters.forall(_.getServers.isEmpty)) {
      //   serviceErrors.put(key, "No snapshot Urls announced to D2 zookeeper in " + fabric + " for resource.")
      //}
    }
  }
}