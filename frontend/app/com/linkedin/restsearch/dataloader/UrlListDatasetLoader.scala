package com.linkedin.restsearch.dataloader

import play.{Logger, Play}
import com.linkedin.restsearch._
import com.linkedin.restliexplorer.D2Service

class UrlListDatasetLoader(urlList: String) extends DatasetLoader {
  def loadDataset(isStartup: Boolean): Dataset = {
    val dataset = new Dataset()
    val clusters = new ClusterMap()
    val cluster = new Cluster()
    val urls = urlList.split(",")
    val services = new ServiceArray()
    urls.foreach { url =>
      val service = new Service()
      // TODO: set the url for later scraping
      service.setUrl(url)
      val path = url.split("/").last
      service.setKey(path)
      service.setPath(path)
      service.setProtocol(Protocol.REST)
      services.add(service)
    }
    cluster.setServices(services)
    cluster.setName("AllResources")
    clusters.put("AllResources", cluster)
    dataset.setClusters(clusters)

    dataset
  }
}
