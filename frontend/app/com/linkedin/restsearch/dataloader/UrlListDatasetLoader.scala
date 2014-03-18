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

import play.api.Play
import play.api.Play.current
import com.linkedin.restsearch._
import scala.collection.JavaConversions._

class UrlListDatasetLoader extends DatasetLoader {
  private val urlListOpt = Play.application.configuration.getStringList("resourceUrls")

  def loadDataset(isStartup: Boolean): Dataset = {
    val dataset = new Dataset()
    val clusters = new ClusterMap()
    val services = new ServiceArray()
    val cluster = new Cluster()
    urlListOpt foreach { urlList =>
      urlList foreach { url =>
        val service = new Service()
        service.setUrl(url)
        val path = url.split("/").last
        service.setKey(path)
        service.setPath(path)
        service.setProtocol(Protocol.REST)
        services.add(service)
      }
    }
    cluster.setServices(services)
    cluster.setName("AllResources")
    clusters.put("AllResources", cluster)
    dataset.setClusters(clusters)

    dataset
  }
}
