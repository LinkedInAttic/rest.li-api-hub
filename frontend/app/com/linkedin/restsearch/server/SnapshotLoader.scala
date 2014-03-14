package com.linkedin.restsearch.server

import com.linkedin.restsearch._
import com.linkedin.restsearch.dataloader._
import com.linkedin.restsearch.fetcher._
import com.linkedin.restsearch.search._
import java.util.concurrent._
import java.util.concurrent.atomic.AtomicReference
import play.{Logger, Play}
import scala.collection.JavaConversions._
import SnapshotLoader._
import com.linkedin.data.template.StringMap
import com.linkedin.restsearch.template.utils.Conversions._

object SnapshotLoader {
  val REFRESH_INTERVAL_MINUTES = 15
  val config = Play.application().configuration()
}

/**
 * Loads a dataset snapshot (d2, idl and data schema information) on construction and rebuilds a new snapshot on a regular interval.
 * @author jbetz
 *
 */
class SnapshotLoader() extends Runnable {

  private val dataLoadStrategy = config.getString("dataLoadStrategy", "zkCrawler")
  private val filesystemCacheDir = config.getString("filesystemCacheDir")
  private val mixinResourcePath = config.getString("mixinResourcePath")
  private val zkHost = config.getString("zkHost")
  private val zkPort = config.getInt("zkPort")
  private val fabric = config.getString("fabric")

  private lazy val crawlingZkLoader = new CrawlingDatasetLoaderProxy(new ZkDatasetLoader(zkHost, zkPort), new CrawlingIdlFetcher())
  //private lazy val crawlingExplorerLoader = new CrawlingDatasetLoaderProxy(new ExplorerDatasetLoader(), new CrawlingIdlFetcher())

  private val datasetLoader = dataLoadStrategy match {
    //case "explorerCrawler" => crawlingExplorerLoader
    case "zkCrawler" => crawlingZkLoader
    case "filesystemCached" => new FilesystemCachingDataLoaderProxy(crawlingZkLoader, filesystemCacheDir)
    //case "explorerFilesystemCached" => new FilesystemCachingDataLoaderProxy(crawlingExplorerLoader, filesystemCacheDir)
    case "resource" => new ResourceDataLoader(filesystemCacheDir)
  }

  private val loader = new MixinDataLoader(datasetLoader, mixinResourcePath)

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
      //   serviceErrors.put(key, "No server Urls announced to D2 zookeeper in " + fabric + " for resource.")
      //}
    }
  }
}