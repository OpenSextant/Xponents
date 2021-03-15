# -*- coding: utf-8 -*-
import os
import re
import sys
from abc import ABC, abstractmethod
from math import sqrt, sin, cos, radians, atan2

from opensextant.utility import get_csv_reader, get_bool

PY3 = sys.version_info.major == 3
countries = []
countries_by_iso = {}
countries_by_fips = {}
countries_by_name = {}
usstates = {}
adm1_by_hasc = {}
__loaded = False


def make_HASC(cc, adm1):
    if not adm1:
        adm1 = '0'
    return '{}.{}'.format(cc, adm1)


class Country:
    """  Country metadata
    """

    def __init__(self):
        self.cc_iso2 = None
        self.cc_iso3 = None
        self.cc_fips = None
        self.name = None
        self.namenorm = None
        self.aliases = []
        self.is_territory = False
        self.is_unique_name = False
        self.timezones = []
        self.languages = set([])
        self.primary_language = None
        self.lat = 0
        self.lon = 0

    def __str__(self):
        return u'{} ({})'.format(self.name, self.cc_iso2)


def format_coord(lat, lon):
    """
    2.6, 3.6 format.
    :param lat: latitude
    :param lon: longitude
    :return: string
    """
    return '{:2.6f},{:3.6f}'.format(float(lat), float(lon))


def validate_lat(f):
    return (f >= -90.0) and (f <= 90.0)


def validate_lon(f):
    return (f >= -180.0) and (f <= 180.0)


def distance_cartesian(x1, y1, x2, y2):
    """
        Given X1, Y1 and X2, Y2 provide the 2-D Cartesian distance between two points.
    """
    xdist = x2 - x1
    ydist = y2 - y1
    return sqrt(xdist * xdist + ydist * ydist)


EARTH_RADIUS_WGS84 = 6378  # KM,  True: 6378.137


def distance_haversine(ddlon1, ddlat1, ddlon2, ddlat2):
    """
    Returns distance in kilometers for given decimal degree Lon/Lat (X,Y) pair

    http://www.movable-type.co.uk/scripts/latlong.html
    """
    lat1 = radians(ddlat1)
    lon1 = radians(ddlon1)
    lat2 = radians(ddlat2)
    lon2 = radians(ddlon2)
    dLat = lat2 - lat1
    dLon = lon2 - lon1
    a = (sin(dLat / 2) * sin(dLat / 2)) + (cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2))
    c = 2 * atan2(sqrt(a), sqrt(1 - a))
    d = EARTH_RADIUS_WGS84 * c
    return d


class Coordinate:
    """
    Convenient class for Lat/Lon pair.
    Expects a row dict with 'lat' and 'lon',
    or kwd args 'lat', 'lon'
    @param row default dictionary
    """

    def __init__(self, row, lat=None, lon=None):

        self.X = 0.0
        self.Y = 0.0
        self.mgrs = None
        self.lat = self.Y
        self.lon = self.X
        # Set geohash on demand, otherwise it can be computed from lat,lon
        self.geohash = None

        if row:
            if 'lat' in row and 'lon' in row:
                lat = row['lat']
                lon = row['lon']

        if lat and lon:
            self.set(lat, lon)

    def validate(self):
        return validate_lat(self.Y) and validate_lon(self.X) and (self.X != 0.0 and self.Y != 0.0)

    def set(self, lat, lon):
        """ Set the location lat, lon"""
        self.X = float(lon)
        self.Y = float(lat)
        self.lat = self.Y
        self.lon = self.X

    def format_coord(self):
        return format_coord(self.Y, self.X)

    def __str__(self):
        if self.Y:
            return format_coord(self.Y, self.X)
        else:
            return 'unset'


class Place(Coordinate):
    """
    Location or GeoBase
    + Coordinate
    + Place
    + Country

    or
    Location
    + Coordinate
       + Place

       etc.  Not sure of the best data model for inheritance.
    This Python API hopes to simplify the concepts in the Java API.

    """

    def __init__(self, pid, name, lat=None, lon=None):
        Coordinate.__init__(self, None, lat=lat, lon=lon)
        # Internal DB or Gazetteer ID
        self.id = None
        # Public or standards Place ID, e.g., GNS, ISO, etc.
        self.place_id = pid
        self.name = name

        self.is_ascii = False
        self.is_upper = False
        self.adm1_postalcode = None  # Province Postal CODE?
        self.place_postalcode = None  # ZIP CODE?
        self.name_type = None
        self.country = None
        self.country_code = None
        self.country_code_fips = None
        self.feature_class = None
        self.feature_code = None
        self.adm1 = None
        self.adm1_name = None
        self.adm2 = None
        self.adm2_name = None
        self.source = None
        self.name_bias = 0.0
        self.id_bias = 0.0
        self.precision = -1
        self.method = None
        self.population = 0
        self.hierarchical_path = None

        # Internal fields for gazetteer curation and text analytics:
        self.name_group = ""
        self.search_only = False

    def has_coordinate(self):
        return self.validate()

    def get_location(self):
        """ Returns (LAT, LON) tuple
        @return: tuple, (lat,lon)
        """
        return self.Y, self.X

    def set_location(self, lat, lon):
        self.set(lat, lon)

    def __str__(self):
        return '{}, {} @({})'.format(self.name, self.country_code, self.format_coord())


def load_countries(csvpath=None):
    """ parses Xponents Basics/src/main/resource CSV file country-names-2015.csv
        putting out an array of Country objects.
    """
    if not csvpath:
        pkg_dir = os.path.dirname(os.path.abspath(__file__))
        csvpath = os.path.join(pkg_dir, 'resources', 'country-names-2015.csv')

    with open(csvpath, 'r', encoding="UTF-8") as fh:
        columns = "country_name,FIPS_cc,ISO2_cc,ISO3_cc,unique_name,territory".split(',')
        fio = get_csv_reader(fh, columns)
        for row in fio:

            # ignore empty row and header.
            if 'country_name' not in row:
                continue
            if row['country_name'] == 'country_name':
                continue

            C = Country()
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
            countries_by_iso[C.cc_iso2] = C
            countries_by_iso[C.cc_iso3] = C

        if C.cc_fips and C.cc_fips != "*":
            countries_by_fips[C.cc_fips] = C

        countries_by_name[C.namenorm] = C

    intl = Country()
    intl.name = "International"
    intl.cc_iso2 = "ZZ"
    intl.cc_iso3 = "ZZZ"
    countries_by_iso["ZZ"] = intl

    # WE, PS, GAZ, etc. and a handful of other oddities are worth noting and remapping.

    global __loaded
    __loaded = len(countries_by_iso) > 0
    return None


def load_us_provinces():
    pkg_dir = os.path.dirname(os.path.abspath(__file__))
    csvpath = os.path.join(pkg_dir, 'resources', 'us-state-metadata.csv')
    with open(csvpath, 'rU', encoding="UTF-8") as fh:
        columns = ["POSTAL_CODE", "ADM1_CODE", "STATE", "LAT", "LON", "FIPS_CC", "ISO2_CC"]
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

    with open(csvpath, 'r', encoding="UTF-8") as fh:
        adm1Splitter = re.compile(r'\.')
        lineSplitter = re.compile('\t')
        for line in fh:
            row = lineSplitter.split(line)
            adm1 = Place(row[0], row[1])
            adm1.feature_class = "A"
            adm1.feature_code = "ADM1"
            adm1.name_type = "N"

            cc_adm1 = adm1Splitter.split(row[0], 2)
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
    """ REQUIRES you load_provinces() first.
    """
    return adm1_by_hasc.get(make_HASC(cc, adm1))


def get_country(code, standard="ISO"):
    """

    :param code: 2- or 3-alpha code.
    :param standard: 'ISO' or 'FIPS'
    :return:  Country object
    """
    if not code or not isinstance(code, str):
        return None

    lookup = code.upper()
    if standard == "ISO":
        if not __loaded:
            load_countries()
        return countries_by_iso.get(lookup)
    elif standard == "FIPS":
        if not __loaded:
            load_countries()
        return countries_by_fips.get(lookup)
    else:
        raise Exception("That standards body '{}' is not known for code {}".format(standard, code))


def is_administrative(feat):
    if not feat: return False
    return "A" == feat.upper()


def is_populated(feat):
    if not feat: return False
    return "P" == feat.upper()


class TextEntity:
    """
    A Text span.

    classes and routines that align with Java org.opensextant.data and org.opensextant.extraction

    * TextEntity: represents a span of text
    * TextMatch: a TextEntity matched by a particular routine.  This is the basis for most all
    extractors and annotators in OpenSetant.
    """

    def __init__(self, text, start, end):
        self.text = text
        self.start = start
        self.end = end
        self.len = -1
        self.is_duplicate = False
        self.is_overlap = False
        self.is_submatch = False
        if self._is_valid():
            self.len = self.end - self.start

    def __str__(self):
        return f"{self.text}({self.start},{self.end})"

    def _is_valid(self):
        if self.start is None or self.end is None:
            return False
        return self.start >= 0 and self.end >= 0

    def contains(self, x1):
        """ if this span contains an offset x1
        :param x1:
        """
        if self.start < 0 or self.end < 0:
            return False
        return self.start <= x1.start < x1.end <= self.end

    def exact_match(self, t):
        return t.start == self.start and t.end == self.end and self._is_valid()

    def is_within(self, t):
        """
        if the given annotation, t, contains this
        :param t:
        :return:
        """
        return t.contains(self.start) and t.contains(self.end)

    def is_after(self, t):
        return self.start > t.end

    def is_before(self, t):
        return self.end < t.start

    def overlaps(self, t):
        """
        Determine if t overlaps self.  If Right or Left match, t overlaps if it is longer.
        If t is contained entirely within self, then it is not considered overlap -- it is Contained within.
        :param t:
        :return:
        """
        #    a1     a2
        #  t1     t2        RIGHT skew
        #    a1     a2
        #       t1     t2   LEFT skew
        #
        #   a1  a2
        #   t1      t2  RIGHT match
        # t1    t2      LEFT match
        #   a1  a2
        #       t1   t2  minimal OVERLAP
        skew_right = t.start < self.start <= t.end < self.end
        skew_left = self.start < t.start <= self.end < t.end
        left_match = self.end == t.end
        right_match = self.start == t.start
        if skew_right or skew_left:
            return True
        return (right_match and skew_left) or (left_match and skew_right)


class TextMatch(TextEntity):
    """
    An entity matched by some tagger; it is a text span with lots of metadata.
    """

    def __init__(self, *args, label=None):
        TextEntity.__init__(self, *args)
        self.id = None
        self.label = label
        self.filtered_out = False
        self.attrs = dict()

    def __str__(self):
        return f"{self.label}/{self.text}({self.start},{self.end})"

    def populate(self, attrs):
        """
        Populate a TextMatch to normalize the set of attributes -- separate class fields on TextMatch from additional
        optional attributes.
        :param attrs:
        :return:
        """
        self.label = attrs.get("type")
        self.attrs = attrs
        self.filtered_out = get_bool(self.attrs.get("filtered-out"))
        for k in ['len', 'length']:
            if k in self.attrs:
                self.len = self.attrs.get(k)
        if self.len is not None and self.start >= 0 and not self.end:
            self.end = self.start + self.len

        # Remove attribute keys that may be confusing.
        for fld in ['offset', 'start', 'end', 'len', 'length', 'type', 'filtered-out', 'text', 'matchtext']:
            if fld in self.attrs:
                del self.attrs[fld]

    def normalize(self):
        """
        Optional, but recommended routine to normalize the matched data.
        That is, parse fields, uppercase, streamline punctuation, etc.
        As well, given such normalization result, this is the opportunity to additionally
        validate the match.
        :return:
        """
        pass


class Extractor(ABC):
    def __init__(self):
        self.id = None

    @abstractmethod
    def extract(self, text, **kwargs):
        """

        :param text: Unicode text input
        :keyword features: an array of features to extract, e.g., "coordinate", "place", "MONEY"
        :return: array of TextMatch
        """
        pass


def render_match(m):
    """

    :param m: TextMatch
    :return: dict
    """
    if not isinstance(m, TextMatch):
        return None
    dct = {
        "type": m.label,
        "text": m.text,
        "offset": m.start,
        "length": m.len,
        "filtered-out": m.filtered_out
    }
    return dct
