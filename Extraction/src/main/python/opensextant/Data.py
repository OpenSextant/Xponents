'''
Xponents Python Data: see comparable classes in Xponents Basics data pkg in Java.
'''
class Country:
   '''  Country metadata
   '''
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
       return u'%s (%s)' %(self.name, self.cc_iso2)


def validate_lat(f):
    return (f >= -90.0) and (f <= 90.0)


def validate_lon(f):
    return (f >= -180.0) and (f <= 180.0)


class Coordinate:
    ''' Convenient class for Lat/Lon pair.
        Expects a row dict with 'lat' and 'lon', 
        or kwd args 'lat', 'lon'
        @param row default dictionary 
    ''' 
    def __init__(self, row, lat=None, lon=None):

        self.X = 0.0
        self.Y = 0.0
        self.mgrs = None

        if row:
            if (row.has_key('lat') and row.has_key('lon')):
                lat = row['lat']
                lon = row['lon']

        if (lat and lon):
            self.X = float(lon)
            self.Y = float(lat)
        else:
            return None


    def validate(self):
        return validate_lat(self.Y) and validate_lon(self.X) and (self.X is not None and self.Y is not None)

    def set(self, strLAT, strLON):
        self.X = float(strLON)
        self.Y = float(strLAT)

    def __str__(self):
        if self.Y:
            return '%3.4f, %3.4f'  % (self.Y, self.X)
        else:
            return 'unset'

