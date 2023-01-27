import unittest
from opensextant import get_language, get_lang_name, get_lang_code, is_lang_euro, is_lang_chinese, list_languages, Language
from opensextant.utility import load_datafile


class TestLangs(unittest.TestCase):
    def test_lookup(self):
        self.assertIsNotNone(get_language("eng"))
        self.assertIsNotNone(get_language("zho"))
        self.assertIsNotNone(get_language("zh"))

        self.assertTrue(len(list_languages()) > 0)

        print(get_language("en"))

    def test_class(self):
        try:
            L = Language("AAA", "AA", "NOT VALID")
            print(L)
            self.fail("Exception expected")
        except Exception as err:
            self.assertTrue(True, "Exception thrown:"+str(err))

    def test_individuals(self):
        print("Name for 'fre'", get_lang_name("fre"))
        print("Code for 'FRENCH'", get_lang_code("FRENCH"))
        print("FRA is Euro?", is_lang_euro("FRA"))
        print("ZH-CN Locale is ...?", is_lang_chinese("ZH-CN"))

        # No exception thrown.
        self.assertTrue(True)

    def x_test_utf8_bom(self):
        load_datafile("test-utf8.txt", delim="|")


if __name__ == '__main__':
    unittest.main()
