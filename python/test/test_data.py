from opensextant.Data import Coordinate, Place

yx = Coordinate({'lat': '55.5', 'lon': '111'})
print yx, " is valid? ", yx.validate()
yx = Coordinate({'lat': '155.5', 'lon': '111'})
print yx, " is valid? ", yx.validate()
yx = Coordinate({'lat': '55.5', 'lon': '-181'})
print yx, " is valid? ", yx.validate()

pl = Place('nowhere', 'Nowhere Really', lat='55.5', lon='-181')
pl.adm1 = 'OBS'
pl.adm1_name = 'State of Obsoletion'
print str(pl)
