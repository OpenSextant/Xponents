package org.opensextant.annotations.test;

import jodd.json.JsonParser;
import org.junit.Test;
import org.opensextant.annotations.Annotation;
import static org.junit.Assert.assertTrue;

public class TestAnnot {

    @Test
    public void test() {
        Annotation a = new Annotation();
        a.newAttributes();
        a.id = "scratch";
        a.name = "NAMEX";
        a.value = "Salamander X";
        a.offset = 0;
        System.out.println("Annotation as a map\t" + a.getMap());

        a = new Annotation();
        a.newAttributes();
        a.id = "scratch2";
        a.name = "ORG";
        a.value = "Salamander Assoc.";
        a.offset = -90;
        System.out.println("Annotation as a map\t" + a.getMap());

        a = new Annotation();
        a.newAttributes();
        a.id = "scratch2";
        a.rec_id = "Doc2";
        a.source_id = "test-docs";
        a.name = "ORG";
        a.value = "Salamander Assoc.";
        a.offset = 2290;
        a.attrs.put("k1", "v1");
        a.attrs.put("date", "2014-04-89");

        // Test for integers.
        int prec = -999;
        // a.attrs.put("prec", 7777);
        if (a.attrs.containsKey("prec")) {
            prec = a.attrs.getInteger("prec");
        }
        System.out.println("Prec, non-existent = " + prec);
        prec = a.attrs.getInteger("prec", -1);
        System.out.println("Prec, non-existent, default= " + prec);
        a.attrs.put("prec", 7000);
        prec = a.attrs.getInteger("prec");
        System.out.println("Prec, exists as integer =" + prec);
        prec = a.attrs.getInteger("prec", -1);
        System.out.println("Prec, exists as integer, w/default = " + prec);

        System.out.println("Annotation as a map\t" + a.getMap());

        // Test null.
        a.attrs = new JsonParser().parseAsJsonObject("{ }");
        System.out.println("Test Null Attrs\t" + a.getMap());
        // Test null.
        a.attrs = null;
        System.out.println("Test Null Attrs\t" + a.getMap());

        Annotation a1 = new Annotation("entity-testID", "Doc2", "Parser1", "ORG", "SalMonder");
        a1.newAttributes();
        a1.addOffset(80);
        a1.addOffset(4);
        a1.addOffset(2290);
        a1.attrs.put("k1", "v1");
        a1.attrs.put("date", "2014-04-89");

        System.out.println("Entity Annotation with multiple offsets as a map\t" + a1.getMap());

        a1.resetOffsets();
        System.out.println("Entity Annotation with no offsets\t" + a1.getMap());

        // Can we do it twice? check for nulls, etc.
        a1.attrs = null;
        a1.resetOffsets();

        a1.offset = 1001;
        System.out.println("Entity Annotation with one offset\t" + a1.getMap());

        assertTrue(a1.getOffsets().isEmpty());
    }
}
