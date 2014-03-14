package com.linkedin.restsearch.search

import java.io.Reader
import org.apache.lucene.analysis._
import org.apache.lucene.analysis.Analyzer.TokenStreamComponents
import org.apache.lucene.analysis.core._
import miscellaneous.WordDelimiterFilter
import org.apache.lucene.util.Version

/**
 * Lucene analyzer used to normalize the search query string.
 * 
 * @author jbetz
 *
 */
class RestSearchAnalyzer(version: Version) extends Analyzer {

  override def createComponents(fieldName: String, reader: Reader): TokenStreamComponents = {
    val source = new WhitespaceTokenizer(version, reader)
    val result = new WordDelimiterFilter(source, WordDelimiterFilter.STEM_ENGLISH_POSSESSIVE, null)
    val resultWithCasing = new LowerCaseFilter(version, result)
    val resultWithCasingAndStopwords = new StopFilter(version, resultWithCasing, StopAnalyzer.ENGLISH_STOP_WORDS_SET)
    new TokenStreamComponents(source, resultWithCasingAndStopwords)
  }
}

/**
 * Lucene analyzer used during indexing.  It is configured to break up names in camel case, like
 * resources and data schemas names, so keyword search works better against them.
 *
 * @author jbetz
 *
 */
class RestIndexingAnalyzer(version: Version) extends Analyzer {

  override def createComponents(fieldName: String, reader: Reader): TokenStreamComponents = {
    val source = new WhitespaceTokenizer(version, reader)
    val result = new WordDelimiterFilter(source,
      WordDelimiterFilter.CATENATE_WORDS
        | WordDelimiterFilter.GENERATE_WORD_PARTS
        | WordDelimiterFilter.PRESERVE_ORIGINAL
        | WordDelimiterFilter.SPLIT_ON_CASE_CHANGE
        | WordDelimiterFilter.SPLIT_ON_NUMERICS
        | WordDelimiterFilter.STEM_ENGLISH_POSSESSIVE, null)

    val resultWithCasing = new LowerCaseFilter(version, result)
    val resultWithCasingAndStopwords = new StopFilter(version, resultWithCasing, StopAnalyzer.ENGLISH_STOP_WORDS_SET)
    new TokenStreamComponents(source, resultWithCasingAndStopwords)
  }
}