package com.linkedin.restsearch.dashboard

import com.linkedin.restli.server._
import com.linkedin.restsearch._
import scala.collection.JavaConverters._
import com.linkedin.restsearch.template.utils.Conversions._
import org.joda.time.{YearMonth, DateTime}
import com.linkedin.restsearch.snapshot.Snapshot
import com.linkedin.restli.restspec.ResourceSchema

case class DashboardStat(name: String, search: Option[String], count: Int)

case class DashboardStats(
  newResources: List[Service],
  migrationCounts: List[DashboardStat],
  resourceCounts: List[DashboardStat],
  methodCounts: List[DashboardStat],
  actionCounts: List[DashboardStat],
  operationCounts: List[DashboardStat],
  batchOnlyCounts: List[DashboardStat]
)

object DashboardStats {

  def buildDashboardStats(snapshot: Snapshot) = {

    def allResourceSchemas : Iterable[ResourceSchema] = {
      snapshot.services.values map { service =>
        service.getResourceSchema
      }
    }

    def searchTermEntry(name: String, term: String) = DashboardStat(name, Some(term), snapshot.searchIndex.getCountForTerm(term))

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
      searchTermEntry(m, "method:" + m)
    }

    val primaryServices = snapshot.services.values.filter(_.isPrimaryColoVariant)

    val operationCounts = List(
      DashboardStat("method", Some("(" + methods.map("method:" + _).mkString(" OR ") + ")"), primaryServices.map(s => if (s.hasResourceSchema) s.getResourceSchema.methods.size() else 0).sum),
      DashboardStat("finder", Some("hasFinder:true"), primaryServices.map(s => if (s.hasResourceSchema) s.getResourceSchema.finders.size() else 0).sum),
      DashboardStat("action", Some("hasAction:true"), primaryServices.map(s => if (s.hasResourceSchema) s.getResourceSchema.actions.size() else 0).sum)
    )

    val actionCounts = List(
      searchTermEntry("'get'", "action:\"get*\""),
      searchTermEntry("'create'", "action:\"create*\""),
      searchTermEntry("'update'", "action:\"update*\""),
      searchTermEntry("'delete'", "action:\"delete*\""),
      searchTermEntry("'find'", "action:\"find*\""),
      searchTermEntry("'list'", "action:\"list*\"")
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
      year <- inception.year().get() to currentYearMonth.year().get()
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
    val newResources = snapshot.search(new PagingContext(0, 100), "createdDate:[" + startOfRange.toString("yMMdd") + " TO " + currentDateTime.toString("yMMdd") + "] AND isColoVariant:false").getElements

    new DashboardStats(
      newResources.asScala.sortBy(_.getCreatedAt).reverse.toList,
      migrationCounts.toList,
      resourceCounts,
      methodCounts,
      actionCounts,
      operationCounts,
      batchOnlyCounts)

    // total errors + breakdown (options, not running)
    // total models + breakdown (record, typeref, enum, ...)
  }
}
