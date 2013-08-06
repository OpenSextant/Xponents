

Copy solr.war to your webapp folder if you intend to serve up a solr gazetteer query.
E.g., 

   unzip Jetty v8 (Java 6+)  or Jetty 9 (Java 7+)
   ./external/jetty-distribution-8.1.7.v20120910.zip

   copy solr.war to jetty/webapps/
   Start jetty --- ant -f run-ant.xml jetty


   Tomcat 6 and 7 have a very similar setup for solr.


Otherwise to use Solr embedded without a web server, that is fine. 
But the full-up Solr webapp is required to run Embedded Solr, even if it is not provisioned via  web-app server.
