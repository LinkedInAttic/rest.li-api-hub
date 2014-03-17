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