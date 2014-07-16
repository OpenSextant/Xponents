package org.opensextant.xtext.test;

import java.io.File;

import org.opensextant.xtext.collectors.mailbox.OutlookPSTCrawler;

public class TestPST {

    public static void main(String[] args) {
        try {
            OutlookPSTCrawler pst = new OutlookPSTCrawler(args[0]);
            pst.incrementalMode = true;
            pst.overwriteMode = true;
            pst.setOutputDir(new File("/tmp"));

            pst.configure();
            pst.collect();
        } catch (Exception err) {
            err.printStackTrace();
        }
    }

}
