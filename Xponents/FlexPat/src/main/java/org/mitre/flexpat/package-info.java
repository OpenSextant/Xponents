/** <!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN">
<html>
  <head>
     <meta http-equiv="content-type" content="text/html; charset=UTF-8">
  </head>
  <body>
    <h1> FlexPat -- A Pattern Definition &amp; Testing Library</h1>
    <br>
    FlexPat is a pattern-based extractor that allows you to define
    regular expressions (RE or regex) along with the test data that you
    believe should be matched.&nbsp;&nbsp; Part of the "features" of
    FlexPat is due to a deficiency in Java's RE support:&nbsp; Java SDK
    does not support named groups. &nbsp; FlexPat solves this be
    defining fields (aka RE groups) that are used to compose more
    complex patterns.&nbsp; The fields are sub-patterns themselves and
    serve two purposes:<br>
    <ul>
      <li>They keep your pattern library organized and more
        object-oriented and reusable.&nbsp; e.g., once you define a
        field for a date pattern, you can reuse that by naming it where
        you need it.</li>
      <li>They help you recall fields from matches so you can
        post-process matches, e.g., for normalization and other stuff.</li>
    </ul>
    <p>A config file is processed by <tt>RegexPatternManager</tt>.&nbsp;

      The file consists of DEFINES, RULES, and TESTS. <br>
    </p>
    <p>DEFINE&nbsp; -- a field name and a valid RE pattern.&nbsp;&nbsp;
      <br>
    </p>
    <pre>&nbsp;&nbsp;&nbsp;&nbsp; #DEFINE field pattern</pre>
    <p>RULE and TEST -- a valid RE pattern that defines things you wish
      to match.&nbsp; &lt;field&gt; must be valid fields DEFINEd ahead
      of time.&nbsp; RULEs are enumerated within a family of
      rules.&nbsp; <tt>RegexPatternManager</tt> and your implementation
      should allow the enabling/disabling of families of rules as well
      as individual rules.&nbsp;&nbsp; RULEs are immediately followed by
      TEST cases that share the family and enumeration of a given rule.<br>
    </p>
    <pre>	#RULE name  family enum  pattern<br>	#TEST name  family enum  data<br><br>name, family and enum are code keywords with no white space. Enumerations are <br>any alphanumeric string, however ease of use, they are typically numbers followed by a few <br>alphabetic characters as modifiers.   <br><br>pattern := RE, which is any valid combination of &lt;field&gt; and RE expressions excluding RE groups.<br>   That is, RULE patterns may not contain additional unnamed/un-DEFINED groups.  The use of "(group)" <br>   in a RULE will cause Flexpat to fail.<br><br>TEST data := is any string of characters.  $NL typically is used to represent a \n character <br>   which should be inserted during testing.  FlexPat does not do this -- the caller must handle this. <br>   This is only a convention.   Data may also contain an optional comment.  Again, this is a convention<br>   The caller should know what do do with the comment.  By convention, if the comment/data includes the<br>   term "FAIL" this is used to imply the test represents a true negative, i.e., do not match or do not<br>   parse as a true positive.    </pre>
    <p>DEFINES and RULES being RE strings, they are escaped properly
      within <tt>RegexPatternManager</tt> -- you the user do not need
      to escape tokens for the programming language, e.g., "\s+" is
      sufficient -- "\\s+" is not needed to escape the "\" modifier.<br>
    </p>
    <br>
    Core objects include the following:<br>
    <br>
    <ul>
      <li><b>Defining patterns</b></li>
      <ul>
        <li><b>RegexPatternManager</b> -- the central pattern manager as
          describe above. It takes a config file as a URL or file.
          DEFINEs are ephemeral -- after RULE creation defines are not
          used after initialization.<br>
        </li>
        <li><b>PatternTestCase</b> -- maps to the TEST objects.<br>
        </li>
        <li><b>RegexPattern</b> -- maps to the RULE objects.<br>
        </li>
      </ul>
      <li><b>Managing matching operations</b></li>
      <ul>
        <li><b>SpanAnnotation</b> -- a trivial class that has a text
          match value and start/end offsets.</li>
        <li><b>TextMatch</b> -- an instance of SpanAnnotation that
          associates the match with a particular rule.</li>
        <li><b>TextMatchResult</b> -- a group of TextMatch objects from
          any set of rules. </li>
      </ul>
      <li><b>Helpers</b></li>
      <ul>
        <li><b>FileUtility</b> -- IO helpers;&nbsp; Migrating to Apache
          Commons IO where possible.<br>
        </li>
        <li><b>TextUtils</b> -- main utility is to generate match IDs
          (i.e., an MD5 hash ID) and safely getting the window around
          text match for context.<br>
        </li>
      </ul>
    </ul>
    <br>
    <h1> Implementation</h1>
    Subclass <b>RegexPatternManager </b>implementing the <b>create_pattern</b>,
    <b>validate_pattern</b>, <b>enable_pattern</b> and <b>create_testcase
    </b>methods.&nbsp;&nbsp;&nbsp; These are specific to your
    patterns.&nbsp;&nbsp; Your own patterns will sub-class from
    RegexPattern, optionally test cases can sub-class
    PatternTestCase.&nbsp; <br>
    <br>
    SEE Also:&nbsp; XCoord and XTemp implementations.<br>
    <br>
    <pre>class MyPattern extends RegexPattern {</pre>
    <pre>&nbsp;&nbsp;&nbsp; public String attr = null;<br>}<br>...<br>myManager.create_pattern( "MYFAM", "09a", "A rule for matching not much")</pre>
    <br>
    Would create a MyPattern instance with the data above. <br>
    <br>
    Starting up your application should look like this:<br>
    <pre>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; patterns = new MyPatternManager(new URL("/path/to/my-patterns.cfg"));</pre>
    <pre>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; patterns.testing = debug;</pre>
    <pre>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; patterns.initialize();</pre>
    <br>
    Using your patterns manager should look like a loop -- which loops
    through and evaluates all enabled patterns.&nbsp; That is, at
    runtime or compile time you can decide in your app how to all users
    or integrators how to enable or disable rules.&nbsp; FlexPat does
    not consider how you implement this -- it simple requires you
    implement a per-rule toggler, enable_pattern( &lt;rule-id&gt; ).<br>
    <br>
    <tt>/**&nbsp; For tracking purposes you should assign each text
      object to a text ID. <br>
      &nbsp;*&nbsp; TextMatches and results can then be associated with
      text by this ID</tt><tt><br>
    </tt><tt>&nbsp;*/</tt><tt><br>
    </tt><tt>&nbsp;&nbsp;&nbsp; public MyPatternResult
      extract_mystuff(String text, String text_id) {</tt><tt><br>
    </tt><tt><br>
    </tt><tt>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; int bufsize =
      text.length();</tt><tt><br>
    </tt><tt><br>
    </tt><tt>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; MyPatternResult
      results = new MyPatternResult();</tt><tt><br>
    </tt><tt>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
      results.result_id = text_id;</tt><tt><br>
    </tt><tt>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; results.matches
      = new ArrayList&lt;TextMatch&gt;();</tt><tt><br>
    </tt><tt><br>
    </tt><tt>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; for
      (RegexPattern repat : patterns.get_patterns()) {</tt><tt><br>
    </tt><tt><br>
    </tt>
    <blockquote>
      <blockquote><tt>&nbsp;/* if repat is enabled, evaluate it. </tt><br>
        <tt>&nbsp;&nbsp; * Once you know you want to evaluate it you
          will likely want to cast <br>
          &nbsp;&nbsp; *&nbsp; the generic RegexPattern </tt><br>
        <tt>&nbsp;&nbsp; * to your own MyPattern</tt><br>
        <tt>&nbsp;&nbsp; * and do more specific stuff with it.</tt><br>
        <tt>&nbsp;</tt><tt>&nbsp; */</tt><br>
      </blockquote>
    </blockquote>
    <tt>&nbsp;&nbsp;&nbsp;&nbsp; &nbsp;&nbsp;&nbsp; &nbsp;&nbsp;
      MyPattern pat = (MyPattern) repat;</tt><br>
    <tt>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
      Matcher match = pat.regex.matcher(text);</tt><br>
    <tt>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
      // This&nbsp; tracks for this result that at least one rule was
      evaluated on the data.</tt><br>
    <tt>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
      // If no rules were evaluated, you have a bigger issue with logic
      or your config file.</tt><br>
    <tt>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
      // </tt><br>
    <tt>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
      results.evaluated = true;</tt><br>
    <br>
    <tt>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
      while (match.find()) {<br>
      &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
      MyMatch domainMatch&nbsp; = new MyMatch() // a TextMatch sub-class<br>
      <br>
      &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
      // Here you parse through the matches.<br>
      &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
      // You use the base pattern manager's ability to map the DEFINES
      to fields by name.<br>
      &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
      // <br>
      &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
      //&nbsp; Get basic RE metadata and then parse out fields from the
      RULE as needed.<br>
      &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
      // <br>
      &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
    </tt><tt><tt>domainMatch</tt>.pattern_id = pat.id;<br>
    </tt><tt><br>
      &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
    </tt><tt><tt>domainMatch</tt>.start = match.start();<br>
      &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
    </tt><tt><tt>domainMatch</tt>.end = match.end();<br>
      &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
    </tt><tt><tt>domainMatch</tt>.text = match.group();<br>
      &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
      Elements fields = patterns.group_map(pat, match)<br>
      <br>
      &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
      // Your domain logic for normalization...<br>
      &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
      // <br>
      &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
      Utility.normalizeFields( domainMatch, fields );<br>
      <br>
      &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
      // Filter?&nbsp; Check for false positives and filter out junk.<br>
      &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
      if (filter.filterOut(</tt><tt><tt>domainMatch</tt>)){<br>
      &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
      continue;<br>
      &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
      }<br>
      <br>
      &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
      results.matches.add( domainMatch );<br>
      <br>
    </tt><tt>&nbsp;&nbsp;&nbsp; &nbsp;&nbsp;&nbsp; &nbsp;&nbsp;&nbsp; }<br>
      &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; }<br>
    </tt><tt></tt><tt><br>
      &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; // You've now assessed
      all RULES on input text.&nbsp; All results are assembled, filtered,
      normalized, etc.<br>
      &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; // return.<br>
    </tt><tt>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; &nbsp; return results;</tt><tt><br>
    </tt><tt><br>
    </tt><tt>}</tt><br>
    <br>
    <br>
    <br>
    <b><br>
    </b><br>
    <br>
  </body>
</html>
*/

package org.mitre.flexpat;
