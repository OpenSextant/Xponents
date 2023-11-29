import copy
import os

from opensextant.extractors.xtemporal import XTemporal
from opensextant.utility import get_csv_writer, ensure_dirs


def run_test():
    print("Run Default Tests")
    datex = XTemporal(debug=True)
    test_results = datex.default_tests()

    print("Save Test Results")
    libdir = os.path.dirname(os.path.abspath(__file__))
    output = os.path.abspath(os.path.join(libdir, "..", "..", "results", "xtemporal-tests.csv"))
    ensure_dirs(output)
    print("... output file at ", output)

    with open(output, "w", encoding="UTF-8") as fh:
        header = ["TEST", "TEXT", "RESULT", "MATCH_TEXT", "MATCH_ATTRS"]
        csvout = get_csv_writer(fh, header)
        csvout.writeheader()
        for result in test_results:

            baserow = {
                "TEST": result["TEST"],
                "TEXT": result["TEXT"],
                "RESULT": result["PASS"],
                "MATCH_TEXT": "",
                "MATCH_ATTRS": ""
            }
            for m in result["MATCHES"]:
                row = copy.copy(baserow)
                row["MATCH_TEXT"] = m.text
                row["MATCH_ATTRS"] = repr(m.attrs)
                csvout.writerow(row)


if __name__ == "__main__":
    run_test()
