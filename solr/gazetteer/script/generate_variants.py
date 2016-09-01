# -*- coding: utf-8 -*-
import re
import simplejson as json

saint_repl = {
    #'santos' : 'sto',  have not found good variation on 'santos'
    'sant' : 's',
    'santa' : 'sta',
    'santo' : 'st',
    'saint' : 'st',
    'sainte' : 'ste',
    'san' : 's'    
     }

general_repl = {
    'fort': 'ft',
    'north': 'n',
    'south': 's' 
    }

splitter = re.compile(u"[-.`'\u2019\s]+", re.UNICODE|re.IGNORECASE)

def save_result(pl, nameVar, out):
    # print "\tADD", n, "=>", nVar
    pl['name'] = nameVar
    pl['source'] = 'XpGen'
    pl['id'] = 20000000L + long(pl['id'])
    out.write(json.dumps(pl))
    out.write(',\n')
    
def generate_GENERAL_variants(gaz, output):
    '''
    Ft Worth <<==== Fort Worth
    N. Hampstead <<== North Hampstead
    W. Bedford Falls   <<== West Bedford Falls
    
    
    Almost as exact as SAINT replacements... 
    ''' 
    for term in general_repl:
        results = gaz.search("name:%s* AND feat_class:(A P) AND id:[ * TO 20000000]" % (term), rows=1000000)
        print "PREFIX", term, results.hits
        for place in results.docs:
            n = place.get('name')
            norm = n.lower()
            if norm == term:
                # No variant. Looking for multi-word names to make variants out of.
                continue
            
            # Prefix pattern!
            if not norm.startswith(term):
                continue
            
            toks = splitter.split(n, 1)
            if len(toks) == 1:
                continue
            
            variant = []
            for t in toks:
                if t.lower() == term:
                    repl = general_repl[term]
                    variant.append("%s." % (repl.capitalize()))                
                else:
                    variant.append(t)
                    
            pid = place.get('place_id')
            # print n, "|", toks
    
            nVar = ' '.join(variant)        
            existing = solrGaz.search(u'place_id:%s AND name:"%s"' % (pid, nVar), rows=1)
            
            if existing.hits == 0:
                save_result(place, nVar, output)
        
        
def generate_SAINT_variants(gaz, output):
    '''
    Valid French village abbreviations:
        St Pryvé St Mesmin   <<<--- Saint-Pryvé-Saint-Mesmin)
    
        Sta Maria           <<---Santa Maria 
    
    '''
    
    for saintly in saint_repl:
        results = gaz.search("name:%s AND id:[ * TO 20000000]" % (saintly), rows=200000)
        print "PREFIX", saintly, results.hits
        for place in results.docs:
            n = place.get('name')
            norm = n.lower()
            if norm == saintly:
                # No variant. Looking for multi-word names to make variants out of.
                continue
            
            # Prefix pattern!
            if not norm.startswith(saintly):
                continue
            
            # Splitter not quite working for trailing "'s"
            toks = splitter.split(n, 3)
            if len(toks) == 1:
                continue
            
            variant = []
            for t in toks:
                if t.lower() == saintly:
                    repl = saint_repl[saintly]
                    variant.append("%s." % (repl.capitalize()))
                    variant.append(' ')
                elif t.lower() == 's':  
                    # Inferring this was an apos s.... TODO:.              
                    variant.append("'s")
                else:
                    variant.append(' ')
                    variant.append(t)
                    
            pid = place.get('place_id')
            # print n, "|", toks
    
            nVar = ''.join(variant).strip()        
            existing = solrGaz.search(u'place_id:%s AND name:"%s"' % (pid, nVar), rows=1)
            if existing.hits == 0:
                save_result(place, nVar, output)
        
        
def tester():
    res = splitter.split("Saint Bob's Pond")
    res = splitter.split("Saint Bob' Pond")
    res = splitter.split("Sant' Bob Pond")
    print res
    
if __name__ == "__main__":
    import pysolr
    import codecs
    import argparse
    import os
    
    ap = argparse.ArgumentParser()
    ap.add_argument('--solr')
    ap.add_argument('--output')

    args = ap.parse_args()
    
    solrGaz = pysolr.Solr(args.solr)
    # added_variants = os.path.join('..', 'conf', 'additions', 'generated-variants.json')
    added_variants = args.output
    fh = codecs.open(added_variants, 'wb', encoding="utf-8")
    fh.write('[\n')

    generate_SAINT_variants(solrGaz, fh)
    generate_GENERAL_variants(solrGaz, fh)

    fh.seek(-1, os.SEEK_END)
    fh.truncate()
    fh.write(']')
    fh.close()
