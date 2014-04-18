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
import com.linkedin.data.schema.resolver.DefaultDataSchemaResolver
import com.linkedin.data.schema._
import com.linkedin.data.template.DataTemplateUtil
import scala.collection.JavaConverters._
import scala.Some

/**
 * Finds data models using the "models" section from the results of an OPTIONS request to a rest.li resource.
 * These models are usually fully inlined, meaning that only the named data models known to be needed by direct
 * references from the idl are root models and all named data models they depend on are inlined.
 *
 */
class ServiceModelsSchemaResolver(service: Service) extends DefaultDataSchemaResolver {
  override def findDataSchema(name: java.lang.String, sb: java.lang.StringBuilder): NamedDataSchema = {
    findModel(service, name) match {
      case Some(value: NamedDataSchema) => value
      case _ => null
    }
  }

  def findModel(service: Service, fqn: String) : Option[DataSchema] = {
    val option = Option(service.getModels.get(fqn)) map { model =>
      DataTemplateUtil.parseSchema(model)
    }
    if (option.isDefined) option
    else {
      val matchingModels = service.getModels.values().asScala flatMap { model =>
        findModelByName(DataTemplateUtil.parseSchema(model), fqn)
      }
      matchingModels.headOption
    }
  }

  // TODO: this recursive search for named resources is really annoying.   Seems like it would be better to flatten upfront.
  def findModelByName(node: DataSchema, fqn: String): Option[DataSchema] = {
    findModelByName(Set(), node, fqn)
  }

  private def findModelByName(visited: Set[DataSchema], node: DataSchema, fqn: String): Option[DataSchema] = {
    if (visited.contains(node)) None
    else {
      node match {
        case named: NamedDataSchema => {
          if (named.getFullName == fqn) {
            Some(named)
          } else {
            named match {
              case record: RecordDataSchema => {
                record.getFields.asScala.flatMap { field =>
                  findModelByName(visited + node, field.getType, fqn)
                }.headOption
              }
              case _ => None
            }
          }
        }
        case array: ArrayDataSchema => {
          findModelByName(visited + node, array.getItems, fqn)
        }
        case map: MapDataSchema => {
          findModelByName(visited + node, map.getValues, fqn)
        }
        case union: UnionDataSchema => {
          union.getTypes.asScala.flatMap { schema =>
            findModelByName(visited + node, schema, fqn)
          }.headOption
        }
        case primitive: PrimitiveDataSchema => {
          if(primitive.getDereferencedDataSchema != null) {
            findModelByName(visited + node, primitive.getDereferencedDataSchema, fqn)
          } else {
            None
          }
        }
        case _ => None
      }
    }
  }
}
