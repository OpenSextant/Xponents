'''
 GeonamesUtility:  lines up with functionality in Xponents Basics utility package.

'''

import codecs
import os
import re

from Data import Country
from CommonsUtils import get_csv_reader, get_bool
from opensextant.Data import Place

countries = []
countries_by_iso = {}
countries_by_fips = {}
countries_by_name = {}
usstates = {}

adm1_by_hasc = {}
def make_HASC(cc, adm1):
    if not adm1:
        adm1='0'
        
    return '{}.{}'.format(cc, adm1)

def load_countries(csvpath=None):
    ''' parses Xponents Basics/src/main/resource CSV file country-names-2015.csv
        putting out an array of Country objects.
    ''' 
    if not csvpath:
        pkg_dir = os.path.dirname(os.path.abspath(__file__))
        csvpath = os.path.join(pkg_dir, 'resources', 'country-names-2015.csv')

    with codecs.open(csvpath, 'rb', encoding="UTF-8") as fh:
      columns = "country_name,FIPS_cc,ISO2_cc,ISO3_cc,unique_name,territory".split(',')
      io = get_csv_reader(fh, columns)
      for row in io:

        # ignore empty row and header.
        if 'country_name' not in row: continue
        if row['country_name'] == 'country_name': continue

        C  = Country()
        C.name = row.get('country_name')
        C.cc_iso2 = row.get('ISO2_cc').upper()
        C.cc_iso3 = row.get('ISO3_cc').upper()
        C.cc_fips = row.get('FIPS_cc').upper()
 
        C.is_name_unique = get_bool(row.get('unique_name'))
        C.is_territory = get_bool(row.get('territory'))
        C.namenorm = C.name.lower()

        countries.append(C)

    for C in countries:
        if not C.is_territory:
            countries_by_iso[ C.cc_iso2 ] = C
            countries_by_iso[ C.cc_iso3 ] = C

        if C.cc_fips and C.cc_fips != "*":
            countries_by_fips[ C.cc_fips] = C

        countries_by_name[C.namenorm] = C

    intl  = Country(); intl.name="International"; intl.cc_iso2="ZZ"; intl.cc_iso3="ZZZ";
    countries_by_iso[ "ZZ" ] = intl 

    # WE, PS, GAZ, etc. and a handful of other oddities are worth noting and remapping.

    return None

def load_us_provinces():
    pkg_dir = os.path.dirname(os.path.abspath(__file__))
    csvpath = os.path.join(pkg_dir, 'resources', 'us-state-metadata.csv')
    with open(csvpath, 'rb') as fh:
        columns = ["POSTAL_CODE","ADM1_CODE","STATE","LAT","LON","FIPS_CC","ISO2_CC"]
        io = get_csv_reader(fh, columns)
        for row in io:
            if row['POSTAL_CODE'] == 'POSTAL_CODE': continue
            
            cc = row["ISO2_CC"]
            adm1_code = row["ADM1_CODE"][2:]
            place_id = make_HASC(cc, adm1_code)
            postal_id = make_HASC(cc, row["POSTAL_CODE"])
            adm1 = Place(place_id, row["STATE"])
            adm1.feature_class = "A"
            adm1.feature_code = "ADM1"
            adm1.name_type = "N"
            
            adm1.country_code = cc
            adm1.adm1 = adm1_code
            adm1.source = "OpenSextant"
                       
            usstates[place_id] = adm1
            usstates[postal_id] = adm1

    
def load_provinces():
    load_world_adm1()
    
def load_world_adm1():
    # Load local country data first, if you have it. US is only one so far.
    load_us_provinces()
        
    pkg_dir = os.path.dirname(os.path.abspath(__file__))
    csvpath = os.path.join(pkg_dir, 'resources', 'geonames.org', 'admin1CodesASCII.txt')
    
    with codecs.open(csvpath, 'rb', encoding="UTF-8") as fh:
        adm1Splitter = re.compile(r'\.')
        lineSplitter = re.compile('\t')
        for line in fh:
            row = lineSplitter.split(line)
            adm1 = Place(row[0], row[1])
            adm1.feature_class = "A"
            adm1.feature_code = "ADM1"
            adm1.name_type = "N"
            
            cc_adm1 = adm1Splitter.split(row[0],2)
            adm1.country_code = cc_adm1[0]
            adm1.adm1 = cc_adm1[1]
            adm1.source = "geonames.org"
            hasc = make_HASC(adm1.country_code, adm1.adm1)            
            if adm1.country_code == "US":
                adm1.source = "USGS"
                if hasc in usstates:
                    us_place = usstates[hasc]
                    us_place.name = adm1.name
                    hasc = make_HASC(us_place.country_code, us_place.adm1)             
                    adm1_by_hasc[hasc] = adm1
                
            adm1_by_hasc[hasc] = adm1
    return
       
def get_province(cc, adm1):
    ''' REQUIRES you load_provinces() first. 
    '''    
    return adm1_by_hasc.get(make_HASC(cc, adm1))

