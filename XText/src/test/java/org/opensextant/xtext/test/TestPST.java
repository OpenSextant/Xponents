package org.opensextant.xtext.test;

import org.opensextant.xtext.collectors.mailbox.OutlookPSTCrawler;

public class TestPST {

    public static void main(String[] args) {
        try {
            OutlookPSTCrawler pst = new OutlookPSTCrawler(args[0]);
            pst.collect();
        } catch (Exception err) {
            err.printStackTrace();
        }
    }

}
