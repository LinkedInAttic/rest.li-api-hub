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

package com.linkedin.restsearch.search

import com.linkedin.restsearch._
import com.linkedin.restli.restspec._
import play.api.Logger
import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.document._
import org.apache.lucene.index._
import org.apache.lucene.queryparser.classic.{ParseException, QueryParser}
import org.apache.lucene.search._
import org.apache.lucene.store._
import org.apache.lucene.util.Version
import scala.collection.JavaConversions._
import scala.collection.mutable
import com.linkedin.data.template.DataTemplateUtil
import com.linkedin.data.schema.DataSchema
import java.lang.StringBuilder
import com.linkedin.restsearch.template.utils.Conversions._
import template.utils.MethodSchemaPart
import org.apache.lucene.document.DateTools.Resolution
import java.util.Date

object LuceneSearchIndex {
  val luceneVersion = Version.LUCENE_42
  val searchAnalyzer: Analyzer = new RestSearchAnalyzer(luceneVersion)
  val analyzer: Analyzer = new RestIndexingAnalyzer(luceneVersion)
}

/**
 * Indexes and searches datasets (idl and data schemas).  Indexes are stored in memory.  Each time index is called, it creates
 * a new index and swaps the old one out for the new one.
 *
 * @author jbetz
 *
 */
class LuceneSearchIndex extends SearchIndex {
  import LuceneSearchIndex._

  var reader : DirectoryReader = null
  val maxQueryResults = 10000;

  override def search(queryString: String): List[String] = {
    val trimmedQueryString = queryString.trim().replaceAll("_", "")
    val isearcher = new IndexSearcher(reader)
    val parser = new QueryParser(luceneVersion, "keywords", searchAnalyzer)
    parser.setAllowLeadingWildcard(true)
    Logger.info("search query: " + queryString)
    val originalQuery = try {
      parser.parse(trimmedQueryString)
    } catch {
      case e: ParseException => {
        // if a parsing error, it's likely the query is not a valid lucene query, maybe it has an opening "(" but not
        // a closing one.  We can fall back by quoting the search terms and doing a literal search in these cases.
        parser.parse("\"" + trimmedQueryString + "\"")
      }
    }

    val query = rewriteForColoVariants(originalQuery)

    val top = isearcher.search(query, maxQueryResults, new Sort(SortField.FIELD_SCORE))
    Logger.info("search hit count: " + top.scoreDocs.size)
    val results = refArrayOps(top.scoreDocs) map { hit =>
      isearcher.doc(hit.doc).get("key")
    }
    Logger.info("search document match count: " + results.size)
    refArrayOps(results).toList
  }

  override def getCountForTerm(queryString: String): Int = {
    val trimmedQueryString = queryString.trim().replaceAll("_", "")
    val isearcher = new IndexSearcher(reader)
    val parser = new QueryParser(luceneVersion, "keywords", searchAnalyzer)
    val query = rewriteForColoVariants(parser.parse(trimmedQueryString))
    val top = isearcher.search(query, maxQueryResults)
    top.totalHits
  }

  // unless the query explicitly searches by isColoVariant or coloVariant term, we exclude colo variants
  private def rewriteForColoVariants(queryIn: Query): Query = {
    val overrideTerms = List("isColoVariant", "coloVariant");
    try {
      val terms = new java.util.HashSet[Term]()
      queryIn.extractTerms(terms)
      if(terms.exists(term => overrideTerms.contains(term.field()))) { // run the exact query provided
        queryIn
      } else { // rewrite query to exclude colo variants
        val bq = new BooleanQuery();
        bq.add(queryIn, BooleanClause.Occur.MUST)
        bq.add(new TermQuery(new Term("isColoVariant", "false")), BooleanClause.Occur.MUST)
        bq
      }
    } catch {
      case e: UnsupportedOperationException => {
        Logger.error("unable to rewrite query: " + queryIn.toString, e)
        queryIn
      }
    }
  }

  override def index(dataset: Dataset): List[String] = {
    val directory = new RAMDirectory()
    val config = new IndexWriterConfig(luceneVersion, analyzer)
    val indexWriter =  new IndexWriter(directory, config)
    val start = System.nanoTime()
    val keys = for {
      cluster <- dataset.getClusters.values()
      service <- asScalaBuffer(cluster.getServices).par // index in parallel
      schema = service.getResourceSchema()
    } yield {
      val key = service.keysToResource.mkString(".")
      Logger.info("Indexing " + key)
      index(indexWriter, key, service, schema)
      key
    }
    val end = System.nanoTime()
    Logger.info("indexing time: " + ((end-start)/1000000000.0) + " seconds ")
    Logger.info("numDocs: " + indexWriter.numDocs())
    indexWriter.commit()
    indexWriter.close()
    Logger.info("index size: " + directory.listAll().length)
    Logger.info("index: " + refArrayOps(directory.listAll()).mkString)
    reader = DirectoryReader.open(directory)
    Logger.info("reader doc count:" + reader.numDocs())
    keys.toList
  }

  private def hasComplexKey(cluster: Cluster, service: Service, identifier: IdentifierSchema): Boolean = {
    val resolver = cluster.getResolver(service)
    val identifierType = identifier.getType()

    // inline schema
    val schema = if (identifierType.startsWith("{")) {
      Some(DataTemplateUtil.parseSchema(identifierType, resolver))
    } else { // look for the model in the service
      Option(resolver.findDataSchema(identifierType, new StringBuilder))
    }

    schema match {
      case None => false
      case Some(dataSchema: DataSchema) => dataSchema.getDereferencedDataSchema.getType == DataSchema.Type.RECORD
    }
  }

  private def index(indexWriter: IndexWriter, key: String, service: Service, schema: ResourceSchema, isSubresource: Boolean = false): Unit = {
    val doc = new Document()
    val keywords = mutable.ListBuffer[String]()

    doc.add(new Field("key", key, TextField.TYPE_STORED))
    keywords += key

    if(service.getClusters().size > 0) {
      val cluster = service.getClusters().get(0)
      doc.add(new Field("cluster", key, TextField.TYPE_STORED))
      keywords += cluster.getName()
    }

    doc.add(new Field("isSubresource", isSubresource.toString, TextField.TYPE_NOT_STORED))

    doc.add(new Field("protocol", service.getProtocol().toString(), TextField.TYPE_STORED))

    if(service.hasCreatedAt) {
      doc.add(new Field("createdDate", DateTools.dateToString(new Date(service.getCreatedAt), Resolution.DAY), TextField.TYPE_NOT_STORED))
    }

    if(service.hasModifiedAt) {
      doc.add(new Field("modifiedDate", DateTools.dateToString(new Date(service.getModifiedAt), Resolution.DAY), TextField.TYPE_NOT_STORED))
    }

    if (schema != null) {
      if (schema.hasName()) {
        doc.add(new Field("resource", schema.getName(), TextField.TYPE_STORED))
        keywords += schema.getName()
      }
      if (schema.hasPath()) {
        keywords += schema.getPath()
      }
      if (schema.hasDoc()) {
        keywords += schema.getDoc()
      }
      if (schema.hasNamespace()) {
        keywords += schema.getNamespace()
      }

      doc.add(new Field("isColoVariant", (!service.isPrimaryColoVariant).toString, TextField.TYPE_NOT_STORED))
      if(!service.isPrimaryColoVariant) {
        doc.add(new Field("coloVariant", service.coloVariantSuffix, TextField.TYPE_NOT_STORED))
      }

      doc.add(new Field("isDeprecated", schema.isDeprecated.toString, TextField.TYPE_NOT_STORED))
      doc.add(new Field("isInternalToSuperblock", schema.isInternalToSuperblock.toString, TextField.TYPE_NOT_STORED))

      if (schema.hasSchema()) {
        val schemaFqn = schema.getSchema()
        val schemaSimpleName = extractSimpleName(schema.getSchema())
        //doc.add(new Field("model", schemaSimpleName, TextField.TYPE_NOT_STORED)) // TODO: add support for model search, needs to be more extensive than just this search field (which does NOT work if uncommented)
        keywords += schemaSimpleName
        //doc.add(new Field("modelFullname", schemaFqn, TextField.TYPE_NOT_STORED))
        keywords += schemaFqn
      }

      if (schema.hasCollection()) {
        doc.add(new Field("isCollection", "true", TextField.TYPE_NOT_STORED))
        val collection = schema.getCollection()

        if (service.hasClusters) {
          if (hasComplexKey(service.getClusters.get(0), service, collection.getIdentifier())) {
            doc.add(new Field("hasComplexKey", "true", TextField.TYPE_NOT_STORED))
          }
        }

        if (collection.hasEntity()) {
          val entity = collection.getEntity()
          indexEntity(doc, keywords, indexWriter, service, entity)
        }

        if (collection.hasIdentifier()) {
          val identifier = collection.getIdentifier()
          indexIdentifier(doc, keywords, identifier)
        }
      }
      if (schema.hasAssociation()) {
        doc.add(new Field("isAssociation", "true", TextField.TYPE_NOT_STORED))
        val association = schema.getAssociation()

        if (association.hasEntity()) {
          val entity = association.getEntity()
          indexEntity(doc, keywords, indexWriter, service, entity)
        }

        if (association.hasAssocKeys()) {
          val assocKeys = association.getAssocKeys()
          indexAssocKeys(doc, keywords, assocKeys)
        }
      }
      if (schema.hasActionsSet()) {
        doc.add(new Field("isActionSet", "true", TextField.TYPE_NOT_STORED))
      }
      if (schema.hasSimple()) {
        doc.add(new Field("isSimple", "true", TextField.TYPE_NOT_STORED))
      }

      if (schema.methods.nonEmpty) {
        indexMethods(doc, keywords, schema.methods);
      }

      if (schema.actions.nonEmpty) {
        indexActions(doc, keywords, schema.actions)
      }

      if (schema.finders.nonEmpty) {
        indexFinders(doc, keywords, schema.finders)
      }
    }
    doc.add(new Field("keywords", keywords.mkString(" "), TextField.TYPE_NOT_STORED))
    indexWriter.addDocument(doc)
  }

  private def indexIdentifier(doc: Document, keywords: mutable.ListBuffer[String], identifier: IdentifierSchema) = {
    if (identifier.hasName) {
      keywords += identifier.getName
    }
    if (identifier.hasType) {
      keywords += identifier.getType
      keywords += extractSimpleName(identifier.getType)
    }
    if (identifier.hasParams) {
      keywords += identifier.getParams
    }
  }

  private def indexFinders(doc: Document, keywords: mutable.ListBuffer[String], finders: FinderSchemaArray) = {
    if(finders.nonEmpty) {
      doc.add(new Field("hasFinder", "true", TextField.TYPE_NOT_STORED))
    }

    val finderNames = new mutable.ListBuffer[String]
    for (finder <- asScalaBuffer(finders)) {
      indexMethodAnnotations(doc, keywords, finder)
      indexFinder(keywords, finder)
      finderNames += finder.getName
    }
    doc.add(new Field("finder", finderNames.mkString(" "), TextField.TYPE_NOT_STORED))
  }

  private def indexFinder(keywords: mutable.ListBuffer[String], finder: FinderSchema) = { // TODO: parameters, assocKeys, annotations
    keywords += finder.getName
    keywords += "finder"
    if (finder.hasDoc) {
      keywords += finder.getDoc
    }
    if (finder.hasParameters) {
      indexParameters(keywords, finder.getParameters)
    }
    if (finder.hasMetadata) {
      val metadata = finder.getMetadata
      if(metadata.hasType) {
        keywords += metadata.getType
        keywords += extractSimpleName(metadata.getType)
      }
    }
  }

  private def indexMethodAnnotations(doc: Document, keywords: mutable.ListBuffer[String], annotated: MethodSchemaPart) {
    doc.add(new Field("hasDeprecated", annotated.isDeprecated.toString, TextField.TYPE_NOT_STORED))
    doc.add(new Field("hasTestMethod", annotated.isTestMethod.toString, TextField.TYPE_NOT_STORED))
  }

  private def indexMethods(doc: Document, keywords: mutable.ListBuffer[String], methods: RestMethodSchemaArray) {
    val methodNames = new mutable.ListBuffer[String]
    for (method <- asScalaBuffer(methods)) {
      indexMethodAnnotations(doc, keywords, method)
      indexMethod(keywords, method)
      methodNames += method.getMethod().replaceAll("_", "")
    }
    doc.add(new Field("method", methodNames.mkString(" "), TextField.TYPE_NOT_STORED))
  }

  private def indexMethod(keywords: mutable.ListBuffer[String], method: RestMethodSchema) {
    keywords += method.getMethod.replaceAll("_", "")
    if (method.hasDoc) {
      keywords += method.getDoc
    }
    if (method.hasParameters) {
      indexParameters(keywords, method.getParameters)
    }
  }

  private def indexAssocKeys(doc: Document, keywords: mutable.ListBuffer[String], assocKeys: AssocKeySchemaArray) {
    val keys = new mutable.ListBuffer[String]
    for (assocKey <- assocKeys) {
      if (assocKey.hasName) {
        keywords += assocKey.getName
        keys += assocKey.getName
      }
      if (assocKey.hasType) {
        keywords += assocKey.getType
        keywords += extractSimpleName(assocKey.getType)
      }
    }
    doc.add(new Field("assocKeys", keys.mkString(" "), TextField.TYPE_NOT_STORED))
  }

  private def indexEntity(doc: Document, keywords: mutable.ListBuffer[String], indexWriter: IndexWriter, service: Service, entity: EntitySchema) = {
    if(entity.hasActions && entity.getActions.nonEmpty) {
      doc.add(new Field("hasEntityAction", "true", TextField.TYPE_NOT_STORED))
      indexActions(doc, keywords, entity.getActions)
    }
    service.allSubresourcesAsServices map { subresource =>
      index(indexWriter, subresource.keysToResource.mkString("."), subresource, subresource.getResourceSchema, isSubresource = true)
    }
  }

  private def indexActions(doc: Document, keywords: mutable.ListBuffer[String], actions: ActionSchemaArray) = {
    if(actions.nonEmpty) {
      doc.add(new Field("hasAction", "true", TextField.TYPE_NOT_STORED))
    }
    val actionNames = new mutable.ListBuffer[String]
    for (action <- asScalaBuffer(actions)) {
      indexMethodAnnotations(doc, keywords, action)
      indexActionSchema(keywords, action)
      actionNames += action.getName
    }
    doc.add(new Field("action", actionNames.mkString(" "), TextField.TYPE_NOT_STORED))
  }

  private def indexActionSchema(keywords: mutable.ListBuffer[String], action: ActionSchema) = {
    keywords += action.getName
    keywords += "action"
    if (action.hasDoc) {
      keywords += action.getDoc
    }
    if (action.hasParameters) {
      indexParameters(keywords, action.getParameters)
    }
    if(action.hasReturns) {
      keywords += action.getReturns
    }
  }

  private def indexParameters(keywords: mutable.ListBuffer[String], parameters: ParameterSchemaArray) {
    for (parameter <- asScalaBuffer(parameters)) {
      if (parameter.hasName) {
        keywords += parameter.getName
      }
      if (parameter.hasDoc) {
        keywords += parameter.getDoc
      }
      if (parameter.hasType) {
        keywords += parameter.getType
        keywords += extractSimpleName(parameter.getType)
      }
      if (parameter.hasItems) {
        keywords += parameter.getItems
      }
    }
  }

  private def extractSimpleName(fqn: String) = {
    val parts = fqn.split(".")
    if(parts.nonEmpty) parts.last
    else fqn
  }
}