

from opensextant.Data import Place
# from opensextant.GeonamesUtility import load_countries
import pysolr

debug=False

def run_lookup(url, lookup, parse):
    ''' Gazetteer demo mimics some of the logic in XponentsGazetteerQuery
        try "San Francisco, CA, US"
    '''

    solrGaz = pysolr.Solr(url)
    # specific unit tests

    records = None
    places = []
    if parse:
        # See other Java demo, XponentsGazetteerQuery
        # assuming NAME, PROV, COUNTRY
        slots = [a.strip() for a in lookup.split(',')]

        if len(slots)<3:  
            print ("NAME, PROV, CC  is required format for --lookup")
            return None

        cityVal = slots[0]
        provVal = slots[1]
        countryVal = slots[2]

        # Find best match for Province. Pass ADM1 code to next query
        query = 'name:"{}" AND feat_class:A AND cc:{}'.format(provVal, countryVal)
        records = solrGaz.search(query, **{"rows":100})

        if not records: return None

        # Use a Place object to abstract things.
        adm1 = asPlace(records.docs[0])
        # Find best match for the tuple NAME/PROV/COUNTRY
        #
        query = 'name:"{}" AND feat_class:A AND cc:{} AND adm1:{}'.format(cityVal, countryVal, adm1.adm1)
        records = solrGaz.search(query, **{"rows":1000})
    else:
        query = 'name:"{}" AND feat_class:P'.format(lookup)
        records = solrGaz.search(query, **{"rows":1000})

    if not records: return None

    for r in records:
        places.append( asPlace(r) )

    return places

def asPlace( r ):
    '''
    @param r : record.
    '''
    if debug: print(r)
    (lat, lon) = r['geo'].split(',')
    p = Place(r['place_id'], r['name'], lat=lat, lon=lon)
    p.adm1 = r.get('adm1')
    p.country_code = r.get('cc')
    return p


def run_query(url, q):
    ''' Expert mode:  Run a solr query to see what you get back. 
        requires you know the schema
    '''
    solrGaz = pysolr.Solr(url)
    records = solrGaz.search(q, **{"rows":100})
    places = []
    for r in records:
        places.append( asPlace(r) )

    return places

def print_places(arr, limit=25):
    print ("FOUND {}. Showing top {}".format(len(arr), limit))
    for p in arr[0:limit]:
        print str(p)

if __name__ == "__main__":

    import argparse

    ap = argparse.ArgumentParser()
    ap.add_argument('--solr')
    ap.add_argument('--output')
    ap.add_argument('--query')
    ap.add_argument('--lookup')
    ap.add_argument('--parse', action="store_true", default=False)
    ap.add_argument('--demo')

    args = ap.parse_args()

    if args.lookup:
        findings = run_lookup(args.solr, args.lookup, args.parse)
        print_places(findings)
    elif args.query:
        findings = run_query(args.solr, args.query)
        print_places(findings)
