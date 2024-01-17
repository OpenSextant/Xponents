# -*- coding: utf-8 -*-
import os
import re

from opensextant import TextMatch, Extractor, reduce_matches


def resource_for(resource_name):
    """

    :param resource_name: name of a file in your resource path; Default: opensextant/resources/NAME
    :return: file path.
    """
    import opensextant
    libdir = os.path.dirname(opensextant.__file__)
    container = os.path.join(libdir, "resources")
    fpath = os.path.join(container, resource_name)
    if os.path.exists(fpath):
        return fpath
    else:
        raise Exception("FileNotFound: Resource not found where expected at " + fpath)


def class_for(full_classname, instantiate=True):
    """

    :param full_classname:
    :param instantiate: True if you wish the found class return an instance.
    :return: Class obj or Class instance.
    """
    from importlib import import_module
    segments = full_classname.split('.')
    clsname = segments[-1]
    modname = '.'.join(segments[:-1])
    mod = import_module(modname)
    clz = getattr(mod, clsname)
    if not clz:
        raise Exception("Class not found for " + full_classname)
    if instantiate:
        return clz()
    else:
        return clz


class RegexPattern:
    def __init__(self, fam, pid, desc):
        self.family = fam
        self.id = pid
        self.description = desc
        # Pattern
        self.regex = None
        # Ordered group-name list with slots in pattern.
        self.regex_groups = []
        self.version = None
        self.enabled = False
        self.match_classname = None
        self.match_class = None

    def __str__(self):
        return "{}, Pattern: {}".format(self.id, self.regex)


class PatternMatch(TextMatch):
    """
    A general Pattern-based TextMatch.
    This Python variation consolidates PoliMatch (patterns-of-life = poli) ideas in the Java API.
    """

    UPPER_CASE = 1
    LOWER_CASE = 2
    FOUND_CASE = 0

    def __init__(self, *args, pattern_id=None, label=None, match_groups=None):
        TextMatch.__init__(self, *args, label=label)
        # Normalized text match is NONE until normalize() is run.
        self.textnorm = None
        self.pattern_id = pattern_id
        self.case = PatternMatch.FOUND_CASE
        self.match_groups = match_groups
        self.variant_id = None
        self.is_valid = True
        self.confidence = -1
        # PERFORMANCE flag:  omit = True to never return the match value.
        #          It could be filtered out and returned.  But omit means we never see it.
        self.omit = False

        # Optionally -- back fill as much surrounding text as you want for
        # normalizer/validator routines. Use pre_text, post_text
        self.pre_text = None
        self.post_text = None
        if self.pattern_id and "-" in self.pattern_id:
            self.variant_id = self.pattern_id.split("-", 1)[1]

    def copy_attrs(self, arr):
        """
        Default copy of match group slots.  Does not work for every situation.
        :param arr:
        :return:
        """
        for k in arr:
            val = self.get_value(k)
            if val:
                self.attrs[k] = val

    def add_surrounding_text(self, text, text_len, length=16):
        """
        Given this match's span and the text it was derived from,
        populate pre_text, post_text with some # of chars specified by length.

        :param text: The text in which this match was found.
        :param text_len: the length of the text buffer.  (avoid repeating len(text))
        :param length:  the pre/post text length to attach.
        :return:
        """
        if self.start > 0:
            x1 = self.start - length
            if x1 < 0:
                x1 = 0
            self.pre_text = text[x1:self.start]
        if self.end > 0:
            x1 = self.end + length
            if x1 > text_len:
                x1 = text_len
            self.post_text = text[self.end:x1]

    def attributes(self):
        """
        Render domain details to meaningful exported view of the data.
        :return:
        """
        default_attrs = {"method": self.pattern_id}
        for (k, v, x1, x2) in self.match_groups:
            default_attrs[k] = v
        return default_attrs

    def normalize(self):
        if not self.text:
            return

        self.textnorm = self.text.strip()
        if self.case == PatternMatch.UPPER_CASE:
            self.textnorm = self.textnorm.upper()
        elif self.case == PatternMatch.LOWER_CASE:
            self.textnorm = self.textnorm.lower()

    def get_value(self, k):
        """
        Get Slot value -- returns first one.
        :param k:
        :return:
        """
        grp = get_slot(self.match_groups, k)
        if grp:
            # tuple is group_name, value, start, end. Return value:
            return grp[1]
        return None


def get_slot(grps, k):
    """
    Given array of match groups, return first key matching
    :param grps:
    :param k:
    :return: tuple matching.
    """
    for g in grps:
        key, v, x1, x2 = g
        if key == k:
            return g
    return None


class PatternTestCase:
    def __init__(self, tid, family, text):
        self.id = tid
        self.family = family
        self.text = text
        self.true_positive = True


def get_config_file(cfg, modfile):
    """
    Locate a resource file that is collocated with the python module, e.g., get_config_file("file.cfg", __file__)
    :param cfg:
    :param modfile:
    :return:
    """
    pkgdir = os.path.dirname(os.path.abspath(modfile))
    patterns_file = os.path.join(pkgdir, cfg)
    if os.path.exists(patterns_file):
        return patterns_file
    raise FileNotFoundError("No such file {} at {}".format(cfg, patterns_file))


class RegexPatternManager:
    """
    RegexPatternManager is the patterns configuration file parser.
    See documentation: https://opensextant.github.io/Xponents/doc/Patterns.md

    """

    def __init__(self, patterns_cfg, module_file=None, debug=False, testing=False):
        self.families = set([])
        self.patterns = {}
        self.patterns_file = patterns_cfg
        if module_file:
            # Resolve this absolute path now.
            self.patterns_file = get_config_file(patterns_cfg, module_file)

        self.patterns_file_path = None
        self.test_cases = []
        self.matcher_classes = {}
        # CUSTOM: For mapping Python and Java classes internally.  Experimental.
        self.match_class_registry = {}
        self.testing = testing
        self.debug = debug
        self._initialize()

    def get_pattern(self, pid):
        return self.patterns.get(pid)

    def create_pattern(self, fam, rule, desc):
        """ Override pattern class creation as needed.
        """
        return RegexPattern(fam, "{}-{}".format(fam, rule), desc)

    def create_testcase(self, tid, fam, text):
        return PatternTestCase(tid, fam, text)

    def validate_pattern(self, repat):
        """Default validation is True
        Override this if necessary, e.g., pattern implementation has additional metadata
        """
        return repat is not None

    def enable_all(self):
        for k in self.patterns:
            pat = self.patterns[k]
            pat.enabled = True

    def disable_all(self):
        for k in self.patterns:
            pat = self.patterns[k]
            pat.enabled = False

    def set_enabled(self, some: str, flag: bool):
        """
        set family enabled or not
        :param some: prefix of a family or family-variant
        :param flag: bool setting
        :return:
        """
        for k in self.patterns:
            pat = self.patterns[k]
            if pat.id.startswith(some):
                pat.enabled = flag

    def _initialize(self):
        """
        :raise Exception if item not found.
        :return:
        """
        self.patterns = {}

        # the  # RULE statements as name and a sequence of DEFINES and regex bits
        defines = {}
        rules = {}
        # Preserve order
        rule_order = []
        # Record pattern setup and validation messages
        configMessages = []

        config_fpath = self.patterns_file
        if not os.path.exists(self.patterns_file):
            config_fpath = resource_for(self.patterns_file)

        # By now we have tried the given file path, inferred a path local to the calling module
        # and lastly tried a resource folder in opensextant/resource/ data.
        if not os.path.exists(config_fpath):
            raise FileNotFoundError("Tried various absolute and inferred paths for the file '{}'".format(
                os.path.basename(self.patterns_file)))

        # PY3:
        with open(config_fpath, "r", encoding="UTF-8") as fh:
            testcount = 0
            for line in fh:
                stmt = line.strip()
                if line.startswith("#DEFINE"):
                    # #DEFINE<tab><defineName><tab><definePattern>
                    fields = re.split("[\t ]+", stmt, 2)
                    defines[fields[1]] = fields[2]
                elif line.startswith("#RULE"):
                    # #RULE<tab><rule_fam><tab><rule_id><tab><pattern>
                    fields = re.split("[\t ]+", stmt, 3)

                    fam = fields[1]
                    ruleEnum = fields[2]
                    rulePattern = fields[3]
                    ruleKey = fam + "-" + ruleEnum

                    # if already a rule by that name, error
                    if ruleKey in rules:
                        raise Exception("FlexPat Config Error - Duplicate rule name " + ruleEnum)

                    rules[ruleKey] = rulePattern
                    rule_order.append(ruleKey)
                elif self.testing and stmt.startswith("#TEST"):
                    fields = re.split("[\t ]+", stmt, 3)
                    testcount += 1

                    fam = fields[1]
                    ruleEnum = fields[2]
                    testtext = fields[3].strip().replace("$NL", "\n")
                    ruleKey = fam + "-" + ruleEnum

                    # testcount is a count of all tests, not just test within a rule family
                    testKey = "{}#{}".format(ruleKey, testcount)
                    self.test_cases.append(self.create_testcase(testKey, fam, testtext))
                elif stmt.startswith("#CLASS"):
                    fields = re.split("[\t ]+", stmt, 2)
                    fam = fields[1]
                    self.matcher_classes[fam] = fields[2]
                else:
                    pass

        elementRegex = "<[a-zA-Z0-9_]+>"
        elementPattern = re.compile(elementRegex)

        for tmpkey in rule_order:
            tmpRulePattern = rules.get(tmpkey)
            fam, rule_name = tmpkey.split("-", 1)
            self.families.add(fam)

            pat = self.create_pattern(fam, rule_name, "No Description yet...")
            if fam in self.matcher_classes:
                try:
                    pat.match_classname = self.matcher_classes.get(fam)
                    if pat.match_classname in self.match_class_registry:
                        # rename. Map Java class to a Python class.
                        pat.match_classname = self.match_class_registry[pat.match_classname]

                    # Do not instantiate, just find the class named in config file.
                    pat.match_class = class_for(pat.match_classname, instantiate=False)
                except Exception as err:
                    print(err)

            # find all of the element definitions within the pattern
            groupNum = 1
            for m in elementPattern.finditer(tmpRulePattern):
                e1 = m.start()
                e2 = m.end()
                elementName = tmpRulePattern[e1 + 1: e2 - 1]
                pat.regex_groups.append(elementName)

                if self.debug:
                    subelementPattern = defines.get(elementName)
                    configMessages.append("\n\t")
                    configMessages.append("{} {} = {}".format(groupNum, elementName, subelementPattern))
                groupNum += 1

            for slot_name in set(pat.regex_groups):
                if slot_name not in defines:
                    raise Exception("Slot definition is not DEFINED for " + slot_name)

                tmpDef = defines[slot_name]
                # NOTE:  Use of parens, "(expr)", is required to create groups within a pattern.
                tmpDefPattern = "({})".format(tmpDef)
                tmpDefSlot = "<{}>".format(slot_name)
                # Replaces all.
                tmpRulePattern = tmpRulePattern.replace(tmpDefSlot, tmpDefPattern)

            if self.debug:
                configMessages.append("\nrulepattern=" + tmpRulePattern)

            pat.regex = re.compile(tmpRulePattern, re.IGNORECASE)
            pat.enabled = True
            self.patterns[pat.id] = pat
            if not self.validate_pattern(pat):
                raise Exception("Invalid Pattern " + str(pat))

        if self.debug:
            configMessages.append("\nFound # of PATTERNS={}".format(len(self.patterns)))


def _digest_sub_groups(m, pattern_groups):
    """
    Reorganize regex groups internally.
    :param pattern_groups: ordered list of groups as they appear in RE
    :return: array only found item tuples:  (group, value, start, end)
    """
    count = 0
    slots = []
    glen = len(pattern_groups)
    for found in m.groups():
        if count > glen:
            raise Exception("Unexpected -- more slots found than groups in pattern.")
        slot_name = pattern_groups[count]
        slot = (slot_name, found, m.start(count + 1), m.end(count + 1))
        slots.append(slot)
        count += 1

    return slots


class PatternExtractor(Extractor):
    """
        Discussion: Read first https://opensextant.github.io/Xponents/doc/Patterns.md

        Example:
        ```
        from opensextant.extractors.poli import PatternsOfLifeManager
        from opensextant.FlexPat import PatternExtractor

        # INIT
        #=====================
        # Invoke a particular REGEX rule set, here poli_patterns.cfg
        # @see https://github.com/OpenSextant/Xponents/blob/master/Core/src/main/resources/poli_patterns.cfg
        mgr = PatternsOfLifeManager("poli_patterns.cfg")
        pex = PatternExtractor(mgr)

        # DEV/TEST
        #=====================
        # "default_test()" is useful to run during development and
        # encourages you to capture critical pattern variants in your "TEST" data.
        # Look at your pass/fail situations -- what test cases are failing your rule?
        test_results = pex.default_tests()
        print("TEST RESULTS")
        for result in test_results:
            print(repr(result))

        # RUN
        #=====================
        real_results = pex.extract(".... text blob 1-800-123-4567...")
        print("REAL RESULTS")
        for result in real_results:
            print(repr(result))
            print("\tRAW DICT:", render_match(result))
        ```
    """

    def __init__(self, pattern_manager):
        """
        invoke RegexPatternManager(your_cfg_file) or implement a custom RegexPatternManager (rare).
        NOTE - `PatternsOfLifeManager` is a  particular subclass of RegexPatternManager becuase
        it is manipulating the input patterns config file which is shared with the Java demo.
        The `CLASS` names unfortunately are specific to Python or Java.

        :param pattern_manager: RegexPatternManager
        """
        Extractor.__init__(self)
        self.id = "xpx"
        self.name = "Xponents Pattern Extractor"
        self.pattern_manager = pattern_manager

    def extract(self, text, **kwargs):
        """ Default Extractor API. """
        return self.extract_patterns(text, **kwargs)

    def extract_patterns(self, text, **kwargs):
        """
        Given some text input, apply all relevant pattern families against the text.
        Surrounding text is added to each match for post-processing.
        :param text:
        :param kwargs:
        :return:
        """
        features = kwargs.get("features")
        if not features:
            features = self.pattern_manager.families

        tlen = len(text)
        results = []
        for fam in features:
            if fam not in self.pattern_manager.families:
                raise Exception("Uknown Pattern Family " + fam)

            for pat_id in self.pattern_manager.patterns:
                pat = self.pattern_manager.patterns[pat_id]
                if not pat.family == fam:
                    continue
                if not pat.enabled:
                    continue

                for m in pat.regex.finditer(text):
                    digested_groups = _digest_sub_groups(m, pat.regex_groups)
                    if pat.match_class:
                        domainObj = pat.match_class(m.group(), m.start(), m.end(),
                                                    pattern_id=pat.id,
                                                    label=pat.family,
                                                    match_groups=digested_groups)
                        # surrounding text may be used by normalization and validation
                        domainObj.add_surrounding_text(text, tlen, length=20)
                        domainObj.normalize()
                        if not domainObj.omit:
                            results.append(domainObj)
                    else:
                        genericObj = PatternMatch(m.group(), m.start(), m.end(),
                                                  pattern_id=pat.id,
                                                  label=pat.family,
                                                  match_groups=digested_groups)
                        genericObj.add_surrounding_text(text, tlen, length=20)
                        results.append(genericObj)

        # Determine if any matches are redundant.  Mark redundancies as "filtered out".
        reduce_matches(results)
        for r in results:
            if r.is_duplicate or r.is_submatch:
                r.filtered_out = True

        return results

    def default_tests(self, scope="rule"):
        """
        Default Tests run all TEST cases for each RULE in patterns config.
        TESTs marked with a 'FAIL' comment are intended to return 0 matches or only matches that are filtered out.
        Otherwise a TEST is intended to return 1 or more matches.

        By default, this runs each test and observes only results that were triggered by that rule being tested.
        If scope is "ruleset" then any results from any rule will be allowed.
        "rule" scope is much better for detailed rule development as it tells you if your rule tests are testing the
        right thing.
        
        Runs the default tests on the provided configuration. Plenty of debug printed to screen.
        But returns the test results as an array, e.g., to write to CSV for review.
        This uses PatternExtractor.extract_patterns() to avoid any collision with the generic use
        of  Extractor.extract() parent method.
        :param scope: rule or ruleset.  Rule scope means only results for rule test case are evaluated.
                 ruleset scope means that all results for a test are evaluated.
        :return: test results array; Each result represents a TEST case run against a RULE
        """
        test_results = []
        for t in self.pattern_manager.test_cases:
            expect_valid_match = "FAIL" not in t.text
            output1 = self.extract_patterns(t.text, features=[t.family])

            output = []
            for m in output1:
                if scope == "rule" and not t.id.startswith(m.pattern_id):
                    continue
                output.append(m)

            # Determine if pattern matched true positive or false positive.
            # To condition the TP or FP based on the matches
            #  keep a running tally of whether each match is filtered or not.
            # That is, for many matches True Positive = at least one unfiltered match is needed, AND was expected.
            #          for many matches False Positive = at least one unfiltered match is needed, AND was NOT expected.
            fpcount = 0
            tpcount = 0
            for m in output:
                allowed = not m.filtered_out or (m.is_duplicate and m.filtered_out)
                if expect_valid_match and allowed:
                    tpcount += 1
                if not expect_valid_match and allowed:
                    fpcount += 1

            tp = tpcount > 0 and expect_valid_match
            fp = fpcount > 0 and not expect_valid_match
            tn = fpcount == 0 and not expect_valid_match
            fn = tpcount == 0 and expect_valid_match
            success = (tp or tn) and not (fp or fn)
            test_results.append({"TEST": t.id,
                                 "TEXT": t.text,
                                 "MATCHES": output,
                                 "PASS": success})

        return test_results


def print_test(result: dict):
    """ print the structure from default_tests()
    """
    if not result:
        return

    tid = result["TEST"]
    txt = result["TEXT"]
    res = result["PASS"]
    matches = "<None>"
    if result["MATCHES"]:
        arr = result["MATCHES"]
        matches = ";".join([match.text for match in arr])
    print(f"TEST: {tid}, TEXT: {txt} PASS:{res}\tMATCHES: {matches}")
