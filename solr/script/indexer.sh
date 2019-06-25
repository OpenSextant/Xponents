echo "Run from Xponents ./solr/ folder"
java -Dlogback.configurationFile=../etc/logback.xml \
   -cp ./solr7/lib/xponents-gazetteer-meta.jar:../lib/*  org.opensextant.extractors.geo.GazetteerIndexer  \
   -u http://localhost:7000/solr/gazetteer -f $1 \
   -s "id,place_id,name,,lat,lon,feat_class,feat_code,FIPS_cc,cc,ISO3_cc,adm1,adm2,adm3,,,source,,,script,name_bias,id_bias,name_type,,SplitCategory,search_only"
