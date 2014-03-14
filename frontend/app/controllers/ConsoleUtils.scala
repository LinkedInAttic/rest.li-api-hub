package controllers

import scala.util.matching.Regex.Match
import scala.util.matching.Regex


trait ConsoleUtils {
  /**
   * Precompile a regex which captures everything before a possibly whitespace-preceded first colon as 'headerName',
   * and everything after (aside from some possible whitespace) as 'headerBody'.
   *
   * Capturing groups are are named for clarity.
   * We omit leading/trailing \s* (and do not have to make the final .* a non-greedy matcher) because we are doing a
   * trim first.  The first group does effectively need to do some backtracking, so we use a non-greedy matcher.
   */

  val headerNameBodyPattern = new Regex("""^([^:]+?)\s*:\s*(.*)$""", "headerName", "headerBody")

  /**
   * Given a string which is presumed to come from the console form (a single blob, with newline-separated headers),
   * return a sequence of (header name, header value) tuples.
   *
   * Separated from Application for easy testing without bringing up an entire fake application.
   */
  def parseHeadersFromConsole(consoleRequestHeaders : String): Seq[(String, String)] =
    consoleRequestHeaders.split("\n").map(_.trim).filter(_.length() > 0).map(headerNameBodyPattern.findFirstMatchIn(_) match {
      case x: Some[Match] => (x.get.group("headerName"), x.get.group("headerBody"))
      case None => throw new IllegalArgumentException("Malformed headers")
    }).toSeq
}
