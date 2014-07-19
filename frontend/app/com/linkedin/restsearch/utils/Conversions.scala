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

package com.linkedin.restsearch.template.utils

import com.linkedin.restli.restspec._
import com.linkedin.restsearch._
import scala.collection.JavaConverters._
import scala.collection.mutable
import com.linkedin.restsearch.template.utils.Conversions._
import com.linkedin.d2.D2Uri
import com.linkedin.data.schema.{DataSchemaResolver, DataSchema, NamedDataSchema, DataSchemaTraverse}
import com.linkedin.data.schema.DataSchemaTraverse.Callback
import org.json.JSONObject
import com.linkedin.restli.docgen.examplegen.ExampleRequestResponseGenerator
import com.linkedin.r2.message.rest.{RestResponse, RestRequest}
import com.linkedin.restli.common.RestConstants
import java.lang.IllegalArgumentException
import play.api.Play
import play.api.Play.current
import com.linkedin.restsearch.resolvers.{SnapshotSchemaResolver, ServiceModelsSchemaResolver}
import scala.Some
import com.linkedin.data.template.StringMap

/**
 * Use implicit conversions to mix in additional utility methods to the data template classes.
 */
object Conversions {
  lazy val knownColoVariantSuffixes = Play.application.configuration.getStringList("coloVariantSuffixes").map(_.asScala).getOrElse(Nil)

  implicit def toRichDataset(dataset: Dataset) = new RichDataset(dataset)
  implicit def toRichCluster(cluster: Cluster) = new RichCluster(cluster)
  implicit def toRichService(service: Service) = new RichService(service)
  implicit def toRichResourceSchema(schema: ResourceSchema) = new RichResourceSchema(schema)
  implicit def toRichFinderSchema(schema: FinderSchema) = new RichFinderSchema(schema)
  implicit def toRichActionSchema(schema: ActionSchema) = new RichActionSchema(schema)
  implicit def toRichRestMethodSchema(schema: RestMethodSchema) = new RichRestMethodSchema(schema)
  implicit def toRichRestRequest(request: RestRequest) = new RichRestRequest(request)
  implicit def toRichRestResponse(response: RestResponse) = new RichRestResponse(response)
  implicit def toRichServiceProvidedRequestResponse(reqRes: ServiceProvidedRequestResponse) = new RichServiceProvidedRequestResponse(reqRes)
  implicit def toRichRequestResponse(requestResponse: RequestResponse) = new RichRequestResponse(requestResponse)
}


trait ColoVariantAware {
  def coloVariantIdentifier: String
  def isPrimaryColoVariant: Boolean

  def stripColoSuffix: String = {
    if (isColoVariant) {
      knownColoVariantSuffixes.foreach { colo =>
        if (coloVariantIdentifier.endsWith(colo)) {
          return coloVariantIdentifier.substring(0, coloVariantIdentifier.length - colo.length)
        }
      }
    }
    coloVariantIdentifier
  }

  lazy val isColoVariant = {
    knownColoVariantSuffixes.exists(coloVariantIdentifier.endsWith)
  }

  lazy val coloVariantSuffix = {
    matchingVariantSuffix match {
      case Some(variantSuffix) => variantSuffix.stripPrefix("-")
      case None => ""
    }
  }

 lazy val canonicalColoVariantIdentifier = {
   matchingVariantSuffix match {
     case Some(variantSuffix) => coloVariantIdentifier.stripSuffix(variantSuffix)
     case None => coloVariantIdentifier
   }
 }

  private lazy val matchingVariantSuffix: Option[String] = {
    knownColoVariantSuffixes.find(coloVariantIdentifier.endsWith)
  }
}

class RichService(service: Service) extends ColoVariantAware with Documented {

  def hasDocumentation: Boolean = service.getResourceSchema.hasDoc
  def documentation: String = service.getResourceSchema.getDoc

  override def coloVariantIdentifier = service.getKey
  def isPrimaryColoVariant = /*(service.hasDefaultService && service.isDefaultService) ||*/ !isColoVariant

  lazy val models = {
    val resolver = new ServiceModelsSchemaResolver(service)
    val rootModels = service.getModels.asScala.map { case (modelName, modelJson) =>
      try {
        modelName -> resolver.findModel(service, modelName)
      } catch {
        case _: Throwable => modelName -> None
      }
    }.collect {
      case (k, Some(v)) => k -> v
    }

    // if the data models are the results of an OPTIONS request,  the root level models list may not contain all
    // named data schemas.  So we must traverse down, finding all additional schemas.
    // Using the existing DataSchemaTraverse class from pegasus, even though it requires using a mutable map.
    val traverser = new DataSchemaTraverse()
    val callback = new TraverserCallback(new mutable.HashMap[String, DataSchema])
    rootModels.values.foreach { schema =>
      traverser.traverse(schema, callback)
    }

    rootModels ++ callback.accumulator
  }

  private class TraverserCallback(val accumulator: mutable.Map[String, DataSchema]) extends Callback {
    def callback(path: java.util.List[String], schema: DataSchema) {
      schema match {
        case named: NamedDataSchema => accumulator += (named.getFullName -> named)
        case _ => ()
      }
    }
  }



  /**
   * Count of resources under this resource, including itself and it's subresources
   */
  lazy val resourceCount = {
    if(service.hasResourceSchema) {
      1 + service.getResourceSchema.subresourceCount
    } else {
      1
    }
  }

  /**
   * Includes transitive subresources.
   * @return
   */
  def allSubresources = {

    if(service.hasResourceSchema())
    {
      val resourceSchema = service.getResourceSchema()
      val entity = if (resourceSchema.hasCollection()) {
        Some(resourceSchema.getCollection().getEntity())
      } else if (resourceSchema.hasAssociation()) {
        Some(resourceSchema.getAssociation().getEntity())
      } else {
        None
      }
      if(entity.isDefined && entity.get.hasSubresources()) {
        Some(entity.get.getSubresources())
      } else None
    } else {
      None
    }
  }

  def allSubresourcesAsServices: List[Service] = {
    service.allSubresources match {
      case Some(subresources) => {
        subresources.asScala.map { subresource =>
          createServiceForSubresource(subresource.getName, subresource, service, service.findRoot)
        }.toList
      }
      case None=> List()
    }
  }


  def findRoot: Service = {
    if(!service.hasParent) service
    else service.getParent.findRoot
  }

  def getParents: List[Service] = {
    def findParents(s: Service): List[Service] = {
      if(s.hasParent) s.getParent :: findParents(s.getParent)
      else List()
    }
    findParents(service).reverse
  }

  /**
   * Gets the resource name path to the resource but using d2 server name for the root resource, e.g.: threadsBackend comments likes
   * @return
   */
  def keysToResource : List[String] = {
    def path(service: Service) : List[String] = {
      if(!service.hasParent()) {
        List(service.getKey())
      } else {
        service.getKey() :: path(service.getParent())
      }
    }
    path(service).reverse
  }

  /**
   * Gets the resource name path to the resource, e.g.: threads comments likes
   * @return
   */
  def pathsToResource : List[String] = {
    def path(service: Service) : List[String] = {
      if(!service.hasParent()) {
        List(service.getResourceSchema.name)
      } else {
        service.getResourceSchema.name :: path(service.getParent())
      }
    }
    path(service).reverse
  }

  def exampleRequestResponseGenerator(resolver: DataSchemaResolver) : Option[ExampleRequestResponseGenerator] = {
    try {
      Some(new ExampleRequestResponseGenerator(service.getParents.map(_.getResourceSchema).asJava, service.getResourceSchema, resolver))
    } catch {
      case iae: IllegalArgumentException => None // workaround until fix in https://rb.corp.linkedin.com/r/255178/ is rolled out
      case e: Throwable => throw e
    }
  }

  private def createServiceForSubresource(key: String, schema: ResourceSchema, parent: Service, rootService: Service) = {
    val currentService = new Service() // key, path, clusters, resourceSchema, models
    currentService.setKey(key)
    currentService.setPath(schema.getPath())
    currentService.setResourceSchema(schema)
    currentService.setClusters(rootService.getClusters())
    currentService.setModels(rootService.getModels())
    currentService.setParent(parent)
    currentService
  }
}

class RichCluster(cluster: Cluster) extends ColoVariantAware {

  def getResolver(service: Service) = {
    if(cluster.getSource == ClusterSource.D2 || cluster.getSource == ClusterSource.NON_D2) {
      new ServiceModelsSchemaResolver(service)
    } else {
      new SnapshotSchemaResolver(service)
    }
  }

  override def coloVariantIdentifier = cluster.getName
  def isPrimaryColoVariant = !isColoVariant || cluster.getServices.asScala.exists(_.isPrimaryColoVariant)

  // finds all other services in this cluster that are variants of the given service
  def coloVariants(primary: Service) = {
    val primaryKey = primary.getKey
    val variants = cluster.getServices.asScala.filter { service =>
      primaryKey == service.canonicalColoVariantIdentifier && service.isColoVariant
    }
    variants.sortBy(_.coloVariantSuffix)
  }

  // given a list of clusters, finds any that are colo variants of this cluster
  def coloVariants(clusters: List[Cluster]) = {
    val variants = clusters.filter { cluster =>
      canonicalColoVariantIdentifier == cluster.canonicalColoVariantIdentifier && cluster.isColoVariant
    }
    variants.sortBy(_.coloVariantSuffix)
  }

  def services(primaryOnly: Boolean = true): List[Service] = {
    val services = cluster.getServices.asScala.toList
    if(primaryOnly) services.filter(_.isPrimaryColoVariant) else services
  }

  def uris: List[D2Uri] = {
    if(!cluster.hasD2Cluster || !cluster.getD2Cluster().hasServices()) List()
    else cluster.getD2Cluster.getUris.asScala.toList
  }
}

trait Named {
  def name: String
}

trait Annotated {
  def annotations: CustomAnnotationContentSchemaMap
}

trait Documented {
  def hasDocumentation: Boolean
  def documentation: String
}

trait Deprecatable extends Annotated {
  def isDeprecated = annotations.containsKey("deprecated")
  def deprecatedDoc: Option[String] = {
    if(isDeprecated) {
      val deprecatedAnnotation = annotations.get("deprecated")
      if(deprecatedAnnotation.data().containsKey("doc")) {
        Some(deprecatedAnnotation.data().getString("doc"))
      } else {
        None
      }
    } else None
  }
}


trait MethodAnnotations extends Annotated {
  def isTestMethod = annotations.containsKey("testMethod")
  def testMethodDoc: Option[String] = {
    if(isTestMethod) {
      val testMethodAnnotation = annotations.get("testMethod")
      if(testMethodAnnotation.data().containsKey("doc")) {
        Some(testMethodAnnotation.data().getString("doc"))
      } else {
        None
      }
    } else None
  }
}

abstract class MethodSchemaPart extends Named with Documented with Deprecatable with MethodAnnotations
class RichFinderSchema(finder: FinderSchema) extends MethodSchemaPart {
  override def name = finder.getName

  override def annotations = {
    if(finder.hasAnnotations) {
      finder.getAnnotations
    } else new CustomAnnotationContentSchemaMap
  }
  override def hasDocumentation = finder.hasDoc
  override def documentation = finder.getDoc
}

class RichActionSchema(action: ActionSchema) extends MethodSchemaPart {
  override def name = action.getName
  override def annotations = {
    if(action.hasAnnotations) {
      action.getAnnotations
    } else new CustomAnnotationContentSchemaMap
  }
  override def hasDocumentation = action.hasDoc
  override def documentation = action.getDoc
}

class RichRestMethodSchema(method: RestMethodSchema) extends MethodSchemaPart {
  override def name = method.getMethod
  override def annotations = {
    if(method.hasAnnotations) {
      method.getAnnotations
    } else new CustomAnnotationContentSchemaMap
  }
  override def hasDocumentation = method.hasDoc
  override def documentation = method.getDoc
}

abstract class ResourceSchemaPart extends Named with Documented with Deprecatable

class RichResourceSchema(schema: ResourceSchema) extends ResourceSchemaPart {
  override def name = schema.getName
  def namespace = schema.getNamespace
  def schemaFullName = schema.getSchema
  def schemaName = stripPackage(schema.getSchema)

  def stripPackage(fullyQualifiedName: String) = {
    fullyQualifiedName.split("\\.").lastOption getOrElse(fullyQualifiedName)
  }

  override def annotations = {
    if(schema.hasAnnotations) {
      schema.getAnnotations
    } else new CustomAnnotationContentSchemaMap
  }
  override def hasDocumentation = schema.hasDoc
  override def documentation = schema.getDoc

  def isInternalToSuperblock = annotations.containsKey("internalToSuperblock")
  def internalToSuperblockDoc: Option[String] = {
    if(isInternalToSuperblock) {
      val superblockAnnotation = annotations.get("internalToSuperblock")
      if(superblockAnnotation.data().containsKey("doc")) {
        Some(superblockAnnotation.data().getString("doc"))
      } else {
        None
      }
    } else None
  }

  def isScoped = isDeprecated || isInternalToSuperblock
  def scope = {
    {
      if (isDeprecated) {
        "(Deprecated)"
      } else {
        ""
      }
    } + {
      if (isInternalToSuperblock) {
        "(Superblock Internal)"
      } else {
        ""
      }
    }
  }

  def methods = {
    if(schema.hasCollection && schema.getCollection.hasMethods) {
      schema.getCollection.getMethods
    } else if(schema.hasAssociation && schema.getAssociation.hasMethods) {
      schema.getAssociation.getMethods
    } else if(schema.hasSimple && schema.getSimple.hasMethods) {
      schema.getSimple.getMethods
    } else {
      new RestMethodSchemaArray()
    }
  }

  def finders = {
    if(schema.hasCollection && schema.getCollection.hasFinders) {
      schema.getCollection.getFinders
    } else if(schema.hasAssociation && schema.getAssociation.hasFinders) {
      schema.getAssociation.getFinders
    } else {
      new FinderSchemaArray()
    }
  }

  def actions = {
    if(schema.hasCollection && schema.getCollection.hasActions) {
      schema.getCollection.getActions
    } else if(schema.hasAssociation && schema.getAssociation.hasActions) {
      schema.getAssociation.getActions
    } else if(schema.hasSimple && schema.getSimple.hasActions) {
      schema.getSimple.getActions
    } else if(schema.hasActionsSet && schema.getActionsSet.hasActions) {
      schema.getActionsSet.getActions
    } else {
      new ActionSchemaArray()
    }
  }

  def entityActions = {
    if(schema.hasCollection && schema.getCollection.hasEntity && schema.getCollection.getEntity().hasActions) {
      schema.getCollection.getEntity.getActions
    } else if(schema.hasAssociation && schema.getAssociation.hasEntity && schema.getAssociation.getEntity().hasActions) {
      schema.getAssociation.getEntity.getActions
    } else if(schema.hasSimple && schema.getSimple.hasEntity && schema.getSimple.getEntity().hasActions) {
      schema.getSimple.getEntity.getActions
    } else {
      new ActionSchemaArray()
    }
  }

  def subresources = {
    if(schema.hasCollection && schema.getCollection.hasEntity && schema.getCollection.getEntity.hasSubresources) {
      schema.getCollection.getEntity.getSubresources
    } else if(schema.hasAssociation && schema.getAssociation.hasEntity && schema.getAssociation.getEntity.hasSubresources) {
      schema.getAssociation.getEntity.getSubresources
    } else if(schema.hasSimple && schema.getSimple.hasEntity && schema.getSimple.getEntity.hasSubresources) {
      schema.getSimple.getEntity.getSubresources
    } else {
      new ResourceSchemaArray()
    }
  }

  /**
   * @return Total subresources under this schema
   */
  def subresourceCount: Int = {
    subresources.size() + subresources.asScala.map(_.subresourceCount).sum
  }

  def hasContents = schema.methods.asScala.nonEmpty || schema.actions.asScala.nonEmpty || schema.entityActions.asScala.nonEmpty || schema.finders.asScala.nonEmpty

  def entityPath = {
    if(schema.hasCollection && schema.getCollection.hasEntity) {
      schema.getCollection.getEntity.getPath
    } else if(schema.hasAssociation && schema.getAssociation.hasEntity) {
      schema.getAssociation.getEntity.getPath
    } else if(schema.hasSimple && schema.getSimple.hasEntity) {
      schema.getSimple.getEntity.getPath
    } else {
      ""
    }
  }
}

class RichDataset(dataset: Dataset) {
  def servicesMap : Map[String, Service] = {
    val servicesByName = dataset.getClusters.asScala map { case (clusterName, cluster) =>
      cluster.getServices.asScala.toList map { service =>
        (service.getKey, service)
      }
    }
    servicesByName.flatten.toMap
  }
}

class RichRestRequest(request: RestRequest) {
  lazy val uri = request.getURI
  def buildD2Uri(service: Service): String = {
    if(service.hasD2Service) {
      val d2Service = service.getD2Service
      val d2ServerNameAsPath = s"/${d2Service.getName}"
      if(d2Service.getPath != d2ServerNameAsPath) {
        uri.toString.replaceFirst(d2Service.getPath, d2ServerNameAsPath)
      } else {
        uri.toString
      }
    } else {
      uri.toString
    }
  }

  def method = request.getMethod
  def headers = request.getHeaders

  def exampleUri = {
    uri.toString
      .replaceAll("%7B", "{").replaceAll("%7D", "}")     // single escaped { and }
      .replaceAll("%257B", "{").replaceAll("%257D", "}") // double escaped { and }
  }

  def exampleUriWithMarkup = {
    exampleUri.replaceAll("%5B", """<span class="escaped-left-brace">%5B</span>""")
              .replaceAll("%5D", """<span class="escaped-right-brace">%5D</span>""")
      .replaceAll("%3D", """<span class="escaped-equals">%3D</span>""") // escaped =
      .replaceAll("%26", """<span class="escaped-amperstand">%26</span>""") // escaped &
  }

  private val curlProvidedHeaders = List(
    RestConstants.HEADER_CONTENT_TYPE,
    RestConstants.HEADER_ACCEPT,
    RestConstants.HEADER_RESTLI_PROTOCOL_VERSION)

  def curlExampleHeaders = headers.asScala.filterNot{ case (header, _) =>
    curlProvidedHeaders.contains(header)
  }

  /*lazy val requestBuilder = {
    val requestBuilder = new RestRequestBuilder(uri).setMethod(method.getHttpMethod().toString());

    requestBuilder.setHeaders(headers);
    requestBuilder.setHeader(RestConstants.HEADER_ACCEPT, RestConstants.HEADER_VALUE_APPLICATION_JSON)
    if(input.isDefined) {
      requestBuilder.setHeader(RestConstants.HEADER_CONTENT_TYPE, RestConstants.HEADER_VALUE_APPLICATION_JSON)
    }
    requestBuilder.setHeader(RestConstants.HEADER_RESTLI_PROTOCOL_VERSION, protocolVersion.toString())
    if (method.getHttpMethod() == HttpMethod.POST)
    {
      requestBuilder.setHeader(RestConstants.HEADER_RESTLI_REQUEST_METHOD, method.toString())
    }
    requestBuilder
  }*/

  def input = {
    if(request.getEntity == null || request.getEntity.length() == 0) None
    else Some(new JSONObject(new String(request.getEntity.copyBytes(), "UTF-8")).toString(2))
  }
}

class RichRestResponse(response: RestResponse) {
  def status = response.getStatus
  def headers = response.getHeaders
  def body = {
    if(response.getEntity == null || response.getEntity.length() == 0) None
    else Some(new JSONObject(new String(response.getEntity.copyBytes(), "UTF-8")).toString(2))
  }
}

class RichServiceProvidedRequestResponse(serviceProvidedRequestResponse: ServiceProvidedRequestResponse) {
  def serviceIdentifier = serviceProvidedRequestResponse.getServiceIdentifier

  def buildRequest(httpMethod: String, methodName: String) = {
    val reqRes = serviceProvidedRequestResponse.getMethodRequestResponse.get(methodName)
    httpMethod + " " + reqRes.getUrl + "\n\n" + formatHeaders(reqRes.requestHeaders) + "\n\n" + reqRes.getRequestBody
  }

  def buildResponse(methodName: String) = {
    val reqRes = serviceProvidedRequestResponse.getMethodRequestResponse.get(methodName)
    formatHeaders(reqRes.responseHeaders) + "\n\n" + reqRes.getResponseBody
  }

  def formatHeaders(headers: collection.Map[String, String]): String = {
    headers.map{case(k, v) => k + ":" + v }.mkString("\n")
  }
}

class RichRequestResponse(requestResponse: RequestResponse) {
  private val defaultRequestHeaders = Map("X-Restli-Protocol-Version" -> "1.0.0", "Accept" -> "application/json")
  private val defaultResponseHeaders = Map("X-Restli-Protocol-Version" -> "1.0.0", "Content-Type" -> "application/json")

  def requestHeaders: collection.Map[String, String] = {
    return defaultRequestHeaders ++ requestResponse.getRequestHeaders.asScala
  }

  def responseHeaders: collection.Map[String, String] = {
    return defaultResponseHeaders ++ requestResponse.getResponseHeaders.asScala
  }
}
