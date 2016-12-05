/*
 * This software was produced for the U. S. Government
 * under Basic Contract No. W15P7T-13-C-A802, and is
 * subject to the Rights in Noncommercial Computer Software
 * and Noncommercial Computer Software Documentation
 * Clause 252.227-7014 (FEB 2012)
 *
 * Copyright (C) 2016 The MITRE Corporation.
 * Copyright (C) 2016 OpenSextant.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.opensextant.mapreduce;

import org.apache.hadoop.conf.Configuration;

import java.net.URL;

/**
 * Configures logging for the mappers and reducers. Currently only supports Log4J.
 */
public class LoggingUtilities {
    private static boolean configurationDone = false;

    public synchronized static void configureLogging(Configuration conf) {
        if (configurationDone) {
            return;
        }
        try {
            String log4JUrl = conf.get(XponentsTaggerDemo.LOG4J_SUPPLEMENTARY_CONFIGURATION);
            if (log4JUrl == null) {
                System.err.println("No supplementary Log4J configuration provided.");
            } else {
                System.err.println("Invoking supplementary Log4J configuration url: " + log4JUrl);
                Log4JUtils.reconfigureLogging(new URL(log4JUrl));
            }
            configurationDone = true;
        } catch (Throwable t) {
            System.err.println("Could not reconfigure logging: " + t);
            t.printStackTrace();
        }
    }
}
