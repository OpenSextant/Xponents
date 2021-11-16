from opensextant import load_major_cities, load_countries, get_country, load_us_provinces, load_provinces
from unittest import TestCase
# These fundamental data sets are flat-file resources in the API,... they are all varations of
# "official" gazetteer data.  Whereas the opensextant.gazetteer.DB class operates with the full set
# of data from the SQLite master file.  The difference is 3 orders of magnitude of data.

class TestGazetteerAPI(TestCase):
    def test_gazetteer_api_sample(self):
        print("Most objects derive from Place class or Country class")
        print("\n===========================")
        print("Working with Countries")
        data = load_countries()
        C = get_country("FR")
        print("country: ", C)
        C = get_country("IZ", standard="FIPS")
        print("country: ", C)
        print("API country list length:", len(data))

        assert len(data) > 0

        print("\n===========================")
        print("Working with Major Cities")
        data = load_major_cities()
        print("city", data[0])
        print("city", data[1])
        print(".....")
        print("API major city count", len(data))
        assert len(data) > 0

        print("\n===========================")
        print("Working with Provinces")
        data = load_provinces()
        count=0
        for adm1_id in data:
            print("province", data[adm1_id])
            count+=1
            if count > 10:
                break
        print("....")
        print("API province count", len(data))
        assert len(data) > 0

        print("\n===========================")
        print("Working with US States only")
        data = load_us_provinces()
        print("province", data[0])
        print("province", data[1])
        print(".....")
        print("API US state count", len(data))
        assert len(data) > 0
