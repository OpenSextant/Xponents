from opensextant.Data import Coordinate


yx = Coordinate({'lat':'55.5', 'lon':'111'})
print yx, " is valid? ", yx.validate()
yx = Coordinate({'lat':'155.5', 'lon':'111'})
print yx, " is valid? ", yx.validate()
yx = Coordinate({'lat':'55.5', 'lon':'-181'})
print yx, " is valid? ", yx.validate()

