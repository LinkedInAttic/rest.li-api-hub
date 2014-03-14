package com.linkedin.restsearch.html

import com.linkedin.restsearch.Cluster
import com.linkedin.restsearch.Service
import com.linkedin.restli.restspec._
import com.linkedin.data.schema._
import com.linkedin.data.template.DataTemplateUtil
import controllers.routes
import scala.collection.JavaConversions._
import com.linkedin.data.schema.generator.SchemaSampleDataGenerator
import com.linkedin.data.{DataList, DataMap}
import com.linkedin.restli.internal.server.util.DataMapUtils
import org.json.JSONObject
import org.json.JSONArray
import com.linkedin.restsearch.template.utils.Conversions._

object TypeRenderer {
  val defaultSpec = new SchemaSampleDataGenerator.DataGenerationOptions()
  def schemaToJsonExample(schema: DataSchema) : String = {
    try {
    SchemaSampleDataGenerator.buildData(schema, defaultSpec) match {
      case dataMap : DataMap => {
        val result = new String(DataMapUtils.mapToBytes(dataMap), "UTF-8")
        if(result.startsWith("{")) {
          new JSONObject(result).toString(2)
        } else {
          result
        }
      }
      case dataList : DataList => {
        val result = new String(DataMapUtils.listToBytes(dataList), "UTF-8")
        if(result.startsWith("[")) {
          new JSONArray(result).toString(2)
        } else {
          result
        }
      }
      case value => value.toString 
    }
    } catch {
      case ial: IllegalArgumentException => "{'message': 'Unable to generate example: "+ial.getMessage+"'}"
      case e: Throwable => throw e
    }
  }
    
  // This is a super lame hack
  // TODO: find way to convert primitive to example using SchemaSampleDataGenerator
  def exampleForPrimitive(typeString: String) = {
    val box = new JSONObject()
    box.put("type", "record")
    box.put("name", "temp")
    val fields = new JSONArray()
    box.put("fields", fields)
    val field = new JSONObject()
    fields.put(field)
    field.put("name", "boxed")
    field.put("type", typeString)
    new JSONObject(schemaToJsonExample(DataTemplateUtil.parseSchema(box.toString))).get("boxed")
  }

  def stripPackage(fullyQualifiedName: String) = {
    fullyQualifiedName.split("\\.").lastOption getOrElse(fullyQualifiedName)
  }

  def typeOfABasicMethod(method: RestMethodSchema, service: Service, keyType: String,  typeRenderer: TypeRenderer): String = {

    def convertKeyType(keyType: String):String = {
      if (keyType.equals("int")) "integer"
      else if (keyType.equals("bool")) "boolean"
      else stripPackage(keyType)
    }

    val methodName = method.getMethod()
    val fixedKeyType = convertKeyType(keyType)
    val resourceReturn = service.getResourceSchema().getSchema()
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
    case "array" => typeStringToHtml(param.getItems()) + "[]"
    case "map" => "Map (string->" + typeStringToHtml(param.getItems()) + ")"
    case _ => typeStringToHtml(param.getType())
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
          routes.Application.model(cluster.getName(), rootService.getKey(), named.getNamespace() +
          "." + named.getName()) + "'>"+ named.getName() +
          "</a>"
      }
      case array: ArrayDataSchema => {
        schemaToHtml(array.getItems()) + "[]"
      }
      case map: MapDataSchema => {
        "Map (string->" + schemaToHtml(map.getValues()) + ")"
      }
      case union: UnionDataSchema => {
        val unionTypesHtml = union.getTypes().map { t =>
          schemaToHtml(t)
        }
        "Union [" + unionTypesHtml.mkString(", ") + "]"
      }
      case primitive: PrimitiveDataSchema => {
        primitive.getType().toString().toLowerCase()
      }
    }
  }

  private def stripPackage(fullyQualifiedName: String) = {
    fullyQualifiedName.split("\\.").last
  }

  private def modelLink(fqn: String) = {
    val rootService = service.findRoot
    if(fqn.contains(".")) {
      "<a href=\"" + routes.Application.model(cluster.getName(), rootService.getKey(), fqn) + "\">" + stripPackage(fqn) + "</a>"
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