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

package com.linkedin.restsearch.utils

import com.linkedin.restsearch.Cluster
import com.linkedin.restsearch.Service
import controllers.routes
import scala.collection.JavaConverters._
import org.json.JSONObject
import org.json.JSONArray
import com.linkedin.restsearch.template.utils.Conversions._
import com.linkedin.data.schema._
import com.linkedin.data.{DataMap, DataList}
import com.linkedin.data.template.DataTemplateUtil
import com.linkedin.data.schema.generator.SchemaSampleDataGenerator
import com.linkedin.restli.internal.server.util.DataMapUtils
import com.linkedin.restli.restspec._

object TypeRenderer {
  val defaultSpec = new SchemaSampleDataGenerator.DataGenerationOptions()


  def stripPackage(fullyQualifiedName: String) = {
    fullyQualifiedName.split("\\.").lastOption getOrElse(fullyQualifiedName)
  }

  def typeOfABasicMethod(method: RestMethodSchema, service: Service, keyType: String,  typeRenderer: TypeRenderer): String = {

    def convertKeyType(keyType: String):String = {
      if (keyType.equals("int")) "integer"
      else if (keyType.equals("bool")) "boolean"
      else stripPackage(keyType)
    }

    val methodName = method.getMethod
    val fixedKeyType = convertKeyType(keyType)
    val resourceReturn = service.getResourceSchema.getSchema
    if (methodName.equals("get")) {
      typeRenderer.typeStringToHtml(resourceReturn)
    } else if (methodName.equals("batch_get")) {
      "<a href=\"https://github.com/linkedin/rest.li/wiki/Rest.li-Protocol#map-of-entities\">batch response</a> containing Map(" + fixedKeyType + "->" + typeRenderer.typeStringToHtml(resourceReturn) + ")"
    } else if (methodName.equals("get_all")) {
      "<a href=\"https://github.com/linkedin/rest.li/wiki/Rest.li-Protocol#list-of-entities\">list response</a> of " + typeRenderer.typeStringToHtml(resourceReturn) + " results"
    } else if (methodName.contains("update")) {
      if (methodName.contains("batch"))
        "<a href=\"https://github.com/linkedin/rest.li/wiki/Rest.li-Protocol#batch-update\">batch update response</a>"
      else
        "<a href=\"https://github.com/linkedin/rest.li/wiki/Rest.li-User-Guide#update\">update response</a>"
    } else if (methodName.contains("create")) {
      if (methodName.contains("batch")) {
        "<a href=\"https://github.com/linkedin/rest.li/wiki/Rest.li-Protocol#batch-create\">batch create response</a>"
      } else {
        "<a href=\"https://github.com/linkedin/rest.li/wiki/Rest.li-User-Guide#create\">create response</a>"
      }
    } else if (methodName.contains("delete")) {
      if (methodName.contains("batch"))
        "<a href=\"https://github.com/linkedin/rest.li/wiki/Rest.li-Protocol#map-of-entities\">batch delete response</a>"
      else
        "<a href=\"https://github.com/linkedin/rest.li/wiki/Rest.li-User-Guide#delete\">delete response</a>"
    } else {
      ""
    }
  }
}

/**
 * Routines for rendering data schemas to html and to pretty printed json example code.
 *
 * @author jbetz
 *
 */
class TypeRenderer(cluster: Cluster, service: Service, val resolver: DataSchemaResolver) {

  // params are a special case because they have a non-standard way of representing maps and arrays.  This is being fixed but for backward-compat
  // we need to handle the case.
  def paramTypeToHtml(param : ParameterSchema) = param.getType match {
    case "array" => typeStringToHtml(param.getItems) + "[]"
    case "map" => "Map (string->" + typeStringToHtml(param.getItems) + ")"
    case _ => typeStringToHtml(param.getType)
  }

  def typeStringToHtml(typeString: String) = {
    if(typeString == null) {
      typeString
    } else if(isInlineSchema(typeString)) {
      try {
        schemaToHtml(parseInlineSchema(typeString))
      } catch {
        case e: IllegalArgumentException =>
          ""
      }
    } else {
      modelLink(typeString)
    }
  }

  def schemaToHtml(schema: DataSchema) : String = {
    val rootService = service.findRoot
    schema match {
      case named: NamedDataSchema => {
        "<a href='" +
          routes.Application.model(cluster.getName, rootService.getKey, named.getNamespace +
          "." + named.getName) + "'>"+ named.getName +
          "</a>"
      }
      case array: ArrayDataSchema => {
        schemaToHtml(array.getItems) + "[]"
      }
      case map: MapDataSchema => {
        "Map (string->" + schemaToHtml(map.getValues) + ")"
      }
      case union: UnionDataSchema => {
        val unionTypesHtml = union.getTypes.asScala.map { t =>
          schemaToHtml(t)
        }
        "Union [" + unionTypesHtml.mkString(", ") + "]"
      }
      case primitive: PrimitiveDataSchema => {
        primitive.getType.toString.toLowerCase
      }
    }
  }

  private def stripPackage(fullyQualifiedName: String) = {
    fullyQualifiedName.split("\\.").last
  }

  private def modelLink(fqn: String) = {
    val rootService = service.findRoot
    if(fqn.contains(".")) {
      "<a href=\"" + routes.Application.model(cluster.getName, rootService.getKey, fqn) + "\">" + stripPackage(fqn) + "</a>"
    } else {
      fqn
    }
  }

  private def isInlineSchema(typeString: String) = {
    typeString.startsWith("{")
  }

  private def parseInlineSchema(typeString: String) = {
    DataTemplateUtil.parseSchema(typeString, resolver)
  }
}