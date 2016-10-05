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


class Place(Coordinate):
    '''
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
    
    '''
    def __init__(self, pid, name, lat=None, lon=None):
        Coordinate.__init__(self, None, lat=lat, lon=lon)
        self.place_id = pid
        self.name = name

        self.is_ascii = False
        self.is_upper = False
        self.adm1_postalcode = None # Province Postal CODE?
        self.place_postalcode = None # ZIP CODE?
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
        self.precision= -1
        self.method = None;
        self.population = 0
        self.hierachical_path= None

    def has_coordinate(self):
        if self.validate():
            return (self.Y != 0 and self.X != 0)
        return False
    
    def get_location(self):
        ''' Returns (LAT, LON) tuple
        @return: tuple, (lat,lon)
        '''
        return (self.Y, self.X)
        
    def set_location(self, lat, lon):
        self.set(lat, lon)

    def __str__(self):
        crd = 'Unset'
        if self.Y:
            crd = '%3.4f, %3.4f'  % (self.Y, self.X)
        meta = '%s (%s), %s'  %( self.name, self.place_id, self.country_code)
        return ', '.join([meta, crd])
        
    
