/*
  This is a basic skeleton JavaScript update processor.

  In order for this to be executed, it must be properly wired into solrconfig.xml; by default it is commented out in
  the example solrconfig.xml and must be uncommented to be enabled.

  See http://wiki.apache.org/solr/ScriptUpdateProcessor for more details.
*/

function processAdd(cmd) {

  doc = cmd.solrDoc;  // org.apache.solr.common.SolrInputDocument
  //id = doc.getFieldValue("id");
  // logger.info("update-script#processAdd: id=" + id);

  testing=0
  if (testing) {
      // Testing script - Objective here is to index only MEX data, cc='MX'; 
      // return false for everything else
      country = doc.getFieldValue("FIPS_cc");

      if (country != "MX")
          return false;
  }

  // CREATE searchable lat lon 
  lat = doc.getFieldValue("lat");
  lon = doc.getFieldValue("lon");
  
  if (lat != null && lon != null)
    doc.setField("geo", lat+","+lon);

  // Individual lat/lon no longer needed.
  doc.removeField("lat");
  doc.removeField("lon");

  // OPTIMIZATION,  Name Type is stored. So optimize it. If possible.
  nt = doc.getFieldValue("name_type");
  if (nt!=null) {
      if (nt=="abbrev")
          doc.setField("name_type", "A");
      else if (nt=="name")
          doc.setField("name_type", "N");
  }
     
}

function processDelete(cmd) {
  // no-op
}

function processMergeIndexes(cmd) {
  // no-op
}

function processCommit(cmd) {
  // no-op
}

function processRollback(cmd) {
  // no-op
}

function finish() {
  // no-op
}
