package org.opensextant.annotations.test;

import org.junit.Test;
import org.opensextant.annotations.Annotation;
import org.opensextant.annotations.AnnotationHelper;
import org.opensextant.data.Place;
import static org.junit.Assert.fail;

public class EntityAnnotTests {

    public static void print(final String x) {
        System.out.println(x);
    }

    @Test
    public void test() {
        AnnotationHelper util = new AnnotationHelper();

        Annotation ea = new Annotation("test-id");
        ea.value = "not null here.";

        // This cache overwrites what is in memory.
        util.cacheAnnotation(ea);
        ea.addOffset(4);
        ea.addOffset(6);
        // Hmmm. Need to convey importance of caching (Annot, Offset) if using the
        // Helper.
        // whereas, just caching (Annot) would not capture or cache the offset.
        ea.addOffset(99);
        // This cache adds the offset to the cached annotation if it was cached;
        // Otherwise creates it anew.
        try {
            util.cacheAnnotation(ea, 99);
        } catch (Exception err) {
            fail("Ba hoo - failed to cache annotation");
        }
        ea.name = "person";
        ea.value = "Mick Jagger";
        util.cacheAnnotation(ea, 99);

        // Normal: once:
        ea.addOffsetAttribute();
        // Test if this is a problem if we call it again.
        ea.addOffsetAttribute();
        ea.addOffsetAttribute();
        print("" + ea.attrs + " offset=" + ea.offset);

        ea = new Annotation("test");
        ea.addOffset(4);
        ea.addOffset(14);

        // Normal: once:
        ea.addOffsetAttribute();
        // Test if this is a problem if we call it again.
        ea.addOffsetAttribute();
        ea.addOffsetAttribute();
        print("" + ea.attrs + " offset=" + ea.offset);
        

        print("Annotation ID:" + AnnotationHelper.getAnnotationId(ea.rec_id, ea.contrib, ea.name, ea.value));
        print("Annotation ID:" + AnnotationHelper.getAnnotationId(ea.rec_id, ea.contrib, ea.name, null));
        
        /*
         * 
         */
        Place geo = new Place();
        AnnotationHelper.createGeocodingAnnotation("tester", "geolocationABC", "ABC City Hall", 12, "test-doc-123",
                geo);

        assert (ea.attrs.containsKey("offsets"));

    }

}
