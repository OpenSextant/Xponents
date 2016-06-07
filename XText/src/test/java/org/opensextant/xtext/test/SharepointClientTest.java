package org.opensextant.xtext.test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.Collection;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.opensextant.util.FileUtility;
import org.opensextant.xtext.collectors.sharepoint.SPLink;
import org.opensextant.xtext.collectors.sharepoint.SharepointClient;

/* 
 */
public class SharepointClientTest {

    public SharepointClientTest() {

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
        gnu.getopt.Getopt opts = new gnu.getopt.Getopt("SharePointClient", args, "go:f:u:p:d:l:");

        String htmlFile = null;
        String spSite = null;
        boolean getItems = false;
        String u = null;
        String p = null;
        String d = null;
        String o = null;

        try {
            int c;
            while ((c = opts.getopt()) != -1) {
                switch (c) {
                case 'f':
                    htmlFile = opts.getOptarg();
                    break;
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
                case 'g':
                    getItems = true;
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

        try {
            FileUtility.makeDirectory(o);

            SharepointClient sp = new SharepointClient(spSite, o, u, p, d);
            sp.configure();

            Collection<SPLink> items = null;
            URL siteURL = new URL(spSite);
            if (htmlFile != null) {
                // Test a previously downloaded HTML page (HTML from a
                // sharepoint site)
                items = sp.parseContentPage(FileUtility.readFile(htmlFile), siteURL);

            } else {
                // Test a single site page, save contents to an output file.
                HttpResponse resp = sp.getPage(siteURL);
                htmlFile = "dump-sharepoint.htm";
                downloadFile(resp.getEntity(), htmlFile);
                if (getItems) {
                    // FileUtility.readFile(htmlFile);
                    items = sp.parseContentPage(FileUtility.readFile(htmlFile), siteURL);
                }
            }

            if (items == null) {
                // Nothing found or nothing requested.
                System.exit(0);
            }

            for (SPLink url : items) {
                if (!url.hasPath()) {
                    System.out.println("Is regular web site - No Path");
                    continue;
                }

                System.out.println("SharePoint Folder? " + url.getSharepointFolder());

                //
                if (url.isFile()) {
                    System.out.println("Product? " + url);
                    if (getItems) {
                        String savePath = "test" + File.separator + url.getNormalPath();

                        File f = new File(savePath.replaceAll("//", "/"));
                        f.getParentFile().mkdirs();
                        HttpResponse itemPage = sp.getPage(url.getURL());
                        downloadFile(itemPage.getEntity(), f.getAbsolutePath());
                    }
                } else if (url.isSharepointFolder()) {
                    System.out.println("Folder to grab:" + url.getSimplifiedFolderURL());

                } else {
                    System.out.println("Page Ignored... ? " + url);
                }
            }
        } catch (Exception err) {
            err.printStackTrace();
        }
    }
}
