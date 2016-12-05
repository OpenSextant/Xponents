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

import java.io.IOException;
import java.util.Arrays;
import java.util.Date;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.io.SequenceFile.CompressionType;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.lib.input.SequenceFileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.SequenceFileOutputFormat;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.opensextant.util.GeonamesUtility;

import gnu.getopt.LongOpt;

public class XponentsTaggerDemo  extends Configured implements Tool {
    public static final String LOG4J_SUPPLEMENTARY_CONFIGURATION = "opensextantLog4jSupplementaryConfiguration";
    
    /**
     * 
     * @param key
     * @param cfg
     * @return
     * @throws IOException
     */
    public static DateRange parseDateRange(String key, Configuration cfg) throws IOException {
        String d = cfg.get(key);
        if (d == null) {
            return null;
        }
        String[] dates = d.split(":");
        DateTimeFormatter DATEFMT = DateTimeFormat.forPattern("yyyyMMdd");
        DateRange r = new DateRange();
        r.startDate = DATEFMT.parseDateTime(dates[0]).getMillis();
        r.endDate = DATEFMT.parseDateTime(dates[1]).getMillis();
        if (r.isValid()) {
            return r;
        }
        throw new IOException("Date Range is incorrect");
    }
    
    /**
     * 
     * @param key
     * @param cfg
     * @return
     * @throws IOException
     */
    public static UTCRange parseUTCRange(String key, Configuration cfg) throws IOException {
        String u = cfg.get(key);
        if (u == null) {
            return null;
        }

        String[] u1u2 = u.split(":");
        UTCRange range = new UTCRange();
        range.startUTC = Integer.parseInt(u1u2[0]);
        range.endUTC = Integer.parseInt(u1u2[1]);
        return range;
    }

    public static class UTCRange {

        public int startUTC = 0;
        public int endUTC = 0;

        public boolean isValid() {
            return startUTC <= endUTC;
        }

        public boolean isWithin(int utc) {
            return (utc >= startUTC && utc <= endUTC);
        }
    }
    
    /*
     * Note with these epochs, 1970-01-01 is 0seconds.  epoch can be negative or positive.
     * Value of 0 generally means not set.
     */
    public static class DateRange {
        public long startDate = 0;
        public long endDate = 0;

        public boolean isValid() {
            return endDate > startDate & startDate != 0;
        }

        public boolean isWithin(long epoch) {
            return epoch >= startDate && epoch <= endDate;
        }

        public String toString() {
            return String.format("%s to %s", new Date(startDate), new Date(endDate));
        }
    }

    public static GeonamesUtility geonames = null;
    
    /**
     * 
     * @throws IOException
     */
    public static void initResources() throws IOException {
        /*
         * load country metadata - codes, names, timezones.
         */
        geonames = new GeonamesUtility();
    }


    /**
     * Returns 0 = SUCCESS, other than 0 is a FAILURE mode.
     * 
     * @throws IOException
     * @throws InterruptedException
     * @throws ClassNotFoundException
     */
    @Override
    public int run(String[] args) throws IOException, ClassNotFoundException, InterruptedException {

        /*
         * Run:
         *    /input/path  /output/path  phase CC  yyyymmdd:yyyymmdd
         *    
         *    phase = geotag | xtax
         */
        LongOpt[] options = {
                new LongOpt("in", LongOpt.REQUIRED_ARGUMENT, null, 'i'),
                new LongOpt("out", LongOpt.REQUIRED_ARGUMENT, null, 'o'),
                new LongOpt("phase", LongOpt.REQUIRED_ARGUMENT, null, 'p'),
                new LongOpt("cc", LongOpt.REQUIRED_ARGUMENT, null, 'c'),
                new LongOpt("date-range", LongOpt.REQUIRED_ARGUMENT, null, 'd'),
                new LongOpt("utc-range", LongOpt.REQUIRED_ARGUMENT, null, 'u'),
                new LongOpt("log4j-extra-config", LongOpt.REQUIRED_ARGUMENT, null, 'L')
        };

        gnu.getopt.Getopt opts = new gnu.getopt.Getopt("socgeo", args, "", options);
        String inPath = null;
        String outPath = null;
        String phase = null;
        String cc = null;
        String dateRange = null;
        String utcRange = null;
        String xponentsArchive = null;
        String log4jExtraConfig = null;

        System.out.println(Arrays.toString(args));
        int c;
        while ((c = opts.getopt()) != -1) {
            switch (c) {

            case 0:
                // 0 = Long opt processed.
                break;

            case 'i':
                inPath = opts.getOptarg();
                break;

            case 'o':
                outPath = opts.getOptarg();
                break;

            case 'p':
                phase = opts.getOptarg();
                break;

            case 'c':
                cc = opts.getOptarg();
                break;

            case 'd':
                dateRange = opts.getOptarg();
                break;

            case 'u':
                utcRange = opts.getOptarg();
                break;

            case 'L':
                log4jExtraConfig = opts.getOptarg();
                break;

            default:
                return -2;

            }
        }

        /* Helper resources -- possibly just replaced by existing SocGeoBase utilities.
         */
        XponentsTaggerDemo.initResources();

        /* Job App Configuration 
         */
        Job job = Job.getInstance(getConf(), "Xponents-Tagging");
        job.setJarByClass(XponentsTaggerDemo.class);


        // REQUIRED:
        if (cc == null || phase == null) {
            System.err.println("Phase and CC must be set");
            return -2;
        }
        job.getConfiguration().set("phase", phase);
        job.getConfiguration().set("country", cc);
        if (dateRange != null) {
            job.getConfiguration().set("date-range", dateRange);
        }
        if (utcRange != null) {
            job.getConfiguration().set("utc-range", utcRange);
        }
        /* Mapper */
        if (phase.equalsIgnoreCase("geotag")) {
            job.setMapperClass(GeoTaggerMapper.class);
        } else if (phase.equalsIgnoreCase("xtax")) {
            job.setMapperClass(KeywordTaggerMapper.class);
        }

        if (log4jExtraConfig != null) {
            job.getConfiguration().set(LOG4J_SUPPLEMENTARY_CONFIGURATION, log4jExtraConfig);
        }

        /* No Reduce step */
        job.setNumReduceTasks(1);

        /* Job Input */
        job.setInputFormatClass(SequenceFileInputFormat.class);

        /* Map Phase ouptut */
        SequenceFileInputFormat.addInputPaths(job, inPath);
        job.setMapOutputKeyClass(NullWritable.class);
        job.setMapOutputValueClass(Text.class);

        /* Job Output */
        job.getConfiguration().setBoolean("mapreduce.map.output.compress", true);
        job.getConfiguration().set(
                "mapreduce.map.output.compress.codec",
                "org.apache.hadoop.io.compress.SnappyCodec");
        job.setOutputFormatClass(SequenceFileOutputFormat.class);

        SequenceFileOutputFormat.setOutputPath(job, new Path(outPath));
        SequenceFileOutputFormat.setCompressOutput(job, true);
        SequenceFileOutputFormat.setOutputCompressionType(job, CompressionType.BLOCK);
        job.getConfiguration().set(
                "mapreduce.output.fileoutputformat.compress.codec",
                "org.apache.hadoop.io.compress.SnappyCodec");

        job.setOutputKeyClass(NullWritable.class);
        job.setOutputValueClass(Text.class);

        return job.waitForCompletion(true) ? 0 : 1;

    }

    /**
     * Print stderr usage.
     */
    public static void usage() {
        System.err.println(
                "XponentsTaggerDemo --in HDFSPATH --out HDFSPATH --phase [ geotag | xtax ] \n"
                        + "              --cc COUNTRYCODE [--date-range yyyymmdd:yyyymmdd] [--utc-range h:h] [--extra-log4j-config file:///path/to/config]"
                        + "\nExample:\n\n"
                        + "XponentsTaggerDemo --in /data/some/thing  --out /user/ubbs/output/some-thing-tagged --phase proploc"
                        + "              --date-range 20150101:20150301  --utc-range -12:-7");

    }

    public static void main(String[] args) throws Exception {
        int result = ToolRunner.run(new Configuration(), new XponentsTaggerDemo(), args);
        if (result == -2) {
            usage();
        }
        System.exit(result);
    }

}
