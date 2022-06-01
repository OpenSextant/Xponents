"""

Created on Dec 5, 2017

@author: ubaldino
"""
from unittest import TestCase, main


class TestReferenceData(TestCase):

    def test_api(self):
        from opensextant import get_country

        print("Lesser known countries ~ Kosovo")
        kosovo1 = get_country("XK", standard="ISO")
        kosovo2 = get_country("kOsOvO", standard="name")
        assert kosovo2 == kosovo1

        print("Well known country ~ USA")
        usa1 = get_country("USA")
        usa2 = get_country("United States", standard="name")
        assert usa1 == usa2

        assert get_country("United States") is None
        try:
            get_country("United States", standard="No Such Standard")
        except:
            print("Error on bad standard arg")
            assert True

    def test_loading(self):
        from opensextant import load_provinces, get_province, load_countries, countries_by_iso

        print("Loading")
        load_provinces()
        load_countries()

        tests = [('ES', '07'), ('US', '06'), ('NOTHING', 'X')]
        for prov in tests:
            pl = get_province(prov[0], prov[1])
            if pl:
                print(prov, pl.name, pl.country_code)
            else:
                print(prov, "Not found")

        print("Kosovo is a country? sovereign country? yes")
        assert countries_by_iso.get("XK") is not None


    def test_cities(self):
        from opensextant import load_major_cities
        data = load_major_cities()
        print(len(data))

    def test_popscale(self):
        from opensextant import popscale

        for pop in [0, 8000, 9000, 15000, 16000, 17000, 33000, 56000, 120000, 150000, 15000000]:
            print( f"City of {pop}.  SCALE =", popscale(pop))

        for pop in [150000, 250000, 1000000]:
            print( f"District of {pop}.  SCALE =", popscale(pop, feature="district"))

        for pop in [1500000, 2500000, 10000000]:
            print( f"Province of {pop}.  SCALE =", popscale(pop, feature="province"))


if __name__ == '__main__':
    main()
