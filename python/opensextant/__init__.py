# -*- coding: utf-8 -*-
import os
import re
import sys
from abc import ABC, abstractmethod
from math import sqrt, sin, cos, radians, atan2, log as mathlog, log10

from opensextant.utility import get_csv_reader, get_bool
from pygeodesy.ellipsoidalVincenty import LatLon as LL
from pygeodesy.geohash import encode as geohash_encode, neighbors as geohash_neighbors

PY3 = sys.version_info.major == 3
countries = []
countries_by_iso = {}
countries_by_fips = {}
countries_by_name = {}
usstates = {}
adm1_by_hasc = {}
__loaded = False


def make_HASC(cc, adm1, adm2=None):
    """
    Create a simplie hiearchical path for a boundary
    :param cc:
    :param adm1:
    :param adm2:
    :return:
    """
    if not adm1:
        adm1 = '0'
    if adm2:
        return '{}.{}.{}'.format(cc, adm1, adm2)
    else:
        return '{}.{}'.format(cc, adm1)


def format_coord(lat, lon):
    """
    2.6, 3.6 format.
    :param lat: latitude
    :param lon: longitude
    :return: string
    """
    return '{:2.5f},{:3.5f}'.format(float(lat), float(lon))


def validate_lat(f):
    return (f >= -90.0) and (f <= 90.0)


def validate_lon(f):
    return (f >= -180.0) and (f <= 180.0)


def parse_admin_code(adm1):
    """
    :param adm1: admin level 1 code
    :return: ADM1 code if possible.
    """
    if not adm1:
        return ""

    code = adm1
    if "?" in adm1:
        code = "0"
    elif "." in adm1:
        cc2, code = adm1.split(".")
    # Normalize Country-level.  Absent ADM1 levels are assigned "0" anyway
    if code.strip() in {"", None, "0", "00"}:
        code = "0"
    return code


def distance_cartesian(x1, y1, x2, y2):
    """
        Given X1, Y1 and X2, Y2 provide the 2-D Cartesian distance between two points.
    """
    xdist = x2 - x1
    ydist = y2 - y1
    return sqrt(xdist * xdist + ydist * ydist)


EARTH_RADIUS_WGS84 = 6378.137 * 1000  # M,  True: 6378.137


def distance_haversine(ddlon1, ddlat1, ddlon2, ddlat2):
    """
    Returns distance in meters for given decimal degree Lon/Lat (X,Y) pair

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
    return int(EARTH_RADIUS_WGS84 * c)


def location_accuracy(conf, prec_err):
    """
    Both confidence and precision error are required to be non-zero and positive.

    Scale ACCURACY by confidence, and inversely log10( R^2 )
    Decreasing accuracy with increasing radius, but keep scale on the order of visible things,
    e.g., 0.01 to 1.00.  This is only one definition of accuracy.

    Consider confidence = 100 (aka 100% chance we have the right location)

    * Country precision ~ +/- 100KM is accuracy = 0.091
    * GPS precision is   10 M precision is accuracy 0.33
    * 1M precision , accuracy =  1.0, (1 / (1+log(1*1)) = 1/1.  In other words a 1m error is basically "perfect"

    :param conf: confidence on 100 point scale (0-100)
    :param prec_err: error in location precision, meters
    :return:
    """
    if not conf or not prec_err:
        return 0
    if conf < 0 or prec_err < 0:
        return 0
    scale = 0.01 * conf
    inv_prec = 1 + log10(prec_err * prec_err)
    acc = scale / inv_prec
    return float(f"{acc:0.4f}")


def _estimate_geohash_precision(r: int):
    """
    Returns hueristic geohash length for the given radius in meters.

    :param r: radius in meters
    """
    precision = 0
    if r > 1000000:
        precision = 1
    elif r > 250000:
        precision = 2
    elif r > 50000:
        precision = 3
    elif r > 10000:
        precision = 4
    elif r > 1000:
        precision = 5
    elif r > 250:
        precision = 6
    elif r > 50:
        precision = 7
    elif r > 1:
        precision = 8
    else:
        raise Exception(f"Not thinking about sub-meter resolution. radius={r}")

    return precision


def _ll2dict(p: LL):
    return {"lat": p.lat, "lon": p.lon}


def _ll2geohash(p: LL):
    return geohash_encode(lat=p.lat, lon=p.lon)


def radial_geohash(lat, lon, radius):
    """
    Propose geohash cells for a given radius from a given point
    """
    corners = {}
    # Find clockwise points at a radius, E, N, S, W. Bearing for North is 0deg.
    p1 = LL(lat, lon)
    corners["N"] = _ll2geohash(p1.destination(radius, 0))
    corners["E"] = _ll2geohash(p1.destination(radius, 90))
    corners["S"] = _ll2geohash(p1.destination(radius, 180))
    corners["W"] = _ll2geohash(p1.destination(radius, 270))
    return corners


def geohash_cells_radially(lat: float, lon: float, radius: int):
    """
    Create a set of geohashes that contain the given area defined by lat,lon + radius
    """
    ensw = radial_geohash(lat, lon, radius)
    radius_error = _estimate_geohash_precision(radius)
    cells = set([])
    for directional in ensw:
        gh = ensw[directional]
        cells.add(gh[0:radius_error - 1])
    return cells


def geohash_cells(gh: str, radius: int):
    """
    For a radius in meters generate the cells contained within or touched by that radius.
    This is approximate precision based on:
    https://en.wikipedia.org/wiki/Geohash   which suggests this approximation could be done mathematically
    :return: Dict of 8 directionals ~ E, N, S, W; NE, SE, SW, NW.  If radius desired fits entirely within a
    lesser precision geohash grid, the only cell returned is "CENTROID", i.e.  radius=2000 (meters) for a geohash such as
    `9q5t`
    """
    radius_error = _estimate_geohash_precision(radius)
    if len(gh) < radius_error:
        return {"CENTROID": gh}
    ghcell = gh[0:radius_error]
    return geohash_neighbors(ghcell)


class Coordinate:
    """
    Convenient class for Lat/Lon pair.
    Expects a row dict with 'lat' and 'lon',
    or kwd args 'lat', 'lon'
    @param row default dictionary
    """

    def __init__(self, row, lat=None, lon=None):
        # TODO: set coordinate to X, Y = None, None by default.
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

    def string_coord(self):
        return ",".join((str(self.lat), str(self.lon)))

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
        self.name_script = None  # Code or label, e.g. L or LATIN
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
        # Precision is actually "Precision Error" in meters
        self.precision = -1
        self.method = None
        # Population stats, if available. Scale is a power-of-2 scale
        # starting at about pop of 2^14 as 0, 32K=1, 64K=2, etc.
        self.population = -1
        self.population_scale = 0
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
        return '{}, {} @({})'.format(self.name, self.country_code, self.string_coord())


class Country(Coordinate):
    """
    Country metadata
    """

    def __init__(self):
        Coordinate.__init__(self, None)
        self.cc_iso2 = None
        self.cc_iso3 = None
        self.cc_fips = None
        self.place_id = None
        self.name = None
        self.namenorm = None
        self.name_type = None
        self.aliases = []
        self.is_territory = False
        self.is_unique_name = False
        self.timezones = []
        self.languages = set([])
        self.primary_language = None

    def __str__(self):
        return u'{} ({})'.format(self.name, self.cc_iso2)


def as_place(ctry: Country, name: str, name_type="N", oid=None):
    """
    Convert to Place.
    :param ctry: Country object
    :param name: the name to use
    :param name_type:
    :param oid: row ID
    :return:
    """
    pl = Place(ctry.cc_iso2, name)
    pl.id = oid
    pl.place_id = ctry.place_id
    pl.name_type = name_type
    pl.feature_class = "A"
    pl.feature_code = "PCLI"
    pl.name_bias = 0.0
    pl.id_bias = 0.0
    pl.country_code = ctry.cc_iso2
    pl.country_code_fips = ctry.cc_fips
    pl.adm1 = "0"
    pl.source = "ISO"
    if ctry.is_territory:
        pl.feature_code = "PCL"
    pl.set_location(ctry.lat, ctry.lon)
    return pl


def load_countries(csvpath=None):
    """ parses Xponents Core/src/main/resource CSV file country-names-2015.csv
        putting out an array of Country objects.
        :return: array of Country
    """
    if not csvpath:
        pkg_dir = os.path.dirname(os.path.abspath(__file__))
        csvpath = os.path.join(pkg_dir, 'resources', 'country-names-2021.csv')

    count = 0
    with open(csvpath, 'r', encoding="UTF-8") as fh:
        columns = "country_name,FIPS_cc,ISO2_cc,ISO3_cc,unique_name,territory,latitude,longitude".split(',')
        fio = get_csv_reader(fh, columns)
        for row in fio:

            # ignore empty row and header.
            if 'country_name' not in row:
                continue
            if row['country_name'] == 'country_name':
                continue
            count += 1
            C = Country()
            C.name = row.get('country_name')
            C.cc_iso2 = row.get('ISO2_cc').upper()
            C.cc_iso3 = row.get('ISO3_cc').upper()
            C.cc_fips = row.get('FIPS_cc').upper()

            # Internal data set "place ID"
            C.place_id = f"C{C.cc_iso2}#{C.cc_fips}#{count}"

            C.is_name_unique = get_bool(row.get('unique_name'))
            C.is_territory = get_bool(row.get('territory'))
            C.namenorm = C.name.lower()
            C.set(row.get("latitude"), row.get("longitude"))

            countries.append(C)

    for C in countries:
        if not C.is_territory and C.cc_iso2 not in countries_by_iso:
            countries_by_iso[C.cc_iso2] = C
            countries_by_iso[C.cc_iso3] = C

        if C.cc_fips and C.cc_fips != "*":
            countries_by_fips[C.cc_fips] = C

        countries_by_name[C.namenorm] = C

    global __loaded
    __loaded = len(countries_by_iso) > 1
    return countries


def get_us_province(adm1: str):
    """

    :param adm1:  ADM1 code or for territories,
    :return:
    """
    if not usstates:
        raise Exception("Run load_us_provinces() first")
    return usstates.get(adm1)


def load_us_provinces():
    """
    Load, store internally and return the LIST of US states.
    NOTE: Place objects for US States have a location (unlike list of world provinces).
    To get location and feature information in full, you must use the SQLITE DB or Xponents Solr.
    :return: array of Place objects
    """
    pkg_dir = os.path.dirname(os.path.abspath(__file__))
    csvpath = os.path.join(pkg_dir, 'resources', 'us-state-metadata.csv')
    usstate_places = []
    with open(csvpath, 'r', encoding="UTF-8") as fh:
        columns = ["POSTAL_CODE", "ADM1_CODE", "STATE", "LAT", "LON", "FIPS_CC", "ISO2_CC"]
        io = get_csv_reader(fh, columns)
        for row in io:
            if row['POSTAL_CODE'] == 'POSTAL_CODE': continue

            cc = row["ISO2_CC"]
            adm1_code = row["ADM1_CODE"][2:]
            postal_code = row["POSTAL_CODE"]
            # HASC path
            place_id = make_HASC(cc, adm1_code)
            postal_id = make_HASC(cc, row["POSTAL_CODE"])
            adm1 = Place(place_id, row["STATE"], lat=row["LAT"], lon=row["LON"])
            adm1.feature_class = "A"
            adm1.feature_code = "ADM1"
            adm1.name_type = "N"

            adm1.country_code = cc
            adm1.adm1 = adm1_code
            adm1.adm1_postalcode = row["POSTAL_CODE"]
            adm1.source = "OpenSextant"

            # Code alone:
            usstates[adm1_code] = adm1
            usstates[postal_code] = adm1
            usstates[place_id] = adm1
            usstates[postal_id] = adm1

            usstate_places.append(adm1)
    return usstate_places


def load_provinces():
    """
    Load, store and return a dictionary of ADM1 boundary names - provinces, states, republics, etc.
    NOTE: Location information is not included in this province listing.  Just Country, ADM1, Name tuples.
    :return:  dict
    """
    return load_world_adm1()


def load_world_adm1():
    """
    Load, store and return a dictionary of ADM1 boundary names - provinces, states, republics, etc.
    :return:  dict
    """
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
            adm1.adm1 = parse_admin_code(cc_adm1[1])
            adm1.source = "G"  # Geonames.org coded.
            hasc = make_HASC(adm1.country_code, adm1.adm1)
            if adm1.country_code == "US":
                adm1.source = "USGS"
                if hasc in usstates:
                    us_place = usstates[hasc]
                    us_place.name = adm1.name
                    hasc = make_HASC(us_place.country_code, us_place.adm1)
                    adm1_by_hasc[hasc] = adm1

            adm1_by_hasc[hasc] = adm1
    return adm1_by_hasc


def get_province(cc, adm1):
    """ REQUIRES you load_provinces() first.
    """
    return adm1_by_hasc.get(make_HASC(cc, adm1))


def get_country(namecode, standard="ISO"):
    """
    Get Country object given a name, ISO or FIPS code.  For codes, you must be
    clear about which standard the code is based in. Some code collisions exist.
    "ZZ" will NOT be returned for the empty code -- if you pass in a NULL or empty
    country code you may have a data quality issue.
    :param namecode: 2- or 3-alpha code.
    :param standard: 'ISO' or 'FIPS', 'name'
    :return:  Country object
    """
    if not namecode or not isinstance(namecode, str):
        return None

    if not __loaded:
        load_countries()

    lookup = namecode.upper()
    if standard == "ISO":
        return countries_by_iso.get(lookup)
    elif standard == "FIPS":
        return countries_by_fips.get(lookup)
    elif standard == "name":
        return countries_by_name.get(namecode.lower())
    else:
        raise Exception("That standards body '{}' is not known for code {}".format(standard, namecode))


def load_major_cities():
    """
    Loads City geo/demographic information -- this does not try to parse all name variants.
    :return:
    """
    pkg_dir = os.path.dirname(os.path.abspath(__file__))
    csvpath = os.path.join(pkg_dir, 'resources', 'geonames.org', 'cities15000.txt')

    from csv import reader
    with open(csvpath, 'r', encoding="UTF-8") as fh:
        rdr = reader(fh, dialect="excel", delimiter="\t")
        cities = []
        for line in rdr:
            if len(line) != 19:
                continue
            if not line[4]:
                print("Not location info for City ~ ", line[0])
                continue
            #          ID       NAME     LAT                 LON
            pl = Place(line[0], line[1], lat=float(line[4]), lon=float(line[5]))
            pl.feature_class = line[6]
            pl.feature_code = line[7]
            pl.country_code = line[8]
            pl.adm1 = parse_admin_code(line[10])
            pl.adm2 = line[11]
            pl.geohash = geohash_encode(pl.lat, pl.lon, precision=6)
            try:
                pl.population = int(line[14])
                pl.population_scale = popscale(pl.population, feature="city")
            except:
                pass
            cities.append(pl)
    return cities


_pop_scale = {
    "city": 13,  # 2^13 ~    8,000
    "district": 15,  # 2^15 ~   32,000
    "province": 17,  # 2^17 ~  130,000
}


def popscale(population, feature="city"):
    """
    Given a population in context of the feature -- provide a
    approximation of the size of the feature on a 10 point scale.

    Approximations for 10 points:
    Largest city is ~15 million
    // Few cities top 30 million, e.g., 2^25.  popscale = 25 - 13 = 12.
    Largest province is ~135 million

    :param population:
    :param feature:  city, district, or province allowed.
    :return: index on 0..10 scale.
    """
    if population < 1:
        return 0
    shifter = _pop_scale.get(feature, 20)
    index = mathlog(population, 2) - shifter
    return int(index) if index > 0 else 0


def is_political(feat_code: str):
    """Test a feature code"""
    if not feat_code: return False
    return feat_code.startswith("PCL")


def is_country(feat_code: str):
    """Test a feature code"""
    return "PCLI" == feat_code


def is_administrative(feat: str):
    if not feat: return False
    return "A" == feat.upper()


def is_populated(feat: str):
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

    def populate(self, attrs: dict):
        """
        Populate a TextMatch to normalize the set of attributes -- separate class fields on TextMatch from additional
        optional attributes.
        :param attrs: dict of standard Xponents API outputs.
        :return:
        """
        self.id = attrs.get("match-id")
        self.label = attrs.get("type")
        self.attrs.update(attrs)
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


class PlaceCandidate(TextMatch):
    """
    A TextMatch representing any geographic mention -- a Place object will
    represent the additional attributes for the chosen place.
    see also in Java org.opensextant.extractors.geo.PlaceCandidate class, which is
    a more in-depth version of this.  This Python class represents the
    response from the REST API, for example.

    """

    def __init__(self, *args, **kwargs):
        TextMatch.__init__(self, *args, **kwargs)
        self.confidence = 0
        self.rules = []
        self.is_country = False
        self.place = None
        # Location certainty is a simple meausre 0.0 to 1.0 to convey confidence + precision in one metric
        self.location_certainty = -1

    def populate(self, attrs: dict):
        """
        Deserialize the attributes dict from either TextMatch schema or Place schema
        :param attrs:
        :return:
        """
        TextMatch.populate(self, attrs)
        geo = Place(None, attrs.get("name"), lat=attrs.get("lat"), lon=attrs.get("lon"))
        if not geo.name:
            geo.name = self.text

        # attribute / schema does not align 100% here.
        geo.country_code = attrs.get("cc")
        geo.adm1 = attrs.get("adm1")
        geo.precision = attrs.get("prec")
        geo.feature_class = attrs.get("feat_class")
        geo.feature_code = attrs.get("feat_code")
        geo.adm1_name = attrs.get("province-name")
        geo.geohash = attrs.get("geohash")
        geo.method = attrs.get("method")

        # Combined match + geo-location confidence:
        self.confidence = attrs.get("confidence")
        if "rules" in attrs:
            # One or more geo-inferencing rules
            self.rules = attrs["rules"].split(";")

        self.is_country = self.label == "country" or is_country(geo.feature_code)
        if self.is_country:
            # Zero out country location; Let user derive country from metadata.
            geo.lat = None
            geo.lon = None
        self.place = geo
        # Items like coordinates and cities, etc receive a location certainty.  Countries do not.
        self.location_certainty = location_accuracy(self.confidence, geo.precision)


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
