package com.linkedin.restsearch.server

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