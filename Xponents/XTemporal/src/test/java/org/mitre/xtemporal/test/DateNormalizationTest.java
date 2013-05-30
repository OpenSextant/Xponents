/**
 *
 *      Copyright 2009-2013 The MITRE Corporation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * **************************************************************************
 *                          NOTICE
 * This software was produced for the U. S. Government under Contract No.
 * W15P7T-12-C-F600, and is subject to the Rights in Noncommercial Computer
 * Software and Noncommercial Computer Software Documentation Clause
 * 252.227-7014 (JUN 1995)
 *
 * (c) 2013 The MITRE Corporation. All Rights Reserved.
 * **************************************************************************
 */
package org.mitre.xtemporal.test;

import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;
import org.mitre.flexpat.TextMatchResultSet;
import org.mitre.xtemporal.DateMatch;
import org.mitre.xtemporal.XTemporal;

/**
 *
 * @author jgibson
 */
public class DateNormalizationTest {
    private static XTemporal timeFinder = null;

    @BeforeClass
    public static void setUpClass() throws Exception {
        timeFinder = new XTemporal(true);
        timeFinder.configure();
    }
    
    /**
     * Note that this may report false negatives if the JVM's default time
     * zone is UTC.
     */
    @Test
    public void ensureTimeZone() {
        final TextMatchResultSet result1 = timeFinder.extract_dates("Oct 07", "dummy");
        assertEquals(1, result1.matches.size());
        final TextMatchResultSet result2 = timeFinder.extract_dates("Oct 2007", "dummy");
        assertEquals(1, result2.matches.size());
        
        DateMatch dt = (DateMatch) result2.matches.get(0);
        long noon = (12*3600*1000);
        assertEquals(1191196800000L+noon, dt.datenorm.getTime());
    }
}
