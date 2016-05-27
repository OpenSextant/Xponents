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
