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

import org.apache.log4j.xml.DOMConfigurator;

import javax.xml.parsers.FactoryConfigurationError;
import java.net.URL;

/**
 * Configures Log4J logging. Only supports XML configuration because that configuration mode can be processed without
 * resetting the whole logging environment.
 * <br>
 * This is in a separate class to insulate the caller against classpath errors if the host
 * environment doesn't include Log4J.
 */
public class Log4JUtils {
    public static void reconfigureLogging(URL log4JXMLConfigurationFile) throws FactoryConfigurationError {
        DOMConfigurator.configure(log4JXMLConfigurationFile);
    }
}
