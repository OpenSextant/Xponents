# -*- coding: utf-8 -*-
"""
Xponents Python Data: see comparable classes in Xponents Basics data pkg in Java.
"""

from math import sqrt, sin, cos, radians, atan2


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
    return '{:2.6f}, {:3.6f}'.format(float(lat), float(lon))

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
        self.place_id = pid
        self.name = name

        self.is_ascii = False
        self.is_upper = False
        self.adm1_postalcode = None  # Province Postal CODE?
        self.place_postalcode = None  # ZIP CODE?
        self.name_type = None
        self.country = None
        self.country_code = None
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
        return '{}, {} @({})'.format(self.name, self.country_code, format_coord(self.Y, self.X))
