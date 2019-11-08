# -*- coding: utf-8 -*-
import re
import simplejson as json

"""
Name Variant Generator:  create as many valid name variants for popular 
abbreviated phrases in names.

    "S. Pedro"   (instead of San Pedro)
    "Sta Maria"  (instead of Santa Maria)
    
Seems trivial, however we miss lots of potential mentions because 
many times only the short form is used:  St. Louis,...

"""

saint_repl = {
    #'santos' : 'sto',  have not found good variation on 'santos'
    'sant' : 's. ',
    'santa' : 'sta. ',
    'santo' : 'sto. ',
    'saint' : 'st. ',
    'sainte' : 'ste. ',
    'san' : 's. '    
     }

general_repl = {
    'mount': 'mt. ',
    'fort': 'ft. ',
    'north': 'n. ',
    'south': 's. ',
    'west': 'w. ',
    'east': 'e. '
    }

splitter = re.compile(u"[-.`'\u2019\s]+", re.UNICODE|re.IGNORECASE)

debug = False

ORIGINALS_BLOCK=20000000


def _init_json(out):
    out.write('[')

def save_result(pl, nameVar, out, first=False):
    if not first: out.write(',\n')        
    pl['name'] = nameVar
    pl['source'] = 'XpGen'
    given_id = pl['id']
    # NOTE: this adds the size of ID space to each new variant.
    pl['id'] = str( ORIGINALS_BLOCK + int(given_id) )
    for k in ["adm1","adm2","adm3"]:
        if k in pl and not pl[k]: del pl[k]
    out.write(json.dumps(pl))
    
def generate_GENERAL_variants(gaz, output):
    """
    Ft Worth <<==== Fort Worth
    N. Hampstead <<== North Hampstead
    W. Bedford Falls   <<== West Bedford Falls
    
    We generate variants that do not already exist.
    
    Almost as exact as SAINT replacements... 
    """
    for term in general_repl:
        count=0
        output_path = '{}_{}.json'.format(output, term)
        fh = codecs.open(output_path, 'wb', encoding="utf-8")
        _init_json(fh)
        first = True

        results = gaz.search("name:{} AND feat_class:(A P) AND id:[ * TO {}]".format(term, ORIGINALS_BLOCK), rows=500000)
        print ("PREFIX", term, results.hits)
        pat = u"(%s\s+)" % (term)
        regex = re.compile(pat, re.UNICODE | re.IGNORECASE)
        repl = general_repl[term].capitalize()
        term_ = term + u' '
        for place in results.docs:
            n = place.get('name')
            norm = n.lower()
            if norm == term:
                # No variant. Looking for multi-word names to make variants out of.
                continue

            norm2 = norm.replace("town of ", "").replace("township of ", "").replace("village of ", "")
            norm2 = norm2.lower().strip()

            # Prefix pattern!
            # Village of West Bar Harbour ==> W. Bar Harbour [[ OK ]]
            # Village of Bar Harbour au West ==>  will not prodouce a variant with West => W. abbreviated.
            if not norm2.startswith(term_):
                continue

            repl_candidates = [norm]
            if len(norm2) - len(term) > 4:
                repl_candidates.append(norm2)
            else:
                # Avoid variants that produce abbreviations and are very short.
                # "West Oz" --> "W. Oz";  "West Oz" - "West " is only 2 chars.
                print ("Short variant avoided", norm2)

            pid = place.get('place_id')
            variants = []

            for nameVariant in set(repl_candidates):
                nVar = regex.sub(repl, nameVariant)
                nVar = nVar.replace('-', ' ').strip()
                nVar = nVar.replace('  ',' ')
                variants.append(nVar)

            if norm != norm2:
                variants.append(norm2)

            for nameVariant in variants:
                existing = solrGaz.search(u'place_id:%s AND name:"%s"' % (pid, nameVariant), rows=1)
                if existing.hits == 0:
                    count+=1
                    save_result(place, nameVariant, fh, first=first)
                    first=False

        if count==0:
            print ("None found")
        fh.write(']')
        fh.close()


def generate_SAINT_variants(gaz, output):
    """
    Valid French village abbreviations:
        St Pryvé St Mesmin   <<<--- Saint-Pryvé-Saint-Mesmin)
    
        Sta Maria           <<---Santa Maria 
        
        Because 'San' is a typical syllable in Asian languages, we'll ignore certain countries
    
    """

    output_path = '{}_{}.json'.format(output, 'SAINT')
    fh = codecs.open(output_path, 'wb', encoding="utf-8")
    _init_json(fh)
    first=True

    ignore_countries = set("TW CN JP LA VN ML PA KR KP".split())
    for saintly in saint_repl:
        results = gaz.search("name:{} AND id:[ * TO {}]".format(saintly, ORIGINALS_BLOCK), rows=500000)
        print("PREFIX", saintly, results.hits)
        
        pat = u"({}}[-`'\u2019\s]+)".format (saintly)
        regex = re.compile(pat, re.UNICODE | re.IGNORECASE)
        repl = saint_repl[saintly].capitalize()
        term_ = '{} '.format(saintly)
        term_dash = '{}-'.format(saintly)
        print (term_)
        for place in results.docs:
            n = place.get('name')
            norm = n.lower()
            if norm == saintly:
                # No variant. Looking for multi-word names to make variants out of.
                continue
            
            # Prefix pattern!
            if not (norm.startswith(term_) or norm.startswith(term_dash)):
                continue

            if len(norm) - len(saintly) < 3:
                # Avoid variants that produce abbreviations and are very short.
                # "Saint Oz" --> "S. Oz";  "Saint Oz" - "Saint " is only 2 chars.
                print ("Short variant avoided", norm)
                continue
            
            cc = place.get('cc')
            if cc in ignore_countries and saintly == 'san':
                continue
            
            nVar = regex.sub(repl, n)
            nVar = nVar.replace('-', ' ').strip()
            nVar = nVar.replace('  ',' ')  

            pid = place.get('place_id')
    
            existing = solrGaz.search(u'place_id:%s AND name:"%s"' % (pid, nVar), rows=1)
            if existing.hits == 0:
                if debug: print(n, "==>", nVar)
                save_result(place, nVar, fh, first=first)
                first=False

    fh.write(']')
    fh.close()
        
        
def tester():
    res = splitter.split("Saint Bob's Pond")
    res = splitter.split("Saint Bob' Pond")
    res = splitter.split("Sant' Bob Pond")
    print(res)
    
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
    print(nVar)


    
if __name__ == "__main__":
    
    # tester()
    import os
    import pysolr
    import codecs
    import argparse
    
    ap = argparse.ArgumentParser()
    ap.add_argument('--solr')
    ap.add_argument('--output')

    args = ap.parse_args()
    
    solrGaz = pysolr.Solr(args.solr)
    basepath = args.output.replace('.json', '')

    generate_GENERAL_variants(solrGaz, basepath)
    generate_SAINT_variants(solrGaz, basepath)

