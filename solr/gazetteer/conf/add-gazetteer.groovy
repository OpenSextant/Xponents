/*
In order for this to be executed, it must be properly wired into solrconfig.xml; by default it is commented out in
the example solrconfig.xml and must be uncommented to be enabled.

See http://wiki.apache.org/solr/ScriptUpdateProcessor for more details.
*/

import org.opensextant.util.FileUtility;
import java.net.URL;

includeCategorySet = null
includeAll = false
ic = params.get("include_category");//array of String
if (ic != null) {
	includeCategorySet = new java.util.HashSet(ic)
	if (includeCategorySet.contains('all')){
	    includeAll = true
	}
}
logger.info "add-gaz Groovy#startup: 'Add Gazetteer'; Params: Include=" + includeCategorySet

CAT_FIELD = params.get("category_field") != null ? params.get("category_field") : "SplitCategory"

Set<String> stopTerms = new HashSet<>();
File stopFile = new File('gazetteer/lib/gazetteer-stopwords.txt')
if (stopFile.exists()) {
	stopTerms = FileUtility.loadDictionary( stopFile, false)
	logger.info "Found Stop terms"
}
params.set("stopterms", stopTerms);

def processAdd(cmd) {

    doc = cmd.solrDoc;  // org.apache.solr.common.SolrInputDocument

    /* See solrconfig for documentation on gazetteer filtering
    * =======================================================
    */
    if (!includeAll && includeCategorySet != null) {
        cat = doc.getFieldValue(CAT_FIELD)
        if (cat==null){
            cat = 'general'
        }
        if (!includeCategorySet.contains(cat)) {
            logger.info "update-script# EXCLUDE: "+cat + " "  + doc
            return false
        }
    }
 
    nm = doc.getFieldValue("name")
    if (nm.length() < 2){
        doc.setField("search_only", "true")
        logger.info "update-script#processAdd: Short name set search only"
    }
    
    nameLower = nm.toLowerCase()
    if (params.get("stopterms").contains(nameLower)){
        doc.setField("search_only", "true")
        logger.info "update-script#processAdd: Stop word set search only"
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

def processDelete(cmd) {
    // no-op
}

def processMergeIndexes(cmd) {
    // no-op
}

def processCommit(cmd) {
    // no-op
}

def processRollback(cmd) {
    // no-op
}

def finish() {
    // no-op
}
