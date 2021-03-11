"""
These "Patterns of Life" are strictly examples to illustrate more general reg-ex patterns
 (that is more general than the coordinate and date/time patterns).

 The main objective here is to show how beyond basic regex matches, we can add important business logic to the
 entity extraction process.

"""

from opensextant.FlexPat import PatternMatch, RegexPatternManager


class TelephoneNumber(PatternMatch):
    def __init__(self, *args, **kwargs):
        PatternMatch.__init__(self, *args, **kwargs)
        self.case = PatternMatch.UPPER_CASE

    def normalize(self):
        PatternMatch.normalize(self)
        print("TBD - normalize phone")


class MACAddress(PatternMatch):
    def __init__(self, *args, **kwargs):
        PatternMatch.__init__(self, *args, **kwargs)
        self.case = PatternMatch.UPPER_CASE

    def normalize(self):
        PatternMatch.normalize(self)
        print("TBD - normalize MAC address")


class Money(PatternMatch):
    def __init__(self, *args, **kwargs):
        PatternMatch.__init__(self, *args, **kwargs)
        self.case = PatternMatch.LOWER_CASE

    def normalize(self):
        PatternMatch.normalize(self)
        print("TBD - normalize Money")


class PatternsOfLifeManager(RegexPatternManager):
    #
    # Demonstration.  PatternsOfLifeManager is a custom RegexPatternManager
    # that shows how to apply FlexPat to extracting common things like currency amounts, MAC addresses
    # and telephone numbers.
    #
    def __init__(self, cfg):
        """
        Call as
            mgr = PatternsOfLifeManager("poli_patterns.cfg")
            patternsApp = PatternExtractor( mgr )

            test_results = patternsApp.default_tests()
            real_results = patternsApp.extract( ".... text blob..." )

        :param cfg: patterns config file.
        """
        RegexPatternManager.__init__(self, cfg, debug=True, testing=True)

    def _initialize(self):
        #
        # This Class registry maps the existing Java classes (in the config file) to Python variations here.
        self.match_class_registry = {
            "org.opensextant.extractors.poli.data.TelephoneNumber":
                "opensextant.extractors.poli.TelephoneNumber",

            "org.opensextant.extractors.poli.data.MACAddress":
                "opensextant.extractors.poli.MACAddress",

            "org.opensextant.extractors.poli.data.Money":
                "opensextant.extractors.poli.Money",

            "org.opensextant.extractors.poli.data.EmailAddress":
                "opensextant.extractors.poli.EmailAddress"
        }
        RegexPatternManager._initialize(self)
