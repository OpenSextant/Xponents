# -*- coding: utf-8 -*-
import re
import simplejson as json

'''
Name Variant Generator:  create as many valid name variants for popular 
abbreviated phrases in names.

    "S. Pedro"   (instead of San Pedro)
    "Sta Maria"  (instead of Santa Maria)
    
Seems trivial, however we miss lots of potential mentions because 
many times only the short form is used:  St. Louis,...

'''

saint_repl = {
    #'santos' : 'sto',  have not found good variation on 'santos'
    'sant' : 's. ',
    'santa' : 'sta. ',
    'santo' : 'st. ',
    'saint' : 'st. ',
    'sainte' : 'ste. ',
    'san' : 's. '    
     }

general_repl = {
    'fort': 'ft. ',
    'north': 'n. ',
    'south': 's. ' 
    }

splitter = re.compile(u"[-.`'\u2019\s]+", re.UNICODE|re.IGNORECASE)

debug = False
first = True
def save_result(pl, nameVar, out):
    global first
    # print "\tADD", n, "=>", nVar
    if first:
        out.write('[')
    else:
        out.write(',')        
        out.write('\n')
        
    pl['name'] = nameVar
    pl['source'] = 'XpGen'
    pl['id'] = 30000000L + long(pl['id'])
    out.write(json.dumps(pl))
    
    if first:
        first = False
    
    
def generate_GENERAL_variants(gaz, output):
    '''
    Ft Worth <<==== Fort Worth
    N. Hampstead <<== North Hampstead
    W. Bedford Falls   <<== West Bedford Falls
    
    We generate variants that do not already exist.
    
    Almost as exact as SAINT replacements... 
    ''' 
    for term in general_repl:
        results = gaz.search("name:%s* AND feat_class:(A P) AND id:[ * TO 20000000]" % (term), rows=1000000)
        print "PREFIX", term, results.hits
        pat = u"(%s\s+)" % (term)
        regex = re.compile(pat, re.UNICODE | re.IGNORECASE)
        for place in results.docs:
            n = place.get('name')
            norm = n.lower()
            if norm == term:
                # No variant. Looking for multi-word names to make variants out of.
                continue
            
            # Prefix pattern!
            if not norm.startswith(term):
                continue
            
            repl = general_repl[term].capitalize()
            nVar = regex.sub(repl, n)
            nVar = nVar.replace('-', ' ').strip()
            nVar = nVar.replace('  ',' ')  

            pid = place.get('place_id')
    
            existing = solrGaz.search(u'place_id:%s AND name:"%s"' % (pid, nVar), rows=1)
            if existing.hits == 0:
                save_result(place, nVar, output)
        
        

def generate_SAINT_variants(gaz, output):
    '''
    Valid French village abbreviations:
        St Pryvé St Mesmin   <<<--- Saint-Pryvé-Saint-Mesmin)
    
        Sta Maria           <<---Santa Maria 
        
        Because 'San' is a typical syllable in Asian languages, we'll ignore certain countries
    
    '''
    
    ignore_countries = set("TW CN JP LA VN ML PA KR KP".split())
    for saintly in saint_repl:
        results = gaz.search("name:%s AND id:[ * TO 20000000]" % (saintly), rows=200000)
        print "PREFIX", saintly, results.hits
        
        pat = u"(%s[-`'\u2019\s]+)" % (saintly)
        regex = re.compile(pat, re.UNICODE | re.IGNORECASE)
        for place in results.docs:
            n = place.get('name')
            norm = n.lower()
            if norm == saintly:
                # No variant. Looking for multi-word names to make variants out of.
                continue
            
            # Prefix pattern!
            if not norm.startswith(saintly):
                continue
            
            cc = place.get('cc')
            if cc in ignore_countries and saintly == 'san':
                continue
            
            repl = saint_repl[saintly].capitalize()
            nVar = regex.sub(repl, n)
            nVar = nVar.replace('-', ' ').strip()
            nVar = nVar.replace('  ',' ')  

            pid = place.get('place_id')
    
            existing = solrGaz.search(u'place_id:%s AND name:"%s"' % (pid, nVar), rows=1)
            if existing.hits == 0:
                if debug: print n, "==>", nVar
                save_result(place, nVar, output)
        
        
def tester():
    res = splitter.split("Saint Bob's Pond")
    res = splitter.split("Saint Bob' Pond")
    res = splitter.split("Sant' Bob Pond")
    print res
    
    replacements = {}
    
    term = 'saint'
    replacements[term] = 'st. '
    pat = u"(%s[-`'\u2019\s]+)" % (term)
    regex = re.compile(pat, re.UNICODE | re.IGNORECASE)
            
    test = 'Saint-Pryvé-Saint-Mesmin'
    repl = replacements[term].capitalize()
    nVar = regex.sub(repl, test)
    nVar = nVar.replace('-', ' ').strip()
    nVar = nVar.replace('  ',' ')  
    print nVar


    
if __name__ == "__main__":
    
    # tester()
    
    import pysolr
    import codecs
    import argparse
    
    ap = argparse.ArgumentParser()
    ap.add_argument('--solr')
    ap.add_argument('--output')

    args = ap.parse_args()
    
    solrGaz = pysolr.Solr(args.solr)
    # added_variants = os.path.join('..', 'conf', 'additions', 'generated-variants.json')
    added_variants = args.output
    fh = codecs.open(added_variants, 'wb', encoding="utf-8")

    generate_SAINT_variants(solrGaz, fh)
    generate_GENERAL_variants(solrGaz, fh)

    fh.write(']')
    fh.close()
