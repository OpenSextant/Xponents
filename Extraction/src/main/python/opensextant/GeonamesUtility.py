'''
 GeonamesUtility:  lines up with functionality in Xponents Basics utility package.

'''

from Data import Country
from CommonsUtils import get_csv_reader, get_bool

countries = []
countries_by_iso = {}
countries_by_fips = {}
countries_by_name = {}

def load_countries(csvpath):
    ''' parses Xponents Basics/src/main/resource CSV file country-names-2015.csv
        putting out an array of Country objects.
    ''' 
    import codecs
    fh = codecs.open(csvpath, 'rb', encoding="UTF-8")
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

    fh.close()

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

