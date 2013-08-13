/*
In order for this to be executed, it must be properly wired into solrconfig.xml; by default it is commented out in
the example solrconfig.xml and must be uncommented to be enabled.

See http://wiki.apache.org/solr/ScriptUpdateProcessor for more details.
*/

var includeCategorySet = null;
{
    var ic = params.get("include_category");//array of String
    if (ic != null) {
        includeCategorySet = new java.util.HashSet(ic);
    }
}
var CAT_FIELD = params.get("category_field") || "SplitCategory";

function processAdd(cmd) {

    var doc = cmd.solrDoc;  // org.apache.solr.common.SolrInputDocument
    // logger.info("update-script#processAdd: id=" + id);


    /* See solrconfig for documentation on gazetteer filtering
    * =======================================================
    */

    if (includeCategorySet != null) {
        var cat = doc.getFieldValue(CAT_FIELD) || "general";
        if (!includeCategorySet.contains(cat)) {
            logger.trace("update-script EXCLUDE: {}", doc);
            return false;
        }
    }

    /* End Filtering
    * =======================================================
    */

    // CREATE searchable lat lon
    lat = doc.getFieldValue("lat");
    lon = doc.getFieldValue("lon");

    if (lat != null && lon != null)
        doc.setField("geo", lat+","+lon);

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
