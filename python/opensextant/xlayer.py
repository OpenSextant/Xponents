# -*- coding: utf-8 -*-
"""
Created on Mar 14, 2016

history: py3.5+ json is as good or better than simplejson

@author: ubaldino
"""

import requests
import requests.exceptions
import json
from opensextant.Extraction import TextMatch
from opensextant.Data import Place


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
            response = requests.get("{}/stop".format (self.server_control), timeout=timeout)
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
        response = requests.get("{}/ping".format (self.server_control), timeout=timeout)
        if response.status_code != 200:
            return response.raise_for_status()
        return True

    def process(self, docid, text, features=["geo"], timeout=10):
        """
        Process text, extracting some entities

          features = "f,f,f,f"  String of comma-separated features
          options  = "o,o,o,o"  String of comma-separated features

          features are places, coordinates, countries, orgs, persons, patterns. 
          
          feature aliases "geo" can be used to get All Geographic entities (places,coordinates,countries)
          feature "taxons" can get at any Taxon "taxons", "persons", "orgs"
          
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
        :return: array of TextMatch objects or empty array.
        """
        json_request = {'docid': docid, 'text': text}
        if self.default_options:
            json_request['options'] = self.default_options
        if features:
            json_request['features'] = ','.join(features)

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



if __name__ == '__main__':
    import os
    import sys
    from traceback import format_exc
    import argparse

    ap = argparse.ArgumentParser()
    ap.add_argument("--service-url", help="XLayer server URL to /process endpoint")
    ap.add_argument("--docid", help="your doc id")
    ap.add_argument("--inputfile", help="your input")
    ap.add_argument("--lines", action="store_true", help="process your inputfile as one line per call")
    ap.add_argument("--text", help="UTF-8 string to process")
    ap.add_argument("--options", help="your service options to send with each request, e.g., 'lowercase,clean_input,revgeo'", default=None)
    ap.add_argument("--features", help="Feature set e.g., 'geo,patterns,taxons'", default="geo,patterns,taxons")
    ap.add_argument("--debug", default=False, action="store_true")
    args = ap.parse_args()

    xtractor = XlayerClient(args.service_url, options=args.options)
    xtractor.debug = args.debug
    feat = ["geo"]
    if args.features:
        feat = args.features.split(',')
    print("Ping server (timeout=5s)....")
    try:
        xtractor.ping(timeout=5)
    except Exception as err:
        print(str(err))
        sys.exit(1)

    # ======================================
    # Support for arbitrary amounts of text
    #
    if args.text:
        _id = "test doc#1"
        _text = args.text
        result = xtractor.process(_id, _text, features=feat)
        print("==============")
        print("INPUT: from text argument")
        print("Annotations\n============")
        print_results(result)
    # ======================================
    # Support data as one text record per line in a file
    #                
    elif args.lines and args.inputfile:
        print("INPUT: from individual lines from inputfile")
        try:
            with open(args.inputfile, 'r', encoding="UTF-8") as fh:
                lineNum = 0
                for line in fh:
                    lineNum += 1
                    _id = "line{}".format(lineNum)
                    print("=============={}:".format(_id))
                    _text = line.strip()
                    result = xtractor.process(_id, _text, features=feat)
                    print("Annotations\n============")
                    print_results(result)
        except Exception as err:
            print(format_exc(limit=5))

    # ======================================
    # Use a single file as the source text to process
    #                
    elif args.inputfile:
        _id = os.path.basename(args.inputfile)
        if args.docid:
            _id = args.docid
        try:
            with open(args.inputfile, 'r', encoding="UTF-8") as fh:
                _text = fh.read()
                _text = _text.strip()
                result = xtractor.process(_id, _text, features=feat)
                print("==============")
                print("INPUT: from text inputfile")
                print("Annotations\n============")
                print_results(result)
        except Exception as err:
            print(format_exc(limit=5))
