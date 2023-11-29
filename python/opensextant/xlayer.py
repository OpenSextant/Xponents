# -*- coding: utf-8 -*-
"""
Created on Mar 14, 2016

@author: ubaldino
"""
import json
import sys

import requests
import requests.exceptions
from opensextant import TextMatch, PlaceCandidate, get_country, make_HASC, \
    is_populated, is_administrative, is_academic, characterize_location, logger_config

# Move away from "geo" and towards a more descriptive place label.
GEOCODINGS = {"geo", "place", "postal", "country", "coord", "coordinate"}


class XlayerClient:
    def __init__(self, server, options=""):
        """
        @param server: URL for the service.   E.g., host:port or 'http://SERVER/xlayer/rest/process'.
        @keyword  options:  STRING. a comma-separated list of options to send with each request.
        There are no default options supported.
        """
        self.server = server
        if not server.startswith("http"):
            self.server = f"http://{server}/xlayer/rest/process"
            self.server_control = f"http://{server}/xlayer/rest/control"
        else:
            # User provided a full URL.
            self.server_control = server.replace('/process', '/control')
        self.debug = False
        self.default_options = options

    def stop(self, timeout=30):
        """
        Timeout of 30 seconds is used here so calls do not hang indefinitely.
        The service URL is inferred:  /process and /control endpoints should be next to each other.
        :return: True if successful or if "Connection aborted" ConnectionError occurs
        """
        try:
            response = requests.get("{}/stop".format(self.server_control), timeout=timeout)
            if response.status_code != 200:
                return response.raise_for_status()
        except requests.exceptions.ConnectionError as err:
            return "Connection aborted" in str(err)
        return False

    def ping(self, timeout=30):
        """
        Timeout of 30 seconds is used here so calls do not hang indefinitely.
        :return: True if successful.
        """
        response = requests.get("{}/ping".format(self.server_control), timeout=timeout)
        if response.status_code != 200:
            return response.raise_for_status()
        return True

    def process(self, docid, text, lang=None, features=["geo"], timeout=10,
                preferred_countries=None, preferred_locations=None):
        """
        Process text, extracting some entities

          lang = "xx" or None, where "xx" is a ISO language 2-char code.
              For general Chinese/Japanese/Korean (CJK) support, use lang = 'cjk'
              Language IDs that have some additional tuning include:
                   "ja", "th", "tr", "id", "ar", "fa", "ur", "ru", "it",
                   "pt", "de", "nl", "es", "en", "tl", "ko", "vi"
          Behavior:  Arabic (ar) or CJK (cjk) lang ID directs tagger to use language-specific tokenizers
              Otherwise other lang ID provided just invokes language-specific stopword filters

          features are places, coordinates, countries, orgs, persons, patterns, postal. 
          
          feature aliases "geo" can be used to get All Geographic entities (places,coordinates,countries)
          feature "taxons" can get at any Taxon "taxons", "persons", "orgs".  As of Xponents 3.6 this reports ALL
              Other taxons available in TaxCat tagger.  "all-taxons" is offered as a means to distinguish old and new behavior.
          feature "postal" will tag obvious, qualified postal codes that are paired with a CITY, PROVINCE, or COUNTRY tag.
          feature "patterns" is an alias for dates and any other pattern-based extractors. For now "dates" is only one
          feature "codes" will tag, use and report coded information for any place; primarily administrative boundaries

          options are not observed by Xlayer "Xgeo", but you can adapt your own service
          to accomodate such options.   Possible options are clean_input, lowercase, for example:

          * clean_input scrubs the input text if it has HTML or other content in it.
          * lowercase allows the tagging to pass on through lower case matches.
          
          but interpretation of "clean text" and "lower case" support is subjective.
          so they are not supported out of the box here.
        :param docid: identifier of transaction
        :param text: Unicode text to process
        :param lang: One of ["ar", "cjk", .... other ISO language IDs]
        :param features: list of geo OR [places, coordinates, countries], orgs, persons, patterns, taxons
        :param timeout: default to 10 seconds; If you think your processing takes longer,
                 adjust if you see exceptions.
        :param preferred_countries: Array of country codes representing those which are preferred fall backs when
            there are ambiguous location names.
        :param preferred_locations:  Array of geohash representing general area of desired preferred matches
        :return: array of TextMatch objects or empty array.
        """
        json_request = {'docid': docid, 'text': text}
        if self.default_options:
            json_request['options'] = self.default_options
        if features:
            json_request['features'] = ','.join(features)
        if preferred_countries:
            json_request['preferred_countries'] = preferred_countries
        if preferred_locations:
            json_request['preferred_locations'] = preferred_locations
        if lang:
            json_request['lang'] = lang

        response = requests.post(self.server, json=json_request, timeout=timeout)
        if response.status_code != 200:
            return response.raise_for_status()

        json_content = response.json()

        if self.debug:
            print(json.dumps(json_content, indent=2))
        if 'response' in json_content:
            # Get the response metadata block
            # metadata = json_content['response']
            pass

        annots = []
        if 'annotations' in json_content:
            aj = json_content['annotations']
            for annot in aj:
                # Desire to move to "label" away from "type"
                label = annot.get("type")
                if label in GEOCODINGS:
                    tm = PlaceCandidate(annot.get('matchtext'), annot.get('offset'), None)
                else:
                    tm = TextMatch(annot.get('matchtext'), annot.get('offset'), None)
                tm.populate(annot)
                annots.append(tm)

        return annots


# ==================
# Geotagger -- Simplified wrapper around Xlayer.  Reduces volume of information
#    EXPERIMENTAL
# ==================


def _increment_count(dct: dict, code: str):
    if not code:
        raise Exception("Data quality issue -- counting on a null value")
    dct[code] = 1 + dct.get(code, 0)


def _infer_slot(all_inf: dict, slot: str, span: PlaceCandidate, match_id=None):
    """
    Insert information into slots.

    :param span:
    :return:
    """
    mid = match_id or span.id
    inf = all_inf.get(mid, {})
    if not inf:
        all_inf[mid] = inf

    if slot in inf:
        return
    if not span:
        raise Exception("Data integrity issue -- inferring location should have a non-null match")
    if slot not in Geotagger.ALLOWED_SLOTS:
        return

    ids = inf.get("match-ids", [])
    if not ids:
        inf["match-ids"] = ids
    ids.append(span.id)

    inf[slot] = {
        # "name": span.place.name,  # Normalized gazetteer name
        "matchtext": span.text  # Mention in text
    }
    if slot != "country":
        inf[slot]["feature"] = span.place.format_feature()


def score_inferences(inf, matches):
    # PASS 2. chose location and fill out chosen metadata.
    # RELATED location information -- use Country Code, ADM1 or the CC.ADM1 province_id to
    # indicate location coding.  This applies to all inferences started.

    for inf_id in inf:
        inference = inf[inf_id]
        if "scores" not in inference:
            continue

        scores = inference["scores"]
        top_score = 0
        top_match = None
        for scored_id in scores:
            score = scores[scored_id]
            if score > top_score:
                top_score = score
                top_match = matches[scored_id]

        feat, res = characterize_location(top_match.place, top_match.label)
        adm1 = top_match.place.adm1
        cc2 = top_match.place.country_code

        # Flesh out the metadata for best location for this mention.
        inference.update({
            "matchtext": top_match.text,
            "confidence": top_match.confidence,
            "resolution": res,
            "feature": feat,
            "lat": top_match.place.lat,
            "lon": top_match.place.lon,
            "province_id": make_HASC(cc2, adm1),
            "cc": cc2,
            "adm1": adm1})

        return inf


class Geotagger:
    """
    GEOTAGGER REST client
    """
    ALLOWED_SLOTS = {"site", "city", "admin", "postal", "country"}

    def __init__(self, cfg: dict, debug=False, features=["geo", "postal", "taxons"]):

        self.debug = debug

        log_lvl = "INFO"
        if debug:
            log_lvl = "DEBUG"
        self.log = logger_config(log_lvl, __name__)

        self.features = features
        url = cfg.get("xponents.url")
        self.xponents = XlayerClient(url)
        self.confidence_min = int(cfg.get("xponents.confidence.min", 10))
        # On Xponents 100 point scale.

        # Test if client is alive
        if not self.xponents.ping():
            raise Exception("Service not available")

    def dbg(self, msg, *args, **kwargs):
        self.log.debug(msg, *args, **kwargs)

    def info(self, msg, *args, **kwargs):
        self.log.info(msg, *args, **kwargs)

    def error(self, msg, *args, **kwargs):
        self.log.error(msg, *args, **kwargs)

    def _location_info(self, spans: list) -> list:
        locs = []
        for t in spans:
            loc_conf = int(t.attrs.get("confidence", -1))
            if isinstance(t, PlaceCandidate):
                if 0 < self.confidence_min <= loc_conf:
                    locs.append(t)
        return locs

    def infer_locations(self, locs: list) -> dict:
        """
        Choose the best location from the list -- Most specific is preferred.
        :param locs: list of locations
        :return:
        """
        # Order of preference:
        # 0. site location
        # 1. postal location w/related info
        # 2. qualified city ~ "City, Province" ... or just "City"
        # 3. Province
        # 4. Country
        #

        # LOGIC:
        #  step 1 - key all locations by match-id, for easy lookup
        #  step 2 - distill compound locations like a postal address to reduce matches to a single "geo inference"
        #       with one best location.
        #  step 3 - organize all location mentions in final `inferences` listing.
        #  step 4 - score inferences, as needed.

        inferences = dict()

        # PASS 1. inventory locations and award points
        matches = {}
        countries = dict()
        rendered_match_ids = dict()

        # Ensure matches are Place Candidates only -- location bearing information.
        for match in locs:
            if isinstance(match, PlaceCandidate):
                matches[match.id] = match

        # Loop through high resolution locations first.
        for mid in matches:
            match = matches[mid]
            loc = match.place  # Place obj
            attrs = match.attrs  # dict
            label = match.label  # entity label
            points = int(0.10 * (match.confidence or 10))

            # POSTAL.  Max points ~ 40 or so, 10 points for each qualifying slot (city, prov, code, etc)
            if label == "postal" and "related" in attrs:
                inferences[mid] = {"match-ids": [mid], "start": match.start, "end": match.end}
                rendered_match_ids[mid] = mid
                points += 10
                related_geo = attrs["related"]
                _increment_count(countries, loc.country_code)
                if related_geo:
                    _infer_slot(inferences, "postal", match)
                    for k in related_geo:
                        # these match IDs indicate the full tuple's geographic connections.
                        points += 10
                        # dereference the postal match.
                        slot = related_geo[k]
                        slot_match = matches.get(slot.get("match-id"))
                        slot_text = related_geo[k]["matchtext"]
                        self.dbg("POSTAL slot %s = %s", k, related_geo[k])
                        if slot_match:
                            _infer_slot(inferences, k, slot_match, match_id=mid)
                            rendered_match_ids[slot_match.id] = mid
                        else:
                            self.info("Xponents BUG: missing match id for postal evidence. %s = %s", k, slot_text)
                inferences[match.id]["scores"] = {mid: points}

        # Iterate over remaining matches.
        for mid in matches:
            match = matches[mid]
            loc = match.place  # Place obj
            attrs = match.attrs  # dict
            label = match.label  # entity label

            if mid not in rendered_match_ids:
                # Ignore all upper case short names... for now. Especially if there is no related geography attached.
                if label == "place" and match.len < 8 and match.text.isupper():
                    self.info("    IGNORE %s", match.text)
                    continue

                if label == "postal":
                    # Such matches should have been associated through some hook above when all postal addresses
                    # gather related mentions.
                    self.dbg("     (BUG) IGNORE Postal %s", match.text)
                    continue

                # Backfill any entries that appear legit, but were not associated with other compound mentions like addresses.
                # Given this is a standalone location, there is no scoring.
                cc2, adm1 = match.place.country_code, match.place.adm1
                feat, res = characterize_location(match.place, match.label)
                inferences[mid] = {
                    "start": match.start, "end": match.end,
                    "matchtext": match.text,
                    "confidence": match.confidence,
                    "resolution": res,
                    "feature": feat,
                    "lat": match.place.lat,
                    "lon": match.place.lon,
                    "province_id": make_HASC(cc2, adm1),
                    "cc": cc2,
                    "adm1": adm1}
            else:
                # Score slots found in compound POSTAL or other matches
                points = int(0.10 * (match.confidence or 10))
                related_mid = rendered_match_ids[mid]

                if label in {"place", "postal"}:
                    _increment_count(countries, loc.country_code)
                    if is_academic(loc.feature_class, loc.feature_code):
                        _infer_slot(inferences, "site", match, match_id=related_mid)
                        points += 20
                    elif is_populated(loc.feature_class):
                        rules = attrs.get("rules", "").lower()
                        qualified = "adminname" in rules or "admincode" in rules
                        _infer_slot(inferences, "city", match, match_id=related_mid)
                        if qualified:
                            points += 20
                        else:
                            # Else location was not qualified fully with district, province, etc..  Just a city name.
                            self.dbg("CITY %s", match.text)
                            points += 10
                    elif is_administrative(loc.feature_class):
                        _infer_slot(inferences, "admin", match, match_id=related_mid)
                        points += 5
                        self.dbg("ADMIN %s", match.text)
                elif label == "country":
                    # No bonus points for country mention.
                    if match.len == 2:
                        self.dbg("IGNORE 2-char mention %s", match.text)
                    else:
                        _infer_slot(inferences, "country", match, match_id=related_mid)
                        _increment_count(countries, loc.country_code)
                        self.dbg("COUNTRY %s", loc)

                if related_mid in inferences:
                    inferences[related_mid]["scores"] = {mid: points}
                else:
                    self.dbg("We missed some feature %s %s %s", label, match.id, match.text)

        score_inferences(inferences, matches)
        for inf_id in inferences:
            inference = inferences[inf_id]
            for k in ["match-ids", "scores"]:
                if k in inference:
                    del inference[k]
        return inferences

    def _mention_info(self, spans: list) -> list:
        men = []
        for t in spans:
            if not isinstance(t, PlaceCandidate) and not t.filtered_out:
                men.append(t)
        return men

    def populate_mentions(self, spans: list) -> dict:
        if not spans:
            return dict()

        def _add_slot(arr, slot_, txt):
            grp = arr.get(slot_, set([]))
            if not grp:
                arr[slot_] = grp
            grp.add(txt)

        men = dict()
        for t in spans:
            # All spans are either taxon, org, or person...; taxon can break out into any flavor of taxonomic term
            catalog = None
            if t.attrs:
                catalog = t.attrs.get("cat") or t.attrs.get("catalog")

            # Handle special cases first, then more general ones.
            if catalog and catalog == "nationality":
                _add_slot(men, "nationality", t.text)
            elif t.label in {"org", "person", "taxon"}:
                _add_slot(men, t.label, t.text)
            else:
                self.info("Mention oddity ...%s", t.label)

        # To allow as valid JSON, we cannot use set().  Convert back to list.
        for slot in men:
            men[slot] = list(men[slot])
        return men

    def _nationality_countries(self, spans):
        countries = set([])
        for t in spans:
            # Its really "catalog".   "cat" may happen in other systems.
            if not (t.attrs and "catalog" in t.attrs):
                continue
            if t.attrs["catalog"] == "nationality":
                taxon = t.attrs.get("name") or t.attrs.get("taxon")  # TODO: more convergence of attribute schemes.
                if taxon and "." in taxon:
                    nat = taxon.split(".")[1]
                    C = get_country(nat)
                    if C:
                        countries.add(C.cc_iso2)
        return countries

    def summarize(self, doc_id, text, lang=None) -> dict:
        """
        Call the XlayerClient process() endpoint,
        distills output tags into `geoinferences` and `mentions` (all other non-geo tags).
        A valid 2-char ISO 639 language code helps to tune

        :param doc_id: ID of text
        :param text: the text input
        :param lang:  language of the text
        :return: A single geoinference
        """
        tags = self.xponents.process(doc_id, text, lang=lang, features=self.features, timeout=15)
        if self.debug:
            self.dbg("TAGS:%d", len(tags))

        output = dict()

        all_locations = self._location_info(tags)
        other_mentions = self._mention_info(tags)
        nationality_cc = self._nationality_countries(tags)
        # TODO -- use nationality in inference to add country info

        # Choose best locations
        output["geoinference"] = self.infer_locations(all_locations)

        # Extra info: This info may be completely unrelated to geography
        output["mentions"] = self.populate_mentions(other_mentions)

        if nationality_cc:
            self.dbg("UNUSED - Nationalities? %s", nationality_cc)

        return output


def print_results(arr):
    """
    :param arr: array of Annotations or TextMatch
    :return:
    """
    for a in arr:
        if isinstance(a, TextMatch):
            if a.filtered_out:
                print("{} Excluded".format(str(a)))
            else:
                print(a)
        else:
            print(a)


def print_match(match: TextMatch):
    """
    :param match:
    :return:
    """
    filtered = ""
    if match.filtered_out:
        filtered = "FILTERED-OUT"
    if match.label == "place":
        cc = match.attrs.get("cc")
        fc = match.attrs.get("feat_class")
        fcode = match.attrs.get("feat_code")
        print(match, f"\t\t\tcountry:{cc}, feature:{fc}/{fcode} {filtered}")
    else:
        print(match, f"\n\tATTRS{match.attrs} {filtered}")


def process_text(extractor, txt, docid="$DOC-ID$", features=[], preferred_countries=[], preferred_locations=[]):
    result = extractor.process(docid, txt, features=features,
                               timeout=90,
                               preferred_countries=preferred_countries,
                               preferred_locations=preferred_locations)
    print(f"=========DOCID {docid}")
    print("TEXT", txt[0:200])
    print("Matches\n============")
    for match in result:
        print_match(match)


def main_demo():
    import os
    from traceback import format_exc
    import argparse

    ap = argparse.ArgumentParser()
    ap.add_argument("input", help="your input")
    ap.add_argument("--service-url", help="XLayer server host:port", default="localhost:8787")
    ap.add_argument("--docid", help="your doc id")
    ap.add_argument("--lines", action="store_true", help="process your inputfile as one line per call")
    ap.add_argument("--text", action="store_true", help="<input> arg is a UTF-8 string to process")
    ap.add_argument("--options",
                    help="your service options to send with each request, e.g., 'lowercase,clean_input,revgeo'",
                    default=None)
    ap.add_argument("--features", help="Feature set e.g., 'geo,patterns,taxons'", default="geo,patterns,taxons")
    ap.add_argument("--countries", help="Countries set e.g., 'AF,US,ID,BR,....", default=None)
    ap.add_argument("--locations", help="Location geohashs set e.g., 'u23,u34,....", default=None)
    ap.add_argument("--debug", default=False, action="store_true")
    args = ap.parse_args()

    service_url = args.service_url
    xtractor = XlayerClient(service_url, options=args.options)
    xtractor.debug = args.debug
    feat = ["geo"]
    countries = None
    locations = None
    if args.features:
        feat = args.features.split(',')
    if args.countries:
        countries = args.countries.split(',')
    if args.locations:
        locations = args.locations.split(',')

    print("Ping server (timeout=5s)....")
    try:
        xtractor.ping(timeout=5)
    except Exception as runErr:
        print(str(runErr))
        sys.exit(1)

    fpath = os.path.abspath(args.input)

    # ======================================
    # Support for arbitrary amounts of text
    #
    if args.text:
        process_text(xtractor, fpath, docid="test-doc-#123", features=feat,
                     preferred_countries=countries, preferred_locations=locations)
    # ======================================
    # Support data as one text record per line in a file
    #
    elif args.lines or args.input.endswith(".json"):
        print("INPUT: from individual lines from input file\n\n")
        is_json = args.input.endswith(".json")
        try:
            with open(fpath, 'r', encoding="UTF-8") as fh:
                lineNum = 0
                for line in fh:
                    textbuf = line.strip()
                    lineNum += 1
                    if is_json:
                        if not textbuf or textbuf.startswith("#"):
                            continue
                        textbuf = json.loads(textbuf).get("text")
                        if not textbuf:
                            print("'text' value required in JSON")
                            continue

                    test_id = "line{}".format(lineNum)
                    process_text(xtractor, textbuf, docid=test_id, features=feat,
                                 preferred_countries=countries, preferred_locations=locations)

        except Exception as runErr:
            print(format_exc(limit=5))

    # ======================================
    # Use a single file as the source text to process
    #
    elif fpath:
        file_id = os.path.basename(fpath)
        try:
            with open(fpath, 'r', encoding="UTF-8") as fh:
                process_text(xtractor, fh.read(), docid=file_id, features=feat,
                             preferred_countries=countries, preferred_locations=locations)
        except Exception as runErr:
            print(format_exc(limit=5))


if __name__ == '__main__':
    main_demo()
