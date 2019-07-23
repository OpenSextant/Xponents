PORT=$1
RESTAPI=http://localhost:$PORT/xlayer/rest/process
# Using curl, POST a JSON object to the service.
#      features : LIST
#      text     : ASCII or UTF-8 string
#      docid    : identifier, mostly for logging purposes.
#
curl --data "{'docid':'SimpleTest#111', 'features':'places,coordinates,countries,persons,orgs,reverse-geocode', 'text':'Aliwagwag is situated in the Eastern Mindanao Biodiversity Corridor which contans one of the largest remaining blocks of tropical lowland rainforest in the Philippines. It covers an area of 10,491.33 hectares (25,924.6acres) and a buffer zone of 420.6 hectares (1,039 acres) in the hydrologically rich mountainous interior of the municipalities of Cateel and Boston in Davao Oriental as well as a portion of the municipality of Compostela in Compostela Valley. It is also home to the tallest trees in the Philippines, the Philippine rosewood, known locally as toog. In the waters of the upper Cateel River, a rare species of fish can be found called sawugnun by locals which is harvested as adelicacy.'}" $RESTAPI 
