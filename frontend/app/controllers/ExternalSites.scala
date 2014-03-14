package controllers;

import com.linkedin.restsearch._
import com.linkedin.restsearch.template.utils.Conversions._
import play.Play
import com.linkedin.data.schema.DataSchemaResolver

object ExternalSites {
  private val config = Play.application().configuration()
  private val d2url = config.getString("d2url")
  private val decoUrl = config.getString("decoUrl")
  private val decoHttpUrl = config.getString("decoHttpUrl")

  def createUrlToRestdocs(cluster: Cluster, path: String) = {
    clusterUri(cluster) match {
      case Some(uri) => uri + "/restli/docs/rest/" + path
      case None => ""
    }
  }

  def createUrlToRestdocsModel(cluster: Cluster, fullyQualifiedName: String) = {
    clusterUri(cluster) match {
      case Some(uri) => uri + "/restli/docs/data/" + fullyQualifiedName
      case None => ""
    }
  }

  def createUrlToDecoProxy(cluster: Cluster, path: String) = {
    clusterUri(cluster) match {
      case Some(uri) => {
        val serverUrlWithoutHttp = uri.replaceFirst("http://", "")
        decoHttpUrl + serverUrlWithoutHttp + path
      }
      case None => ""
    }
  }

  def clusterUri(cluster: Cluster): Option[String] = {
    cluster.uris.headOption.map(_.getURI.toString.replace(".stg:", ".stg.linkedin.com:"))
  }

  def createD2Url(path: String) = {
    d2url + path
  }

  def createDecoUrl(urn: String) = {
    decoUrl + "/" + urn
  }

  def createD2UrlToDecoProxy(service: Service, resolver: DataSchemaResolver) = {
    decoUrl // TODO: add path and key
  }

  def createViewvcUrl(coords: IvyCoordinate, javaClassName: String): String = {
    val mp = stripPackage(coords.getOrg)
    val name = coords.getName
    val javaFilePath = javaClassName.replaceAll("\\.", "/") + ".java"
    "http://viewvc.corp.linkedin.com/netrepo/%s/trunk/%s/%s/src/main/java/%s?view=markup".format(mp, mp, name, javaFilePath)
  }

  def stripPackage(fullyQualifiedName: String) = {
    fullyQualifiedName.split("\\.").lastOption getOrElse(fullyQualifiedName)
  }
}
