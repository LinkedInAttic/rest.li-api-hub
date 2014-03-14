package com.linkedin.restsearch.permlink

import org.json.JSONObject
import com.linkedin.restli.internal.server.util.DataMapUtils
import com.linkedin.restli.restspec.ResourceSchema
import com.linkedin.restsearch._
import java.io.ByteArrayInputStream
import com.linkedin.data.template.StringMap
import scala.concurrent.Future
import play.api.libs.concurrent.Execution.Implicits._
import com.linkedin.restliexplorer.{D2Service, D2Cluster}

/**
 * Uses the CachingPasteInClient to store Cluster information in go/pastein.
 */
object ClusterPermlinkClient {

  def read(permlink: String): Future[Cluster] = {
    CachingPasteInClient.load(permlink) map { jsonStr =>
      parse(permlink, new JSONObject(jsonStr))
    }
  }

  def write(snapshot: String): Future[String] = {
    parse("upload_in_progress", new JSONObject(snapshot)) // validate before storing in pasteIn by parsing.  Will throw exception if parsing fails
    CachingPasteInClient.store(snapshot)
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
