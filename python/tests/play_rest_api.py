import json
from argparse import ArgumentParser
from opensextant.xlayer import XlayerClient
from opensextant import TextMatch, PlaceCandidate


def print_match(match:TextMatch):
    """

    :param match:
    :return:
    """
    # if match.label == "place":
    if isinstance(match, PlaceCandidate):
        cc = match.attrs.get("cc")
        fc = match.attrs.get("feat_class")
        fcode = match.attrs.get("feat_code")
        print(match, f"\t\t\tcountry:{cc}, feature:{fc}/{fcode} ")
    else:
        print(match, f"\n\tATTRS{match.attrs}")


def batcher(batchfile):
    # Batch consists of JSON (newline-delimited) lines.
    #    each entry has an "id" and "text"
    with open(batchfile, "r", encoding="UTF-8") as fh:
        for line in fh:
            sample = line.strip()
            if not sample or sample.startswith("#"):
                continue
            test = json.loads(sample)
            print("===============\nTEST:  ", test["id"], test["text"])
            geotags = api.process(test["id"], test["text"], features=["geo", "postal"], timeout=30)
            for geotag in geotags:
                print_match(geotag)


def onefile(one):
    with open(one, "r", encoding="UTF-8") as fh:
        print(f"===============\nFILE:  {one}")
        geotags = api.process(one, fh.read(), features=["geo", "postal"], timeout=30)
        for geotag in geotags:
            print_match(geotag)


def just_text(text):
    print(f"===============\nTEXT:  {text}")
    geotags = api.process("cli-text", text, features=["geo", "postal"], timeout=30)
    for geotag in geotags:
        print_match(geotag)


ap = ArgumentParser()
ap.add_argument("url")
ap.add_argument("file")
ap.add_argument("--text", action="store_true", default=False)
args = ap.parse_args()

api = XlayerClient(args.url)

if args.file.endswith(".json"):
    # argument is a batch JSON file.
    batcher(args.file)
elif args.text:
    # argument is "text"
    just_text(args.file)
else:
    # file path
    onefile(args.file)

