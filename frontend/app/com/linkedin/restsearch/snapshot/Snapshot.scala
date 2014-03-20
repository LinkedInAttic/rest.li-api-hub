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
import scala.collection.JavaConversions._
import scala.math
import com.linkedin.restli.restspec.ResourceSchema
import com.linkedin.restsearch.template.utils.Conversions._
import play.Play
import org.joda.time.{LocalDateTime, YearMonth, LocalDate, DateTime}
import org.joda.time

/**
 * Provides access to a snapshot of d2 (cluster, server, uri), idl (restspec.json) and data schema (.pdsc) data.
 * @author jbetz
 *
 */
class Snapshot(
  dataset: Dataset,
  val refreshedAt: Long,
  val searchIndex: SearchIndex) {

  val clusters = dataset.getClusters().toMap
  val services = dataset.servicesMap
  val serviceErrors = dataset.getServiceErrors().toMap

  val metadata = {
    val metadata = new DataMap()
    metadata.put("totalEntries", services.values.filter(_.isPrimaryColoVariant).map(_.resourceCount).sum.asInstanceOf[java.lang.Integer])
    metadata.put("totalClusters", clusters.values.filter(_.isPrimaryColoVariant).size.asInstanceOf[java.lang.Integer])
    metadata.put("totalErrors", serviceErrors.size.asInstanceOf[java.lang.Integer])
    metadata.put("refreshedAt", refreshedAt.asInstanceOf[java.lang.Long])
    metadata
  }

  def search(pagingContext: PagingContext, queryString: String): CollectionResult[Service, ServiceQueryMetadata] = {
    val results = if (queryString == null || queryString.trim().equals("")) {
        collectionAsScalaIterable(services.values)
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
    new CollectionResult[Service, ServiceQueryMetadata](seqAsJavaList(resultPage.toSeq), int2Integer(results.size), metadata)
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
        case onlyKey :: Nil => cluster.getServices().find(_.getKey() == onlyKey)
        case firstKey :: subresourceKeys => {
          cluster.getServices().find(_.getKey() == firstKey) match {
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
   * TODO:  We should index this information upfront so we don't have to do this recursive search routine
   * here and so we can avoid constructing new service instances for all the subresources on each request.
   *
   * Create a list of Services from the root Service down to the correct subresource according to the resource keys in resourcePath.
   *
   * E.g.  given the "threadsBackend" service as root and List("comments", "likes") for UscpBackend, this routine will return a list of the form:
   * List(
   *   <existing threadsBackend service that is passed in as root>,
   *   <new service instance for "comments" with parent set to "threadsBackend">,
   *   <new service instance for "likes" with parent set to "comments">)
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
            subresources.find(_.getName() == currentPathKey) match {
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
    val currentService = new Service() // key, path, clusters, protocol, resourceSchema, models
    currentService.setKey(key)
    currentService.setPath(schema.getPath())
    currentService.setResourceSchema(schema)
    currentService.setClusters(rootService.getClusters())
    currentService.setProtocol(rootService.getProtocol())
    currentService.setModels(rootService.getModels())
    currentService.setParent(parent)
    currentService
  }

  def allClusters : List[Cluster] = {
    clusters.values.toList.sortWith(_.getName().toLowerCase < _.getName().toLowerCase)
  }

  def allResourceSchemas : Iterable[ResourceSchema] = {
    services.values map { service =>
      service.getResourceSchema
    }
  }

  def allMethods = allResourceSchemas map { schema =>
    schema.methods
  }

  def searchTermEntry(name: String, term: String) = DashboardStat(name, Some(term), searchIndex.getCountForTerm(term))

  lazy val dashboardStats = buildDashboardStats()
  private def buildDashboardStats() = {
    // total REST vs. content services

    val protocolCounts = List(
      searchTermEntry("rest", "protocol:REST"),
      searchTermEntry("content service", "protocol:CONTENT_SERVICE")
    )

    val resourceCounts = List(
      searchTermEntry("collections", "isCollection:true AND NOT hasComplexKey:true"),
      searchTermEntry("complex keys", "isCollection:true AND hasComplexKey:true"),
      searchTermEntry("associations", "isAssociation:true"),
      searchTermEntry("action sets", "isActionSet:true"),
      searchTermEntry("simple", "isSimple:true")
    )

    // total methods + breakdown (methods, finders, actions)
    val methods = List("get", "batch_get", "update", "batch_update",
      "partial_update", "batch_partial_update",
      "delete", "batch_delete", "create", "batch_create")

    val methodCounts = methods map { m =>
      searchTermEntry(m, "method:" + m + " AND protocol:REST")
    }

    val primaryServices = services.values.filter(_.isPrimaryColoVariant)
    val operationCounts = List(
      DashboardStat("method", Some("(" + methods.map("method:" + _).mkString(" OR ") + ") AND protocol:REST"), primaryServices.map(s => if (s.getProtocol == Protocol.REST && s.hasResourceSchema) s.getResourceSchema.methods.size() else 0).sum),
      DashboardStat("finder", Some("hasFinder:true AND protocol:REST"), primaryServices.map(s => if (s.getProtocol == Protocol.REST && s.hasResourceSchema) s.getResourceSchema.finders.size() else 0).sum),
      DashboardStat("action", Some("hasAction:true AND protocol:REST"), primaryServices.map(s => if (s.getProtocol == Protocol.REST && s.hasResourceSchema) s.getResourceSchema.actions.size() else 0).sum)
    )

    val actionCounts = List(
      searchTermEntry("'get'", "action:\"get*\" AND protocol:REST"),
      searchTermEntry("'create'", "action:\"create*\" AND protocol:REST"),
      searchTermEntry("'update'", "action:\"update*\" AND protocol:REST"),
      searchTermEntry("'delete'", "action:\"delete*\" AND protocol:REST"),
      searchTermEntry("'find'", "action:\"find*\" AND protocol:REST"),
      searchTermEntry("'list'", "action:\"list*\" AND protocol:REST")
    )

    val batchOnlyCounts = List(
      searchTermEntry("get", "method:batch_get AND NOT method:get"),
      searchTermEntry("update", "method:batch_update AND NOT method:update"),
      searchTermEntry("create", "method:batch_create AND NOT method:create"),
      searchTermEntry("delete", "method:batch_delete AND NOT method:delete")
    )

    val servicesBucketedByCreatedAt = primaryServices.groupBy(s =>
      new DateTime(s.getCreatedAt).toLocalDate.toString("MMM y")
    )

    val serviceCountBucketedByCreatedAt = servicesBucketedByCreatedAt.mapValues(_.size)

    val inception = new YearMonth(2012, 10)
    val currentYearMonth = new YearMonth(System.currentTimeMillis())
    val timeBuckets = for {
      year <- 2012 to 2014
      month <- 1 to 12
      date = new YearMonth(year, month)
      if(!date.isBefore(inception) && !date.isAfter(currentYearMonth))
      label = date.toString("MMM y")
    } yield  (label, serviceCountBucketedByCreatedAt.getOrElse(label, 0))
    val migrationCounts = timeBuckets.map{ case(label, count) =>
      DashboardStat(label, None, count)
    }

    val currentDateTime = new DateTime(System.currentTimeMillis())
    val startOfRange = currentDateTime.minusDays(31)
    val newResources = search(new PagingContext(0, 100), "createdDate:[" + startOfRange.toString("yMMdd") + " TO " + currentDateTime.toString("yMMdd") + "] AND protocol:REST AND isColoVariant:false").getElements

    new DashboardStats(
      newResources.toList.sortBy(_.getCreatedAt).reverse,
      migrationCounts.toList,
      protocolCounts,
      resourceCounts,
      methodCounts,
      actionCounts,
      operationCounts,
      batchOnlyCounts)

    // total errors + breakdown (options, not running)
    // total models + breakdown (record, typeref, enum, ...)
  }
}

case class DashboardStat(name: String, search: Option[String], count: Int)
case class DashboardStats(
  val newResources: List[Service],
  val migrationCounts: List[DashboardStat],
  val protocolCounts: List[DashboardStat],
  val resourceCounts: List[DashboardStat],
  val methodCounts: List[DashboardStat],
  val actionCounts: List[DashboardStat],
  val operationCounts: List[DashboardStat],
  val batchOnlyCounts: List[DashboardStat]
)
