package com.linkedin.restsearch.template.utils

import com.linkedin.restli.restspec._
import com.linkedin.restsearch.{Dataset, ClusterSource, Cluster, Service}
import scala.Some
import scala.collection.JavaConversions._
import com.linkedin.restsearch.template.utils.Conversions._
import com.linkedin.restsearch.server.{SnapshotSchemaResolver, ServiceModelsSchemaResolver}
import com.linkedin.restliexplorer.D2Uri
import com.linkedin.data.schema.DataSchemaResolver
import org.json.JSONObject
import com.linkedin.restli.docgen.examplegen.ExampleRequestResponseGenerator
import com.linkedin.r2.message.rest.{RestResponse, RestRequest}
import com.linkedin.restli.common.RestConstants
import java.lang.IllegalArgumentException

/**
 * Use implicit conversions to mix in additional utility methods to the data template classes used by go/restli.
 */
object Conversions {
  implicit def toRichDataset(dataset: Dataset) = new RichDataset(dataset)
  implicit def toRichCluster(cluster: Cluster) = new RichCluster(cluster)
  implicit def toRichService(service: Service) = new RichService(service)
  implicit def toRichResourceSchema(schema: ResourceSchema) = new RichResourceSchema(schema)
  implicit def toRichFinderSchema(schema: FinderSchema) = new RichFinderSchema(schema)
  implicit def toRichActionSchema(schema: ActionSchema) = new RichActionSchema(schema)
  implicit def toRichRestMethodSchema(schema: RestMethodSchema) = new RichRestMethodSchema(schema)
  implicit def toRichRestRequest(request: RestRequest) = new RichRestRequest(request)
  implicit def toRichRestResponse(response: RestResponse) = new RichRestResponse(response)
}


trait ColoVariantAware {
  val knownColoVariantSuffixes = List("Master", "-EI", "-EI1", "-EI2", "-LVA1", "-ECH3", "-ELA4")

  def coloVariantIdentifier: String
  def isPrimaryColoVariant: Boolean

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

class RichService(service: Service) extends ColoVariantAware {
  override def coloVariantIdentifier = service.getKey
  def isPrimaryColoVariant = /*(service.hasDefaultService && service.isDefaultService) ||*/ !isColoVariant

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
        subresources.map { subresource =>
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
      Some(new ExampleRequestResponseGenerator(service.getParents.map(_.getResourceSchema), service.getResourceSchema, resolver))
    } catch {
      case iae: IllegalArgumentException => None // workaround until fix in https://rb.corp.linkedin.com/r/255178/ is rolled out
      case e => throw e
    }
  }

  private def createServiceForSubresource(key: String, schema: ResourceSchema, parent: Service, rootService: Service) = {
    val currentService = new Service() // key, path, clusters, protocol, resourceSchema, models
    currentService.setKey(key)
    currentService.setPath(schema.getPath())
    currentService.setResourceSchema(schema)
    currentService.setClusters(rootService.getClusters())
    currentService.setProtocol(rootService.getProtocol())
    currentService.setModels(rootService.getModels())
    currentService.setParent(parent)
    currentService
  }
}

class RichCluster(cluster: Cluster) extends ColoVariantAware {

  def getResolver(service: Service) = {
    if(cluster.getSource == ClusterSource.D2) {
      new ServiceModelsSchemaResolver(service)
    } else {
      new SnapshotSchemaResolver(service)
    }
  }

  override def coloVariantIdentifier = cluster.getName
  def isPrimaryColoVariant = !isColoVariant || cluster.getServices.toList.exists(_.isPrimaryColoVariant)

  // finds all other services in this cluster that are variants of the given service
  def coloVariants(primary: Service) = {
    val primaryKey = primary.getKey
    val variants = cluster.getServices.filter { service =>
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
    val services = cluster.getServices.toList
    if(primaryOnly) services.filter(_.isPrimaryColoVariant) else services
  }

  def uris: List[D2Uri] = {
    if(!cluster.hasD2Cluster || !cluster.getD2Cluster().hasServices()) List()
    else cluster.getD2Cluster.getUris.toList
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
        "(Internal to Superblock)"
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
    subresources.size() + subresources.map(_.subresourceCount).sum
  }

  def hasContents = schema.methods.nonEmpty || schema.actions.nonEmpty || schema.entityActions.nonEmpty || schema.finders.nonEmpty

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
    val servicesByName = mapAsScalaMap(dataset.getClusters) map { case (clusterName, cluster) =>
      cluster.getServices.toList map { service =>
        (service.getKey, service)
      }
    }
    servicesByName.flatten.toMap
  }
}

class RichRestRequest(request: RestRequest) {
  lazy val uri = request.getURI
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

  private val curliProvidedHeaders = List(
    RestConstants.HEADER_CONTENT_TYPE,
    RestConstants.HEADER_ACCEPT,
    RestConstants.HEADER_RESTLI_PROTOCOL_VERSION)

  def curliExampleHeaders = headers.filterNot{ case (header, _) =>
    curliProvidedHeaders.contains(header)
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