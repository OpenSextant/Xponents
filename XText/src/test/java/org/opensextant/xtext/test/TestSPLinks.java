package org.opensextant.xtext.test;

import static org.junit.Assert.*;

import java.net.URL;

import org.junit.Test;
import org.opensextant.xtext.collectors.sharepoint.SPLink;

public class TestSPLinks {


    public static void err(String msg) {
        System.err.println(msg);
    }

    public static void print(String msg) {
        System.out.println(msg);
    }

    /**
     * Ensure Hyperlink parsing and hueristics do not interfere too much with Sharepoint link parsing.
     */
    @Test
    public void testSharepointLink(){
        try {
            URL home = new URL("http://share.it.org/sites/MyProject");
            SPLink sl = new SPLink("http://share.it.org/sites/MyProject/Blimpy/Forms/AllItems.aspx?RootFolder=%2Fsites%2FMyProject%2FBlimpy%2FSomeDir&FolderCTID=0x01203411186943761B4C479B9D7101B5DEAAA9&View={36C6YYYYY-C309-4C62-8566-E6995879209F}", home);
            print("SP: "+sl.getAbsoluteURL() + " PATH="+sl.getNormalPath());
        } catch (Exception err){
            err.printStackTrace();
        }
    }

}
