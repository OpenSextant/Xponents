/****************************************************************************************
 *  TestDbfInputStream.java
 *
 *  Created: Jun 23, 2009
 *
 *  @author DRAND
 *
 *  (C) Copyright MITRE Corporation 2009
 *
 *  The program is provided "as is" without any warranty express or implied, including
 *  the warranty of non-infringement and the implied warranties of merchantibility and
 *  fitness for a particular purpose.  The Copyright owner will not be liable for any
 *  damages suffered by you as a result of using the Program.  In no event will the
 *  Copyright owner be liable for any special, indirect or consequential damages or
 *  lost profits even if the Copyright owner has been advised of the possibility of
 *  their occurrence.
 *
 ***************************************************************************************/
package org.opensextant.giscore.test.input;

import java.io.File;
import java.io.IOException;

import org.junit.Test;
import org.opensextant.giscore.events.IGISObject;
import org.opensextant.giscore.events.Row;
import org.opensextant.giscore.events.Schema;
import org.opensextant.giscore.input.dbf.DbfInputStream;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class TestDbfInputStream {

// @Test
    public void testDbfInputStream1() throws Exception {
        checkDbf(new File("data/shape/Iraq.dbf"));
    }

// @Test
    public void testDbfInputStreamShort() throws Exception {
        checkDbf(new File("data/shape/MBTA.dbf"));
        /*
        <Schema name='schema_2' id='s_2'>
          <SimpleField name='OBJECTID' type='LONG/10'/>
          <SimpleField name='SOURCE' type='STRING/5'/>
          <SimpleField name='LINE' type='STRING/6'/>
          <SimpleField name='GRADE' type='SHORT/1'/>
          <SimpleField name='SHAPE_LEN' type='DOUBLE/19 scale=11'/>
        </Schema>

        Sample row data:
        OBJECTID (LONG) = '1'
        SOURCE (STRING) = 'DLG'
        LINE (STRING) = 'SILVER'
        GRADE (SHORT) = '3'
        SHAPE_LEN (DOUBLE) = '4575.18028634'
        */
    }

// @Test
    public void testDbfInputStream2() throws Exception {
        checkDbf(new File("data/notyetvalidshape/tl_2008_us_metdiv.dbf"));
    }

// @Test
    public void testDbfInputStream3() throws Exception {
        checkDbf(new File("data/notyetvalidshape/AlleghenyCounty_Floodplain2000.dbf"));
    }

    private void checkDbf(File file) throws IOException {
        if (!file.exists()) {
            throw new IllegalArgumentException("file not found: " + file);
        }
        DbfInputStream dbfs = new DbfInputStream(file, new Object[0]);
        try {
            IGISObject obj = dbfs.read();
            assertNotNull(obj);
            assertTrue(obj instanceof Schema);
            Schema s = (Schema) obj;
            assertNotNull(s.getKeys());
            assertTrue(s.getKeys().size() > 1);
            obj = dbfs.read();
            while (obj != null) {
                assertNotNull(obj);
                assertTrue(obj instanceof Row);
                obj = dbfs.read();
            }
        } finally {
            dbfs.close();
        }
    }

}
