import os
import re
import codecs
from opensextant.Extraction import TextMatch, Extractor

"""
FlexPat: regular-expression assistant. 
Create a pattern library that is easy to read, adapt, test and share.... let alone execute and diagnose.

FlexPat is a configuration-based approach to employing regular expressions. Using the well-defined
syntax design your entity patterns in families of RULEs. 

Repetitive fields or sub-patterns are defined with DEFINEs -- compose RULEs with your DEFINEs, other litterals
and regular expressions.  DEFINES are formal groups that are available after matching so you can use those named groups
for validation.   ((Yes, Python has named groups in re module; but to support any programming language we cannot
rely on named groups still.))

For each RULE family you have rule variations. For each variation you should have TEST cases that provide example
texts containing true positives and true negatives.  TEST cases intended to either NOT match or match, but invalidate
the match should be marked as "FAIL". 

Specific uses:

* opensextant.extractors.poli:  a refactoring of the Java PatternsOfLife extractor. The idea here is to demonstrate
  how to manage many RULE families, but call them as needed easily.

* opensextant.extractors.xcoord: TBD. These topics would demonstrate an extractor app intended to run all RULEs, 
  indiscriminately to find all possible entities. XCoord (Java) is for geo coordinate extraction & normalization.

* opensextant.extractors.xtemporal: TBD. ditto. XTemporal is for date/time extraction & normalization.

Complete user manual TBD 2019.

TODO:
* limitation  -- RULE patterns may not have other unnamed groups "()" literally in the pattern. Only groups allowed
  are those in DEFINE. 
"""

NOT_SUBMATCH = 0
IS_SUBMATCH = 1
IS_DUPLICATE = 2


def reduce_matches(matches):
    """
    Mark each match if it is a submatch or overlap or exact duplicate of other.
    :param matches: array of TextMatch (or TextEntity). This is the more object oriented version
    of reduce_matches_dict
    :return:
    """
    if len(matches) < 2:
        return
    loop = 0
    for M in matches:
        loop += 1
        m1 = M.start
        m2 = M.end
        for N in matches[loop:]:
            n1 = N.start
            n2 = N.end

            if m2 < n1:
                # M entirely before N
                continue
            if m1 > n2:
                # M < entirely after N
                continue

            if n1 == m1 and n2 == m2:
                # Exact duplicate - Mark N as dup, as M is first in array.
                N.is_duplicate = True
                break

            if n1 <= m1 < m2 <= n2:
                # M is within N span
                M.is_submatch = True
                break

            if m1 <= n1 < n2 <= m2:
                # N is within M span
                N.is_submatch = True
                break


def reduce_matches_dict(matches):
    """
    Accepts an array annotations (dict). Inserts the "submatch" flag in dict if there is a
    submatch (that is, if another TextEntity A wholly contains another, B -- B is a submatch).
    We just have to loop through half of the array ~ comparing each item to each other item once.

    :param matches: array of dicts.
    """
    _max = len(matches)
    if _max < 2:
        return

    loops = 0
    for i in range(0, _max):
        M = matches[i]
        m1 = M['start']
        m2 = M['end']

        for j in range(i + 1, _max):
            loops += 1
            N = matches[j]
            n1 = N['start']
            n2 = N['end']

            if m2 < n1:
                # M before N
                continue

            if m1 > n2:
                # M after N
                continue

            if n1 == m1 and n2 == m2:
                N['submatch'] = IS_DUPLICATE
                break

            if n1 <= m1 < m2 <= n2:
                M['submatch'] = IS_SUBMATCH
                # Determined state of M.
                # break this internal loop
                break

            if m1 <= n1 < n2 <= m2:
                N['submatch'] = IS_SUBMATCH
                # Determined state of N,
                # But possibly more N contained within M. Do not break yet.
                break
    return


def resource_for(resource_name):
    """

    :param resource_name: name of a file in your resource path; Default: opensextant/resource/NAME
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
        if self.pattern_id and "-" in self.pattern_id:
            self.variant_id = self.pattern_id.split("-", 1)[1]

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


class PatternTestCase:
    def __init__(self, tid, family, text):
        self.id = tid
        self.family = family
        self.text = text
        self.true_positive = True


# from abc import ABC, abstractmethod
class RegexPatternManager:
    def __init__(self, cfg, debug=False, testing=False):
        self.families = set([])
        self.patterns = {}
        self.patterns_file = cfg
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
        for pat in self.patterns:
            pat.enabled = True

    def disable_all(self):
        for pat in self.patterns:
            pat.enabled = False

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

        # PY3:
        with codecs.open(config_fpath, "r", encoding="UTF-8") as fh:

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

    :param match_offsets:  given SRE_Match.regs tuples, align found items with Pattern's groups
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

    def __init__(self, pattern_manager):
        """
        Construct a RegexPatternManager externally, then pass that in here.
        :param pattern_manager: RegexPatternManager
        """
        Extractor.__init__(self)
        self.id = "xpx"
        self.name = "Xponents Pattern Extractor"
        self.pattern_manager = pattern_manager

    def extract(self, text, **kwargs):
        """
        Given some text input, apply all relevant pattern families against the text.
        :param text:
        :param kwargs:
        :return:
        """
        features = kwargs.get("features")
        if not features:
            features = self.pattern_manager.families

        results = []
        for fam in features:
            if fam not in self.pattern_manager.families:
                raise Exception("Uknown Pattern Family " + fam)

            for pat_id in self.pattern_manager.patterns:
                pat = self.pattern_manager.patterns[pat_id]
                if not pat.family == fam:
                    continue

                for m in pat.regex.finditer(text):
                    digested_groups = _digest_sub_groups(m, pat.regex_groups)
                    if pat.match_class:
                        domainObj = pat.match_class(m.group(), m.start(), m.end(),
                                                    pattern_id=pat.id,
                                                    label=pat.family,
                                                    match_groups=digested_groups)
                        domainObj.normalize()
                        results.append(domainObj)
                    else:
                        genericObj = PatternMatch(m.group(), m.start(), m.end(),
                                                  pattern_id=pat.id,
                                                  label=pat.family,
                                                  match_groups=digested_groups)
                        results.append(genericObj)

        # Determine if any matches are redundant.  Mark redundancies as "filtered out".
        reduce_matches(results)
        for r in results:
            if r.is_duplicate or r.is_submatch:
                r.filtered_out = True

        return results

    def default_tests(self):
        """
        Runs the default tests on the provided configuration. Plenty of debug printed to screen.
        But returns the test results as an array, e.g., to write to CSV for review.

        :return: test results array; Each result represents a TEST case run against a RULE
        """
        test_results = []
        for t in self.pattern_manager.test_cases:
            expect_valid_match = "FAIL" not in t.text
            output = self.extract(t.text, features=[t.family])

            # Determine if pattern matched true positive or false positive.
            tp = len(output) > 0 and expect_valid_match
            tn = not tp
            for m in output:
                if not expect_valid_match:
                    if m.filtered_out:
                        tn = True
                        tp = False
                    else:
                        tp = False

            test_results.append({"TEST": t.id,
                                 "TEXT": t.text,
                                 "MATCHES": output,
                                 "PASS": tp or tn})

        return test_results
