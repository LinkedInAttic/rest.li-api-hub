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

package com.linkedin.restsearch.permlink

import org.json.JSONObject
import com.linkedin.restli.internal.server.util.DataMapUtils
import com.linkedin.restli.restspec.ResourceSchema
import com.linkedin.restsearch._
import java.io.ByteArrayInputStream
import com.linkedin.data.template.StringMap
import scala.concurrent.Future
import play.api.libs.concurrent.Execution.Implicits._
import com.linkedin.d2.{D2Service, D2Cluster}

/**
 * Uses a pastebin to store Cluster information.
 */
class ClusterPermlinkClient(pastebinClient: PastebinClient) {

  def read(permlink: String): Future[Cluster] = {
    pastebinClient.load(permlink) map { jsonStr =>
      parse(permlink, new JSONObject(jsonStr))
    }
  }

  def write(snapshot: String): Future[String] = {
    parse("upload_in_progress", new JSONObject(snapshot)) // validate before storing in a pastebin by parsing.  Will throw exception if parsing fails
    pastebinClient.store(snapshot)
  }

  private def parse(permlink: String, json: JSONObject): Cluster = {
    val serviceList = new ServiceArray()
    val service = new Service()

    serviceList.add(service)
    val clusterName = "permlink:" + permlink
    val cluster = new Cluster().setName(clusterName).setServices(serviceList).setSource(ClusterSource.SNAPSHOT)

    cluster.setD2Cluster(new D2Cluster().setName(clusterName))

    val serviceClusters = new ClusterArray()
    serviceClusters.add(new Cluster().setName("permlink:" + permlink))
    service.setClusters(serviceClusters)


    if(!json.has("models")) {
      if(json.has("name")) {
        throw new SnapshotUploadException("Uploaded file must be a .snapshot.json file, not a .restspec.json file.")
      } else {
        throw new SnapshotUploadException("Uploaded file does not appear to be a .snapshot.json file.")
      }
    }
    val schemaJson = json.getJSONObject("schema")
    val resourceSchema = try {
      val rawJson = schemaJson.toString()
      DataMapUtils.read(new ByteArrayInputStream(rawJson.getBytes("UTF-8")), classOf[ResourceSchema])
    } catch {
      case e: Exception => {
        throw new SnapshotUploadException("Error parsing json in uploaded file: " + e.getMessage())
      }
    }
    service.setResourceSchema(resourceSchema)
    service.setKey(resourceSchema.getName)
    service.setPath(resourceSchema.getPath)
    service.setProtocol(Protocol.REST)
    service.setD2Service(new D2Service().setName(resourceSchema.getName).setPath(resourceSchema.getPath))

    val modelsJson = json.getJSONArray("models")

    val modelMap = new StringMap()
    (0 to modelsJson.length()-1) map { i =>
      val model = modelsJson.getJSONObject(i)
      if(model.has("name")) {
        val fqn = {
          if (model.has("namespace")) {
            model.getString("namespace") + "."
          } else ""
        } + model.getString("name")
        modelMap.put(fqn, model.toString)
      }
    }

    service.setModels(modelMap)

    cluster
  }
}
