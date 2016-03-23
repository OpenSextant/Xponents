package org.opensextant.examples;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import org.apache.http.HttpEntity;
import org.opensextant.util.FileUtility;
import org.opensextant.xtext.ConvertedDocument;
import org.opensextant.xtext.XText;
import org.opensextant.xtext.collectors.CollectionListener;
import org.opensextant.xtext.collectors.web.DefaultWebCrawl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static net.htmlparser.jericho.Config.LoggerProvider;

/*  
 */
public class WebCrawl implements CollectionListener {
    
    static {
        LoggerProvider = LoggerProvider.SLF4J;
    }

    private Logger log = LoggerFactory.getLogger(getClass());

    public WebCrawl() {

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
        System.out.println("\t WebCrawl   -l <link>  [ -f <file> ] -o <output> [-d]");
        System.out.println(
                "\t  -l link:  connect to website link and crawl.  -d keeps crawl contained to that page/folder");
        System.out.println(
                "\t  -f file:  parse and crawl the given HTML file, as if you captured it from the given link.");
    }

    public static void main(String[] args) {
        gnu.getopt.Getopt opts = new gnu.getopt.Getopt("WebCrawl", args, "do:l:f:");

        String o = null;
        String webSite = null;
        boolean currentDirOnly = false;
        String inputFile = null;

        try {
            int c;
            while ((c = opts.getopt()) != -1) {
                switch (c) {
                case 'l':
                    webSite = opts.getOptarg();
                    break;

                case 'f':
                    inputFile = opts.getOptarg();
                    break;

                case 'o':
                    o = opts.getOptarg();
                    break;

                case 'd':
                    currentDirOnly = true;
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

        WebCrawl me = new WebCrawl();

        try {
            FileUtility.makeDirectory(o);

            XText conv = new XText();

            /*
             * Following setup for conversion and crawl says this:
             * 
             *  - Save converted data at cacheDir
             *  - The inputDir == cacheDir, so  conversions are "saved with input"
             *  
             *  This is because the crawler below is crawling and downloading HTML pages to the "inputDir"
             *  For simplicity sake, the pages are being converted and cached in that same hierarchical folder.
             *  
             *  Alternatively, crawler can save content to A, convert and cache in B.,  we A != B and B is not inside A, etc.
             *  /path/to/A
             *  /path/to/B
             *  for example.
             */
            File cacheDir = new File(o);
            conv.enableSaving(true);

            // Order of setting is important.  Since cacheDir == input & saving with input, 
            // Then no need to set a separate cacheDir.
            //conv.getPathManager().setConversionCache(cacheDir.getAbsolutePath());
            conv.getPathManager().setInputRoot(cacheDir);
            conv.getPathManager().enableSaveWithInput(true);
            conv.setup();

            DefaultWebCrawl crawl = new DefaultWebCrawl(webSite, o);
            crawl.setAllowCurrentDirOnly(currentDirOnly);

            String proxyHost = System.getProperty("http.proxyHost");
            if (proxyHost != null) {
                crawl.setProxy(proxyHost + ":80");
            }
            crawl.configure();

            // Set these items
            crawl.setConverter(conv);
            crawl.setListener(me);

            // Go do it.
            if (inputFile != null) {
                File f = new File(inputFile);
                if (f.exists()) {
                    crawl.collect(f); // parse links from file as if the file was pulled from website -l link. 
                } else {
                    System.err.println("File does not exist F=" + inputFile);
                }
            } else {
                crawl.collect();
            }

        } catch (Exception err) {
            err.printStackTrace();
        }
    }

    //@Override
    public void collected(ConvertedDocument doc, String fpath) throws IOException {
        log.info("Got doc with {} = {}", doc.id, doc.filepath);
    }

    //@Override
    public void collected(File doc) throws IOException {
        log.info("Got file with  {}", doc);
    }

    //@Override
    public boolean exists(String oid) {
        log.info("Got object with  {}", oid);
        return false;
    }
}
