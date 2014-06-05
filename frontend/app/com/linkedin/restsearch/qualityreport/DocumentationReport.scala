package com.linkedin.restsearch.qualityreport

import com.linkedin.restsearch.{Cluster, Dataset}
import scala.collection.JavaConverters._
import com.linkedin.restsearch.template.utils.Conversions._
import com.linkedin.restsearch.template.utils.{RichActionSchema, RichFinderSchema, RichRestMethodSchema, MethodSchemaPart}

trait DocumentationCoverage {
  def totalElements: Int
  def documentedElements: Int
  def documentationRatio: Float = (totalElements, documentedElements) match {
    case (0, _) => 1
    case (_, 0) => 0
    case _ => documentedElements.toFloat / totalElements.toFloat
  }
}

trait DocumentationReport {
  // TODO:  improve quality check
  def calculateDocStringQuality(docOpt: Option[String]): Float = {
    docOpt match {
      case Some(doc) if doc.length > 0 => 1.0f
      case Some(doc) if doc.length == 0 => 0.0f
      case None => 0.0f
    }
  }
}

case class DatasetReport(clusterReports: Seq[ClusterReport]) extends DocumentationCoverage {
  override def totalElements: Int = clusterReports.map(_.totalElements).sum
  override def documentedElements: Int = clusterReports.map(_.documentedElements).sum
}
case class ClusterReport(name: String, resourceReports: Seq[ResourceReport]) extends DocumentationCoverage {
  override def totalElements: Int = resourceReports.map(_.totalElements).sum
  override def documentedElements: Int = resourceReports.map(_.documentedElements).sum
}

case class ResourceReport(key: String, doc: Option[String], methodReports: Seq[MethodReport]) extends DocumentationReport with DocumentationCoverage {
  override def totalElements: Int = methodReports.size + 1
  override def documentedElements: Int = methodReports.count(_.documentationQuality > 0.5f) + ( if (documentationQuality > 0.5f) 1 else 0 )

  val documentationQuality = calculateDocStringQuality(doc)
}

case class MethodReport(name: String, doc: Option[String]) extends DocumentationReport {
  val documentationQuality = calculateDocStringQuality(doc)
}

/**
 *
 */
object DocumentationReport {

  def buildDatasetReport(clusters: Seq[Cluster]): DatasetReport = {
    val clusterReports = clusters map { cluster =>

      val resourceReports = cluster.getServices.asScala.map { service =>
        val methodSchemaParts =
          service.getResourceSchema.methods.asScala.map(new RichRestMethodSchema(_)) ++
          service.getResourceSchema.finders.asScala.map(new RichFinderSchema(_)) ++
          service.getResourceSchema.actions.asScala.map(new RichActionSchema(_)) ++
          service.getResourceSchema.entityActions.asScala.map(new RichActionSchema(_))

        val methodReports = methodSchemaParts map { part =>
          new MethodReport(part.name, Option(part.documentation))
        }
        val doc = Option(service.documentation).flatMap { docString =>
          docString.split("generated from:")(0) match {
            case "" => None
            case str => Some(str)
          }
        }
        new ResourceReport(service.getKey, doc, methodReports)
      }

      new ClusterReport(cluster.getName, resourceReports)
    }

    new DatasetReport(clusterReports.toSeq)
  }
}
