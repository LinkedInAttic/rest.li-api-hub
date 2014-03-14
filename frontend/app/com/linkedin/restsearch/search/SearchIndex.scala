package com.linkedin.restsearch.search

import com.linkedin.restsearch.Dataset
import com.linkedin.restsearch.Protocol

/**
 * Provides an interface for batch reindexing and searching datasets (idl and data schemas).
 * 
 * @author jbetz
 *
 */
trait SearchIndex {
  def index(dataset: Dataset): List[String]
  def search(queryString: String): List[String]
  def getCountForTerm(term: String): Int
}
