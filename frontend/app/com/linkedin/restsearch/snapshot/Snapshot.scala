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

import play.api.Logger
import com.linkedin.data.DataMap
import com.linkedin.restli.server._
import com.linkedin.restsearch._
import com.linkedin.restsearch.search._
import scala.collection.JavaConverters._
import scala.math
import com.linkedin.restli.restspec.ResourceSchema
import com.linkedin.restsearch.template.utils.Conversions._
import play.Play
import com.linkedin.restsearch.dashboard.DashboardStats
import com.linkedin.restsearch.resolvers.ServiceModelsSchemaResolver
import com.linkedin.data.schema.DataSchema

/**
 * Provides access to a snapshot of d2 (cluster, server, uri), idl (restspec.json) and data schema (.pdsc) data.
 * @author jbetz
 *
 */
class Snapshot(
  dataset: Dataset,
  val refreshedAt: Long,
  val searchIndex: SearchIndex) {

  val clusters = dataset.getClusters().asScala.toMap
  val services = dataset.servicesMap
  val serviceErrors = dataset.getServiceErrors().asScala.toMap

  lazy val models = services.values.map(_.models).reduce(_ ++ _).toMap

  val metadata = {
    val metadata = new DataMap()
    metadata.put("totalEntries", services.values.filter(_.isPrimaryColoVariant).map(_.resourceCount).sum.asInstanceOf[java.lang.Integer])
    metadata.put("totalClusters", clusters.values.filter(_.isPrimaryColoVariant).size.asInstanceOf[java.lang.Integer])
    metadata.put("totalErrors", serviceErrors.size.asInstanceOf[java.lang.Integer])
    metadata.put("refreshedAt", refreshedAt.asInstanceOf[java.lang.Long])
    metadata.put("environment", Some(Play.application.configuration.getString("environment")).getOrElse("UNKNOWN ENVIRONMENT"))
    metadata
  }

  def search(pagingContext: PagingContext, queryString: String): CollectionResult[Service, ServiceQueryMetadata] = {
    val results = if (queryString == null || queryString.trim().equals("")) {
        services.values
    } else {
      for {
        key <- searchIndex.search(queryString)
        parts = key.split('.').toList
        if(parts.size > 0)
        service <- services.get(parts.head)
        hit = try { findSubresourcesAlongPath(service, parts.tail).last }
              catch { case e: NoSuchElementException => Logger.warn("findService resulted in not found error: ", e);  null }
        if (hit != null) && isValidSearchResult(hit)
      } yield hit
    }
    val fromIndex = math.min(results.size, pagingContext.getStart())
    val toIndex = math.min(results.size, pagingContext.getStart() + pagingContext.getCount())
    val metadata = new ServiceQueryMetadata()
    metadata.setRefreshedAt(refreshedAt)
    metadata.setTotalEntries(services.size)
    val resultPage = results.slice(fromIndex, toIndex)
    new CollectionResult[Service, ServiceQueryMetadata](resultPage.toSeq.asJava, int2Integer(results.size), metadata)
  }

  private def isValidSearchResult(service: Service) = {
    !service.hasPath() || {
      val path = service.getPath().trim()
      (!path.equals("/") && !path.equals(""))
    }
  }

  def findCluster(name: String) : Option[Cluster] = {
    clusters.values.find(_.getName() == name)
  }

  def findService(cluster: Cluster, serviceKey: String) : Option[Service] = {
    try {
      val serviceKeyParts = serviceKey.split("\\.").toList
      serviceKeyParts match {
        case Nil => None
        case onlyKey :: Nil => cluster.getServices.asScala.find(_.getKey() == onlyKey)
        case firstKey :: subresourceKeys => {
          cluster.getServices.asScala.find(_.getKey() == firstKey) match {
            case Some(rootService) => {
              val servicesAlongPath = findSubresourcesAlongPath(rootService, subresourceKeys)
              servicesAlongPath.lastOption
            }
            case None => None
          }
        }
      }
    } catch {
      case e: NoSuchElementException => {
        Logger.warn("findService resulted in not found error: ", e)
        None
      }
    }
  }

  /**
   * TODO: We should index this information upfront so we don't have to do this recursive search routine
   *       here and so we can avoid constructing new service instances for all the subresources on each request.
   *
   * Create a list of Services from the root Service down to the correct subresource according to the resource keys in
   * resourcePath.
   *
   */
  private def findSubresourcesAlongPath(rootService: Service, resourcePathKeys: List[String]): List[Service] = {
    def find(currentService: Service, visitedPathKeys: List[String], reaminingPathKeys: List[String]): List[Service] = {
      reaminingPathKeys match {
        case Nil => {
          List(currentService)
        }
        case currentPathKey :: remainingPathKeys => currentService.allSubresources match {
          case Some(subresources) => {
            subresources.asScala.find(_.getName() == currentPathKey) match {
              case Some(subresourceForCurrentKey) => {
                val subresourceService = createServiceForSubresource(currentPathKey, subresourceForCurrentKey, currentService, rootService)
                currentService :: find(subresourceService, currentPathKey :: visitedPathKeys, remainingPathKeys)
              }
              case None => throw new NoSuchElementException(currentPathKey + " not found at " + visitedPathKeys.mkString("/"))
            }
          }
          case None => throw new NoSuchElementException(currentPathKey + " not found, subresources not found at " + visitedPathKeys.mkString("/"))
        }
      }
    }

    find(rootService, List(), resourcePathKeys)
  }

  private def createServiceForSubresource(key: String, schema: ResourceSchema, parent: Service, rootService: Service) = {
    val currentService = new Service() // key, path, clusters, resourceSchema, models
    currentService.setKey(key)
    currentService.setPath(schema.getPath)
    currentService.setResourceSchema(schema)
    currentService.setClusters(rootService.getClusters)
    currentService.setModels(rootService.getModels)
    currentService.setParent(parent)
    currentService
  }

  lazy val dashboardStats = DashboardStats.buildDashboardStats(this)

  def allClusters : List[Cluster] = {
    clusters.values.toList.sortWith(_.getName.toLowerCase < _.getName.toLowerCase)
  }
}



