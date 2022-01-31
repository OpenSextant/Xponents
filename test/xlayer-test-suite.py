# -*- coding: utf-8 -*-
#
import os
import json
from opensextant import Place, PlaceCandidate

def print_intro(test_id, content):
    print(f"{test_id}\t{content[0:100]}")


def print_results(matches):
    """

    :param matches: arr of TextMatch
    """
    for match in matches:
        if isinstance(match, PlaceCandidate):
            geo = match.place
            # Label is place, country, coord, or postal
            print(f"{match.label}\t{match.text}\n\t{geo},\t{geo.feature_class}/{geo.feature_code} confidence:{match.confidence}")
        else:
            # Label is taxon, person, org or nationality
            #  .... or pattern name
            print(f"{match.label}\t{match.text}\n\t{match.attrs}")


def unit_testing():
    # Test placename tests from unit testing.
    testfile = os.path.join(xponents_home, "src", "test", "resources", "data", "placename-tests.txt")
    with open(testfile, "r", encoding="UTF-8") as fh:
        docid = os.path.basename(testfile)
        for line in fh:
            if line.strip().startswith("#"):
                continue
            print_intro(docid, line)
            print_results(client.process(docid, line, features=["geo"]))


def postal_testing():
    testfile = os.path.join(xponents_home, "src", "test", "resources", "data", "postal-addresses.json")
    with open(testfile, "r", encoding="UTF-8") as fh:
        docid = os.path.basename(testfile)
        for line in fh:
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            content = json.loads(line)
            print_results(client.process(content["id"], content["text"], features=["postal", "geo"]))


def random_story_testing():
    testfile = os.path.join(xponents_home, "src", "test", "resources", "data", "randomness.txt")
    with open(testfile, "r", encoding="UTF-8") as fh:
        docid = os.path.basename(testfile)
        content = fh.read()
        print_results(client.process(docid, content, features=["geo", "postal", "dates", "taxons"]))


def arabic_testing():
    # Excerpt from Wikipedia, 2021-12-31:
    # https://ar.wikipedia.org/wiki/%D8%A7%D9%86%D9%81%D8%AC%D8%A7%D8%B1_%D9%85%D8%B1%D9%81%D8%A3_%D8%A8%D9%8A%D8%B1%D9%88%D8%AA_2020
    docid = "wikipedia_beruit_gas_2020_arabic"
    content = """
              إم في روسوس
    Crystal Clear app kdict.png مقالة مفصلة: إم في روسوس

    إم في روسوس راسية على المرفأ (يمين الصورة) يوم 8 أكتوبر 2017.
    في 23 أيلول 2013، أبحرت سفينة بضائع روسية تُدعى «إم في روسوس» (بالإنجليزية: MV Rhosus)‏ وترفع العلم المولدوفي من مرفأ مدينة باطوم الجورجية، متوجهة بالشحنة إلى مدينة بيرا في موزمبيق، وعلى متنها 2750 طنًا من مادة نترات الأمونيوم. ولدى بلوغها مرفأ اسطنبول توقفت ليومين، قبل أن تبحر مجدداً في 3 تشرين الأول،  خلال الرحلة اضطرت إلى التوقّف في ميناء بيروت في 21 تشرين الثاني بسبب مشاكل في محركها. وعند التفتيش الذي قامت به سلطات الرقابة في الميناء، قُرّر أن السفينة تعاني من عيوب كبيرة تعيق تحركها وأنها غير صالحة للإبحار، ومُنِعت إثر ذلك من الإبحار. كان على متن السفينة ثمانية أوكرانيين ومواطن روسي واحد، وبمساعدة القنصل الأوكراني، أفرج عن البحارة الأوكرانيين الخمسة وأعيدوا إلى بلادهم، بينما بقي أربعة من أفراد الطاقم، من بينهم قبطان السفينة على متنها لرعايتها.

    وفي حيثيات توقيف السفينة، ذكر موقع "شيب أريستيد.كوم" (بالإنجليزية: shiparrested.com)‏، وهو شبكة تتعامل مع الدعاوى القانونية في قطاع الشحن، أن مالك السفينة أفلس بعد وقت قصير من ذلك، ثم تخلى عن سفينته، مما دفع دائنين مختلفين للتقدم بدعاوى قانونية ضده، وفقدت الجهة التي كانت تستأجر السفينة اهتمامها بالشحنة،  بينما لم يتمكن بقية طاقم السفينة من النزول منها بسبب قيود الهجرة. ثم حصل الدائنون أيضًا على ثلاثة مذكرات اعتقال ضد رعاة السفينة. وجادل المحامون في إعادة الطاقم إلى بلادهم لأسباب إنسانية. وبسبب الخطر الذي تمثله المواد التي كانت لا تزال على متن السفينة، سمح قاضي الأمور المستعجلة في بيروت للطاقم بالعودة إلى بلادهم بعد أن كانوا قد علقوا على متن السفينة لمدة عام تقريبًا. وفي عام 2014، بأمر من المحكمة، قامت سلطات الميناء بتفريغ السفينة من الشحنة الخطرة ونقلت المواد إلى الشاطئ بسبب المخاطر المتعلقة بإبقاء نترات الأمونيوم على متن السفينة، حيث خزنتها في المستودع رقم 12 في الميناء،  لتظل هناك لأكثر من ست سنوات.

    """
    print_results(client.process(docid, content, lang="ar", features=["geo", "patterns", "taxons"]))

def cjk_testing():
    # Excerpt from https://english.kyodonews.net/news/2021/08/008ad4067ab0-japan-issues-exclusion-orders-to-80-chinese-fishing-ships-in-2021.html
    # Translated by Google Translate to Japanese

    docid = "kyodonews_fishing_2021"
    content = """
        海上保安庁は今年、東シナ海の日本の領海で違法に操業している疑いのある合計80隻の中国漁船に排除命令を出したと、この問題に詳しい筋が木曜日に語った。

    日本は中国に、外国の漁船が尖閣諸島周辺の海域で「許可なく」運航することは許可されておらず、東京が支配しているが北京が主張していると語った。

    2012年9月に撮影されたファイル写真は、東シナ海の尖閣諸島を示しています。 （共同通信）
    中国ではディアオユと呼ばれる無人島は、北京が絶えず日本の領海内またはその近くに船を送り、現状に挑戦することで東京に圧力をかけているため、長い間アジア諸国間の紛争の中心でした。

    東シナ海での中国による漁業停止は月曜日に終了する予定であり、北京が尖閣諸島またはその近くに膨大な数の政府および漁船を送る可能性があるという懸念を引き起こしている。

    2016年8月、中国海警局の船のグループと300隻もの漁船が島の周りに群がりました。東京からの高レベルの抗議が殺到したにもかかわらず、彼らの何人かは繰り返し日本の海域に侵入した。

    沿岸警備隊の除外命令は、日本の領海で違法な操業を行っている、またはそうしようとしている外国の漁船を対象としている、と情報筋は述べた。

    今年の注文はすべて、4月までの4か月間発行されたと情報筋は語った。海上保安庁は、2020年に138隻、2019年に147隻、2018年に76隻、2017年に10隻の中国漁船に警告を発した。

    一方、2月、中国は、海上での違法行為に関与した外国船が命令に従わないと主張する場合、沿岸警備隊が武器を使用できるようにする物議を醸す法律を施行し、日中関係を海上安全保障に関してより脆弱にしました。

    中国の王毅外相は最近、「未知の日本の漁船」が島の海域に入ったと言って、この地域への公式船の派遣を正当化した。
        """
    print_results(client.process(docid, content, lang="ja", features=["geo", "patterns", "taxons"]))


def test_suite():
    """
    Test operation here relies on globals xponents_home and the Xlayer client
    :return:
    """
    unit_testing()
    postal_testing()
    arabic_testing()
    cjk_testing()


if __name__ == "__main__":
    from argparse import ArgumentParser
    from opensextant.xlayer import XlayerClient

    ap = ArgumentParser()
    ap.add_argument("url",
                    help="Try the format http://localhost:8787/xlayer/rest/process ... adjust host/port as needed")
    ap.add_argument("--show-filtered", default=False)
    args = ap.parse_args()

    client = XlayerClient(args.url)
    script_dir = os.path.dirname(__file__)
    xponents_home = os.path.dirname(script_dir)

    test_suite()
