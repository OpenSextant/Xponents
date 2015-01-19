package org.opensextant.xtext.test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import org.apache.http.HttpEntity;
import org.opensextant.util.FileUtility;
import org.opensextant.xtext.ConvertedDocument;
import org.opensextant.xtext.XText;
import org.opensextant.xtext.collectors.CollectionListener;
import org.opensextant.xtext.collectors.sharepoint.DefaultSharepointCrawl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/* 
 */
public class SharepointCrawlTest implements CollectionListener {

    private Logger log = LoggerFactory.getLogger(getClass());

    public SharepointCrawlTest() {

    }

    /**
     * REF: http://stackoverflow.com/questions/10960409/how-do-i-save-a-file-
     * downloaded-with-httpclient-into-a-specific-folder
     */
    public static void downloadFile(HttpEntity entity, String destPath) throws IOException {
        org.apache.commons.io.IOUtils.copy(entity.getContent(), new FileOutputStream(destPath));
    }

    public static void usage() {
        System.out.println("\nUsage\n");
        System.out.println("\nParse HTML file\n");
        System.out.println("\t SharePointClient -f file -g");
        System.out.println("\nDownload Sharepoint Page\n");
        System.out.println("\t SharePointClient -u <user> -p <pass> -d <domain> -l <link>");
    }

    public static void main(String[] args) {
        gnu.getopt.Getopt opts = new gnu.getopt.Getopt("SharePointClient", args, "o:u:p:d:l:");

        String spSite = null;
        String u = null;
        String p = null;
        String d = null;
        String o = null;

        try {
            int c;
            while ((c = opts.getopt()) != -1) {
                switch (c) {
                case 'u':
                    u = opts.getOptarg();
                    break;
                case 'p':
                    p = opts.getOptarg();
                    break;
                case 'd':
                    d = opts.getOptarg();
                    break;
                case 'l':
                    spSite = opts.getOptarg();
                    break;
                case 'o':
                    o = opts.getOptarg();
                    break;

                default:
                    usage();
                    System.exit(-1);

                }
            }
        } catch (Exception execErr) {
            usage();
            execErr.printStackTrace();
            System.exit(-1);
        }

        SharepointCrawlTest me = new SharepointCrawlTest();

        try {
            FileUtility.makeDirectory(o);

            XText conv = new XText();
            conv.enableSaving(true);
            conv.getPathManager().setInputRoot(new File(o));
            //conv.getPathManager().setExtractedChildrenCache(o);
            conv.getPathManager().enableSaveWithInput(true);
            //conv.getPathManager().setConversionCache(o+File.separator + "xtext");                    
            conv.setup();

            DefaultSharepointCrawl sp = new DefaultSharepointCrawl(spSite, o, u, p, d);            
            sp.configure();

            // Set these items
            sp.setConverter(conv);
            sp.setListener(me);

            // Go do it.
            sp.collect();

        } catch (Exception err) {
            err.printStackTrace();
        }
    }

    @Override
    public void collected(ConvertedDocument doc, String fpath) throws IOException {
        log.info("Got doc with {} = {}", doc.id, doc.filepath);
    }

    @Override
    public void collected(File doc) throws IOException {
        log.info("Got file with  {}", doc);
    }

    @Override
    public boolean exists(String oid) {
        log.info("Got object with  {}", oid);
        return false;
    }
}
