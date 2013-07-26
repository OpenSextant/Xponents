/** <!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01//EN" "http://www.w3.org/TR/html4/strict.dtd">
<html>
  <head>
    <meta http-equiv="content-type" content="text/html; charset=UTF-8">
    <title>XCoord: Geographic Coordinate Extraction</title>
  </head>
  <body>
    <h1> XCoord: Geographic Coordinate Extraction </h1>
    XCoord is a developer toolkit for extracting 3 major forms of
    coordinate patterns from any textual data: <br>
    <ul>
      <li> UTM - Universal Transverse Mercator<br>
      </li>
      <li>MGRS - Military Grid Reference System<br>
      </li>
      <li>Degrees, Minutes, Seconds variants (DD, DM, DMS</li>
    </ul>
    <p>XCoord allows the user to define their own coordinate patterns or
      extend the default patterns.<br>
    </p>
    <h1>Usage</h1>
    <p>From the command line you can quickly test XCoord on a set of
      given test cases or provide a file of your own.<br>
    </p>
    <pre style="margin-left: 40px;">ant test <br>file?&nbsp;&nbsp;&nbsp; test/mytest.txt<br></pre>
    <pre style="margin-left: 40px;">ant test-default</pre>
    <p>... runs internal unit tests coupled with the given patterns
      configuration file<br>
    </p>
    <p>Programmatically, the essential usage is:<br>
    </p>
    <pre style="margin-left: 40px;">XCoord xc = new XCoord();<br>xc.configure();<br>GeocodingResult geocodes = xc.extract_coordinates(text, text_id);<br></pre>
    <p>Tuning peformance happens at many levels.&nbsp; XCoord can toggle
      each coordinate pattern family: UTM, MGRS, DM, DMS, DD if there
      are limited or known formats desired.&nbsp; As well, for embedding
      XCoord into other systems (such as its parent project
      OpenSextant), the constructor can take a configuration file, for
      example, <span style="font-family: monospace;">xc.configure(
        "mypatterns.cfg")</span>. Such configuration files must be in
      the CLASSPATH currently.</p>
    <p>When Interpreting GeocodingResults the caller of XCoord should
      check if an individual match is a submatch (<span
        style="font-style: italic; font-family: monospace;">GeocoordMatch</span><span
        style="font-family: monospace;">.</span><span
        style="font-weight: bold;"><span style="font-family: monospace;">is_submatch</span>)</span>
      or not.&nbsp; While each pattern is assessed individually, there
      may be multiple matches resulting in overlapping
      annotations.&nbsp; The intention is that the longest distinct
      match is most relevant for any given span of text.&nbsp; Although
      in some uses all matches are worth seeing.&nbsp; To be clear,
      matches that are contained entirely within other matches are
      marked as submatches and therefore less likely to be the item of
      interest for geocoding. Other matches may overlap (<span
        style="font-family: monospace;">GeocoordMatch.is_overlap = true</span>)<br>
    </p>
    <h1>Pattern Definition</h1>
    <p>FlexPat (derived from a few other MITRE efforts) allows XCoord to
      design the coordinate patterns as regular expressions, using named
      pattern groups.&nbsp; As of Java version 6, the Java regular
      expression (regex) capability does not allow the full regex
      grammar, including naming pattern groups.&nbsp; FlexPat was
      designed to address this gap in functionality as well as to
      provide a foundation for simple text matches, pattern definition,
      and pattern test cases.&nbsp; See documentation in XCoord's
      PatternManager.<br>
    </p>
    <p><br>
    </p>
    <h1>Runtime Flags and Optimization</h1>
    <p>The use of configuration file parameters suggests that you have
      one value for a parameter at runtime through the duration of the
      current process.&nbsp;&nbsp; Since processing may be
      context-sensitive, we use static runtime flags (a bit mask of
      flags from XConstants) to influence and tune
      behavior.&nbsp;&nbsp;&nbsp; Current flags include toggling
      coordinate pattern families and the option to extract context
      text.<br>
    </p>
    <pre>&nbsp;&nbsp;&nbsp; XCoord.RUNTIME_FLAGS ^= XConstants.FILTER_DMS_ON // Turn OFF DMS filters using XOR</pre>
    <pre>&nbsp;   XCoord.RUNTIME_FLAGS |= XConstants.FLAG_ALL_FILTERS // return to default filter behavior with all filters.</pre>
    <pre>&nbsp;   XCoord.RUNTIME_FLAGS = XConstants.FLAG_ALL_FILTERS  // return to default behavior with all filters.</pre>
    <p><br>
      Other FLAG parameters will be added over time to allow XCoord
      behavior to be adapted at runtime.<br>
      <br>
    </p>
    <p><br>
    </p>
    <p><br>
    </p>
    <h1><br>
    </h1>
  </body>
</html>
*/

package org.mitre.xcoord;
