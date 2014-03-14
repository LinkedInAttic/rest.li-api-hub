package com.linkedin.restsearch.dataloader

import com.linkedin.data.template.{StringMap, StringArray}
import com.linkedin.restsearch._
import org.json.JSONObject
import scala.collection.JavaConversions._
import org.apache.zookeeper.Watcher.Event.KeeperState
import org.apache.zookeeper.{WatchedEvent, ZooKeeper, Watcher}
import play.api.Logger
import com.linkedin.restsearch.dataloader.ZkDatasetLoader._
import java.util.concurrent.CountDownLatch
import com.linkedin.restliexplorer.{D2Uri, D2UriArray, D2Cluster}
import java.net.URI
import org.apache.zookeeper.data.Stat

object ZkDatasetLoader {
  val ZK_CONNECT_TIMEOUT_MINUTES = 3
}


case class Uri(val uri: String, val clusterName: String)

/**
 * Loads cluster, service and uri data from zookeeper.
 * @author jbetz
 *
 */
class ZkDatasetLoader(zkHost: String, zkPort: Int) extends DatasetLoader {

  override def loadDataset(isStartup: Boolean): Dataset =  {
    val connectedSignal = new CountDownLatch(1);

    class ZkWatcher extends Watcher {
      def process(event: WatchedEvent) {
        if (event.getState() == KeeperState.SyncConnected) {
          connectedSignal.countDown();
        }
      }
    }

    val zkClient = new ZooKeeper(zkHost + ":" + zkPort, ZK_CONNECT_TIMEOUT_MINUTES*60, new ZkWatcher)
    connectedSignal.await()
    try {
      loadDataFromZookeeper(zkClient)
    } finally {
      zkClient.close()
    }
  }

  private def loadDataFromZookeeper(zkClient: ZooKeeper) = {
    Logger.info("Loading dataset from zookeeper...")
    val dataset = new Dataset()

    dataset.setServiceErrors(new StringMap())

    val servicesJson = loadChildrenAsStrings(zkClient, "/d2/services")
    val urisJson = loadGrandchildrenAsStrings(zkClient, "/d2/uris")

    val services = deserializeService(servicesJson)
    val uris = deserializeUris(urisJson)

    val pairs = for {
      service <- services.values
      clusters = service.getClusters()
      cluster <- asScalaBuffer(clusters)
    } yield (cluster, service)

    val clusters = for ((cluster, pairs) <- pairs.groupBy(_._1))
      yield {
        val services = pairs map {
          case(_, services) => services
        }
        val copyOfCluster = cluster.clone().asInstanceOf[Cluster]
        copyOfCluster.setServices(new ServiceArray(asJavaCollection(services)))
        (copyOfCluster.getName, copyOfCluster)
      }

    joinUrisOntoClusters(clusters, uris)

    dataset.setClusters(new ClusterMap(clusters))

    Logger.info("...zookeeper dataset loaded.")
    dataset
  }

  /**
   * Add D2Cluster information about URIs from zookeeper "uris" data
   * @param clusters
   * @param uris
   */
  private def joinUrisOntoClusters(clusters: Map[String, Cluster], uris: Map[String, Uri]) {
    val serversByClusterName = uris.values
      .groupBy(_.clusterName)
      .map { case (key, values) =>
        (key, new StringArray(asJavaCollection(values map (_.uri))))
      }

    for (cluster <- clusters.values;
         servers = serversByClusterName.get(cluster.getName());
         if (servers.isDefined);
         uris <- servers;
         d2Uris = uris map toD2Uri
      ) {
      if(!cluster.hasD2Cluster) cluster.setD2Cluster(new D2Cluster())
      val d2Cluster = cluster.getD2Cluster
      if(!d2Cluster.hasUris) d2Cluster.setUris(new D2UriArray())
      d2Cluster.getUris.addAll(d2Uris)
    }
  }

  private def toD2Uri(uri: String) = {
    val d2Uri = new D2Uri()
    d2Uri.setURI(uri)
    d2Uri
  }

  case class JsonAndStats(jsonStr: String, stat: Stat)

  private def loadChildrenAsStrings(zkClient: ZooKeeper, path: String): Map[String, JsonAndStats] = {
    val keyJsonStrPairs = for {
      zkKey <- asScalaBuffer(zkClient.getChildren(path, false))
      data = zkClient.getData(path + "/" + zkKey, false, null)
      if (data != null)
      stat = zkClient.exists(path + "/" + zkKey, false)
      jsonStr = new String(data, "UTF-8")
    } yield (zkKey, JsonAndStats(jsonStr, stat))
    keyJsonStrPairs.toMap
  }

  private def loadGrandchildrenAsStrings(zkClient: ZooKeeper, path: String): Map[String, String] = {
    val grandparentZkKeys = zkClient.getChildren(path, false)
    val results = for {
      grandparentZkKey <- asScalaBuffer(grandparentZkKeys)
      parentZkKeys = zkClient.getChildren(path + "/" + grandparentZkKey , false)
      parentZkKey <- asScalaBuffer(parentZkKeys)
      data = try {
        zkClient.getData(path + "/" + grandparentZkKey +  "/" + parentZkKey, false, null)
      } catch {
        case e: Throwable => {
          Logger.error("failed to load zookeeper data at path " + path, e)
          null
        }
      }
      if (data != null)
      jsonStr = new String(data, "UTF-8")
    } yield (grandparentZkKey + "/" + parentZkKey, jsonStr)
    results.toMap
  }

  // "/d2/services/janusCapContentService"
  //
  // {"path":"/contentServiceRest",
  // "serviceName":"janusCapContentService",
  // "clusterName":"CapServices",
  // "loadBalancerStrategyName":"degrader",
  // "loadBalancerStrategyList":["degraderV2","degrader"],
  // "loadBalancerStrategyProperties":
  //   {"maxClusterLatencyWithoutDegrading":"500",
  //    "defaultSuccessfulTransmissionWeight":"1.0",
  //    "pointsPerWeight":"100",
  //    "updateIntervalMs":"5000"}}
  private def deserializeService(servicesJson: Map[String, JsonAndStats]): Map[String, Service] = {
    for ((serviceName, JsonAndStats(serviceJson, stat)) <- servicesJson) yield {
      val service = new JSONObject(serviceJson)
      val serviceResult = new Service()
      serviceResult.setKey(serviceName)
      val path = service.getString("path")
      serviceResult.setPath(path)
      if (path.equals("/contentServiceRest")) {
        serviceResult.setProtocol(Protocol.CONTENT_SERVICE)
      } else {
        serviceResult.setProtocol(Protocol.REST)
      }

      if(stat != null) {
        serviceResult.setCreatedAt(stat.getCtime)
        serviceResult.setModifiedAt(stat.getMtime)
      }

      if(service.has("serviceMetadataProperties")) {
        val serviceMetadataProperties = service.getJSONObject("serviceMetadataProperties");
        if(serviceMetadataProperties.has("isDefaultService")) {
          val isDefaultService = serviceMetadataProperties.getBoolean("isDefaultService");
          serviceResult.setDefaultService(isDefaultService);
        }
      } else {
        serviceResult.setDefaultService(true);
      }

      val cluster = new Cluster()
      cluster.setName(service.getString("clusterName"))
      val clusters = new ClusterArray()
      clusters.add(cluster)
      serviceResult.setClusters(clusters)
      //val serversPlaceholder = new StringArray()
      //cluster.setServers(serversPlaceholder)
      (serviceName, serviceResult)
    }
  }

  // "/d2/uris/CapServices/ephemoral--101da05e-a440-408e-972c-a8c602629e0c-0000003456"
  //
  // {"weights":
  //   {"http://esv4-app100.stg.linkedin.com:10059/cap2-ds/resources":1.0},
  //   "clusterName":"CapServices",
  //   "partitionDesc":
  //     {"http://esv4-app100.stg.linkedin.com:10059/cap2-ds/resources":{"0":{"weight":1.0}}}
  // }
  //
  private def deserializeUris(urisJson: Map[String, String]): Map[String, Uri] = {
    val keyUriPairs = for {
      (key, uriJson) <- urisJson
      uriObject = new JSONObject(uriJson)
      clusterName = uriObject.getString("clusterName")
      if(uriObject.has("weights"))
      weights = uriObject.getJSONObject("weights")
      uris = weights.keys().asInstanceOf[java.util.Iterator[String]]
      uri <- uris
    } yield (key, new Uri(uri, clusterName))
    keyUriPairs.toMap
  }

  // "/d2/clusters/CapServices"
  //
  // {"properties":
  //    {"requestTimeout":"10000","getTimeout":"10000"},
  //  "banned":[],
  //  "clusterName":"CapServices",
  //  "prioritizedSchemes":["http"],
  //  "partitionProperties":{"partitionType":"NONE"}
  // }
  private def deserializeClusters(clustersJson: Map[String, String]): Map[String, Cluster] = {
    for {
      (clusterName, clusterJson) <- clustersJson
    } yield {
      val cluster = new JSONObject(clusterJson)
      val clusterResult = new Cluster()
      clusterResult.setName(clusterName)
      (clusterName, clusterResult)
    }
  }
}
