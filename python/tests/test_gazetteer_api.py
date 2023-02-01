import os
from unittest import TestCase, main
from opensextant import load_major_cities, load_countries, get_country, load_us_provinces, load_provinces
from opensextant.gazetteer import AdminLevelCodes


# These fundamental data sets are flat-file resources in the API,... they are all varations of
# "official" gazetteer data.  Whereas the opensextant.gazetteer.DB class operates with the full set
# of data from the SQLite master file.  The difference is 3 orders of magnitude of data.

class TestGazetteerAPI(TestCase):

    def setUp(self) -> None:
        # For development:
        # self.admin_lookup = AdminLevelCodes(os.path.join("solr", "etc", "gazetteer", "global_admin1_mapping.json"))
        # Library use, after global mapping was produced.
        self.admin_lookup = AdminLevelCodes()

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
        count = 0
        for adm1_id in data:
            print("province", data[adm1_id])
            count += 1
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

    def test_admin_lookups(self):

        print("Testing Admin Lookups")
        print("Test US.MA lookup")
        test = self.admin_lookup.get_alternate_admin1("US", "MA", "ISO")
        self.assertEqual("25", test)
        print("Test AF.NIM lookup")
        test = self.admin_lookup.get_alternate_admin1("AF", "NIM", "ISO")
        self.assertEqual("19", test)

        print("Test AG.05 lookup")
        test = self.admin_lookup.get_alternate_admin1("AG", "05", "FIPS")
        self.assertEqual("05", test)

        # Odd switch FIPS 10 becomes ISO 01 in KENYA
        #            ...  19 becomes ISO 10 ........
        # So, CALLER must know what the starting standard is.
        #    We can say       FIPS(KE.19) = ISO(KE.10)
        #    But do not say     KE.19 <=> KE.10  are interchangeable without knowing standard/context.
        print("Test KE.01 lookup")
        test = self.admin_lookup.get_alternate_admin1("KE", "01", "ISO")
        self.assertEqual("10", test)

        print("FIPS KE.02 has what ISO alternates?",
              self.admin_lookup.get_alternate_admin1("KE", "02", "FIPS"))

        # ISO KE.19
        adm1 = self.admin_lookup.get_alternate_admin1("KE", "19", "ISO")
        self.assertTrue("28" in adm1)
        adm1 = self.admin_lookup.get_alternate_admin1("KE", "19", "FIPS")
        self.assertTrue("10" in adm1)

        # FIPS KE.10
        adm1 = self.admin_lookup.get_alternate_admin1("KE", "10", "FIPS")
        self.assertTrue("01" in adm1)

        # Evidence:  These place IDs reside in various boundaries, but based on different codebooks
        #    Reference: http://www.statoids.com/uke.html
        # "KE.10": ["N-2242161", "G200573", "N-2247473"]
        # "KE.19": ["N-2252682", "G7667657", "N-2247473"]
        # "KE.28": ["N-2252682", "N-2256516", "G190106"]


if __name__ == '__main__':
    main()
