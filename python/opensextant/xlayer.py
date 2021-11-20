# -*- coding: utf-8 -*-
"""
Created on Mar 14, 2016

@author: ubaldino
"""

import json

import requests
import requests.exceptions
from opensextant import Place, TextMatch


class XlayerClient:
    def __init__(self, server, options=""):
        """
        @param server: URL for the service.   E.g., http://SERVER/xlayer/rest/process.
        @keyword  options:  STRING. a comma-separated list of options to send with each request.
        There are no default options supported.
        """
        self.server = server
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

    def process(self, docid, text, features=["geo"], timeout=10, preferred_countries=None, preferred_locations=None):
        """
        Process text, extracting some entities

          features = "f,f,f,f"  String of comma-separated features
          options  = "o,o,o,o"  String of comma-separated features

          features are places, coordinates, countries, orgs, persons, patterns, postal. 
          
          feature aliases "geo" can be used to get All Geographic entities (places,coordinates,countries)
          feature "taxons" can get at any Taxon "taxons", "persons", "orgs"
          feature "postal" will tag obvious, qualified postal codes that are paired with a CITY, PROVINCE, or COUNTRY tag.
          
          options are not observed by Xlayer "Xgeo", but you can adapt your own service
          to accomodate such options.   Possible options are clean_input, lowercase, for example:

          * clean_input scrubs the input text if it has HTML or other content in it.
          * lowercase allows the tagging to pass on through lower case matches.
          
          but interpretation of "clean text" and "lower case" support is subjective.
          so they are not supported out of the box here.
        :param docid: identifier of transaction
        :param text: Unicode text to process
        :param features: LIST of geo OR [places, coordinates, countries], orgs, persons, patterns, taxons
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
                tm = TextMatch(annot.get('matchtext'), annot.get('offset'), None)
                tm.populate(annot)
                annots.append(tm)
                if self.debug:
                    print("Match '{}' at char offset {}".format(tm.text, tm.start))
                    if 'lat' in tm.attrs:
                        geo = Place(None, tm.text, lat=tm.attrs.get('lat'), lon=tm.attrs.get('lon'))
                        print("\trepresenting location: {}".format(str(geo)))

        return annots


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


def process_text(txt, docid="$DOC-ID$", features=[], preferred_countries=[], preferred_locations=[]):
    result = xtractor.process(docid, txt, features=features,
                              timeout=90,
                              preferred_countries=preferred_countries,
                              preferred_locations=preferred_locations)
    print(f"=========DOCID {docid}")
    print("Matches\n============")
    for match in result:
        print_match(match)


if __name__ == '__main__':
    import os
    import sys
    from traceback import format_exc
    import argparse

    ap = argparse.ArgumentParser()
    ap.add_argument("input", help="your input")
    ap.add_argument("--service-url", help="XLayer server host:port", default="localhost:5757")
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
    if not args.service_url.startswith("http"):
        service_url = f"http://{args.service_url}/xlayer/rest/process"

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

    # ======================================
    # Support for arbitrary amounts of text
    #
    if args.text:
        process_text(args.input, docid="test-doc-#123", features=feat,
                     preferred_countries=countries, preferred_locations=locations)
    # ======================================
    # Support data as one text record per line in a file
    #                
    elif args.lines or args.input.endswith(".json"):
        print("INPUT: from individual lines from input file")
        is_json = args.input.endswith(".json")
        try:
            with open(args.input, 'r', encoding="UTF-8") as fh:
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
                    process_text(textbuf, docid=test_id, features=feat,
                                 preferred_countries=countries, preferred_locations=locations)

        except Exception as runErr:
            print(format_exc(limit=5))

    # ======================================
    # Use a single file as the source text to process
    #                
    elif args.input:
        file_id = os.path.basename(args.input)
        try:
            with open(args.input, 'r', encoding="UTF-8") as fh:
                process_text(fh.read(), docid=file_id, features=feat,
                             preferred_countries=countries, preferred_locations=locations)
        except Exception as runErr:
            print(format_exc(limit=5))
