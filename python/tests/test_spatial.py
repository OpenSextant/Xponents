# -*- coding: utf-8 -*-

from unittest import TestCase, main
from opensextant import geohash_encode, geohash_neighbors, geohash_cells, radial_geohash, geohash_cells_radially


def print_cells(lat, lon):
    pt = geohash_encode(lat, lon)
    print(f"Point of interest {lat},{lon} is ", pt)
    r = 50
    print(f"Geohash corners for {lat},{lon} R={r}", radial_geohash(lat, lon, r))
    r = 5000
    print(f"Geohash corners for {lat},{lon} R={r}", radial_geohash(lat, lon, r))
    r = 25000
    print(f"Geohash corners for {lat},{lon} R={r}", radial_geohash(lat, lon, r))

    r = 50
    cells = geohash_cells_radially(lat, lon, r)
    print(f"Containing cells R={r}", cells)
    r = 5000
    cells = geohash_cells_radially(lat, lon, r)
    print(f"Containing cells R={r}", cells)
    r = 25000
    cells = geohash_cells_radially(lat, lon, r)
    print(f"Containing cells R={r}", cells)


class TestSpatialQuery(TestCase):
    def test_geohash_neighbors(self):
        # vary latitude to see how cells change from equator to poles
        for lat, lon in [(0, -118), (45, -118), (65, -118), (-75, -118)]:
            print_cells(lat, lon)

        # Pygeodesy testing:
        xx = "9q5fpg"
        print(geohash_neighbors(xx))
        xx = "9q5f"
        print(geohash_neighbors(xx))

        # Range finder -- using a radius approximation in geohash:
        print("Range finder around a high-precision geohash")
        xx = "9q5fpgtt"
        cells = geohash_cells(xx, radius=400)
        self.assertEqual(8, len(cells))
        cells = geohash_cells(xx, radius=1500)
        self.assertEqual(8, len(cells))
        cells = geohash_cells(xx, radius=2500)
        self.assertEqual(8, len(cells))
        cells = geohash_cells(xx, radius=15000)
        self.assertEqual(8, len(cells))
        # Additionally, the length of proposed cells here will vary based on radius.
        self.assertEqual(4, len(cells.get("NW")))


if __name__ == "__main__":
    main()