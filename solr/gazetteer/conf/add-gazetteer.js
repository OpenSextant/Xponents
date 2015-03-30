/*
In order for this to be executed, it must be properly wired into solrconfig.xml; by default it is commented out in
the example solrconfig.xml and must be uncommented to be enabled.

See http://wiki.apache.org/solr/ScriptUpdateProcessor for more details.
*/

var includeCategorySet = null;
var includeAll = false;
{
    var ic = params.get("include_category");//array of String
    if (ic != null) {
        includeCategorySet = new java.util.HashSet(ic);
        if (includeCategorySet.contains('all')){
            includeAll = true;
        }
    }
    logger.info("add-gaz JS#startup: 'Add Gazetteer'; Params: Include=" + includeCategorySet);
}
var CAT_FIELD = params.get("category_field") || "SplitCategory";
var lat, lon;


function processAdd(cmd) {

    var doc = cmd.solrDoc;  // org.apache.solr.common.SolrInputDocument
    // logger.info("update-script#processAdd: id="+ doc.getFieldValue("id"));


    /* See solrconfig for documentation on gazetteer filtering
    * =======================================================
    */
    if (!includeAll && includeCategorySet != null) {
        var cat = doc.getFieldValue(CAT_FIELD) || "general";
        if (!includeCategorySet.contains(cat)) {
            logger.info("add-gaz JS# EXCLUDE: " + doc);
            return false;
        }
    }
 
    var nm = doc.getFieldValue("name");
    if (nm.length() < 2){
        doc.setField("search_only", "true");
        logger.info("add-gaz JS#processAdd: Short name set search only");
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
