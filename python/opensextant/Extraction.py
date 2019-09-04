"""

classes and routines that align with Java org.opensextant.data and org.opensextant.extraction

* TextEntity: represents a span of text
* TextMatch: a TextEntity matched by a particular routine.  This is the basis for most all
  extractors and annotators in OpenSetant.
"""
from opensextant import PY3
from opensextant.CommonsUtils import get_bool


class TextEntity:
    """
    A Text span.
    """

    def __init__(self, text, start, end):
        self.text = text
        self.start = start
        self.end = end
        self.len = -1
        self.is_duplicate = False
        self.is_overlap = False
        self.is_submatch = False
        if self._is_valid():
            self.len = self.end - self.start

    def __str__(self):
        return "{}({},{})".format(self.text, self.start, self.end)

    def __unicode__(self):
        if PY3:
            return u"{}({},{})".format(self.text, self.start, self.end)
        else:
            return u"{}({},{})".format(unicode(self.text), self.start, self.end)

    def _is_valid(self):
        return self.start >= 0 and self.end >= 0

    def contains(self, x1):
        """ if this span contains an offset x1
        :param x1:
        """
        if self.start < 0 or self.end < 0:
            return False
        return self.start <= x1.start < x1.end <= self.end

    def exact_match(self, t):
        return t.start == self.start and t.end == self.end and self._is_valid()

    def is_within(self, t):
        """
        if the given annotation, t, contains this
        :param t:
        :return:
        """
        return t.contains(self.start) and t.contains(self.end)

    def is_after(self, t):
        return self.start > t.end

    def is_before(self, t):
        return self.end < t.start

    def overlaps(self, t):
        """
        Determine if t overlaps self.  If Right or Left match, t overlaps if it is longer.
        If t is contained entirely within self, then it is not considered overlap -- it is Contained within.
        :param t:
        :return:
        """
        #    a1     a2
        #  t1     t2        RIGHT skew
        #    a1     a2
        #       t1     t2   LEFT skew
        #
        #   a1  a2
        #   t1      t2  RIGHT match
        # t1    t2      LEFT match
        #   a1  a2
        #       t1   t2  minimal OVERLAP
        skew_right = t.start < self.start <= t.end < self.end
        skew_left = self.start < t.start <= self.end < t.end
        left_match = self.end == t.end
        right_match = self.start == t.start
        if skew_right or skew_left:
            return True
        return (right_match and skew_left) or (left_match and skew_right)


class TextMatch(TextEntity):
    """
    An entity matched by some tagger; it is a text span with lots of metadata.
    """

    def __init__(self, *args, label=None):
        TextEntity.__init__(self, *args)
        self.id = None
        self.label = label
        self.filtered_out = False
        self.attrs = {}

    def __str__(self):
        return "{}/{}({},{})".format(self.label, self.text, self.start, self.end)

    def __unicode__(self):
        if PY3:
            return u"{}/{}({},{})".format(self.label, self.text, self.start, self.end)
        else:
            return u"{}/{}({},{})".format(self.label, unicode(self.text), self.start, self.end)

    def populate(self, attrs):
        self.label = attrs.get("type")
        self.attrs = attrs
        self.filtered_out = get_bool(self.attrs.get("filtered-out"))
        length = self.attrs.get("length")
        if length and self.start >= 0 and not self.end:
            self.end = self.start + length

    def normalize(self):
        """
        Optional, but recommended routine to normalize the matched data.
        That is, parse fields, uppercase, streamline punctuation, etc.
        As well, given such normalization result, this is the opportunity to additionally
        validate the match.
        :return:
        """
        pass


from abc import ABC, abstractmethod


class Extractor(ABC):
    def __init__(self):
        self.id = None

    @abstractmethod
    def extract(self, text, **kwargs):
        """

        :param text: Unicode text input
        :keyword features: an array of features to extract, e.g., "coordinate", "place", "MONEY"
        :return: array of TextMatch
        """
        pass


def render_match(m):
    """

    :param m: TextMatch
    :return: dict
    """
    if not isinstance(m, TextMatch):
        return None
    dct = {
        "type": m.label,
        "text": m.text,
        "offset": m.start,
        "length": m.len,
        "filtered-out": m.filtered_out
    }
    return dct
