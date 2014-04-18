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

package com.linkedin.restsearch.fetcher

import com.linkedin.data.schema.DataSchema
import com.linkedin.restli.restspec.ResourceSchema
import com.linkedin.restsearch.Service

sealed abstract class ScrapeResult()
case class SuccessfulScrape(resourceSchema: ResourceSchema, dataSchemas: Map[String, DataSchema]) extends ScrapeResult()
case class FailedScrape(ex: Throwable) extends ScrapeResult() {
  override def toString = {
    Option(ex.getMessage).getOrElse("unknown error of type: " + ex.getClass)
  }
}
/**
 * Fetches idl (aka resource schemas) and data schemas for a service.
 * @author jbetz
 *
 */
trait IdlFetcher {
  def fetch(service: Service): ScrapeResult
}
