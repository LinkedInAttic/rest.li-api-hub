package com.linkedin.restsearch.fetcher

import com.linkedin.data.schema.DataSchema
import com.linkedin.restli.restspec.ResourceSchema

sealed abstract class ScrapeResult()
case class SuccessfulScrape(val resourceSchema: ResourceSchema, val dataSchemas: Map[String, DataSchema]) extends ScrapeResult()
case class FailedScrape(val ex: Throwable) extends ScrapeResult() {
  override def toString = {
    Option(ex.getMessage).getOrElse("unknown error of type: " + ex.getClass)
  }
}
/**
 * Fetches idl (aka resource schemas) and data schemas from a d2 service name.
 * @author jbetz
 *
 */
trait IdlFetcher {
  def fetch(serviceKey: String, servicePath: String): ScrapeResult
}
