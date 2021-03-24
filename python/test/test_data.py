# -*- coding: utf-8 -*-

from opensextant import Coordinate, Place

print("Parsing and Validation of coordinates"
      "=====================================")
yx = Coordinate({'lat': '55.5', 'lon': '111'})
print(yx, " is valid? ", yx.validate())
yx = Coordinate({'lat': '155.5', 'lon': '111'})
print(yx, " is valid? ", yx.validate())
yx = Coordinate({'lat': '55.5', 'lon': '-181'})
print(yx, " is valid? ", yx.validate())

pl = Place('nowhere', 'Nowhere Really', lat='55.5', lon='-181')
pl.adm1 = 'OBS'
pl.adm1_name = 'State of Obsoletion'
print(str(pl))

print("Text manipulation and testing"
      "=====================================")

#from opensextant import PY3
from opensextant.utility import is_ascii, is_text, bytes2unicode

assert not is_ascii('ée')
assert not is_ascii(u'ée')
# if not PY3:
#    assert not is_ascii(unicode(u'ée'))

assert is_ascii('EE')

assert not is_text(0)
assert is_text("abc")
assert is_text(u'eé')

val = bytes2unicode(u'ée'.encode("iso-8859-1"))
print(val)
