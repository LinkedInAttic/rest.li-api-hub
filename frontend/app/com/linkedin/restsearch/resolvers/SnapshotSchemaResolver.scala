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

package com.linkedin.restsearch.resolvers

import com.linkedin.restsearch.Service
import com.linkedin.data.schema._
import com.linkedin.data.template.DataTemplateUtil
import scala.collection.mutable
import com.linkedin.data.schema.resolver.DefaultDataSchemaResolver
import java.lang.StringBuilder

/**
 * Finds models using the "models" section of a snapshot.json file.   snapshot.json files are relatively flat
 * and all named data schemas are in the root.
 */
class SnapshotSchemaResolver(service: Service) extends DefaultDataSchemaResolver {
  val cache = mutable.Map[String, NamedDataSchema]()
  override def findDataSchema(name: String, sb: StringBuilder): NamedDataSchema = {
    cache.getOrElseUpdate(name, {
        Option(service.getModels.get(name)) map { model =>
          DataTemplateUtil.parseSchema(model, this)
        } collect {
          case value: NamedDataSchema => value
        } getOrElse {
          throw new IllegalArgumentException("schema not found in snapshot: " + name)
        }
      }
    )
  }
}