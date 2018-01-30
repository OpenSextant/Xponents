XText Usage
===========

# Overview #

The three main constructs in XText are:

- **The document conversion.**  This is ALWAYS an interpretation of the real thing. There is no right answer for BIN>TEXT
- **The data converters.**  These are simple classes that take a single input object or stream and return a conversion.
- **The data collectors.** Crawling is also an interpretation of an original data sources.  XText supports simple web, 
  sharepoint, and IMAP mail box crawls. PST mailbox crawl is now experimental.
  

# Structure #
## XText ##
The main program, XText, provides a thin shell around Tika and other Converters (org.opensextant.xtext.iConvert interface).
XText will crawl an input folder or handle a single file.  The converter chosen is determined by the supported file
types (namely by file extension) established at runtime.  The defaults are reasonable -- PDF, RTF, all Office documents, 
HTML content, and JPEG images -- will be converted.  To adapt the list or extend it, see the XText API.

XText demonstrates the listener pattern for the most basic file system crawl:  As content is discovered, it is thrown to 
an optional listener for additional post-processing.  The listener is provided by the caller.  The conversion listener 
helps eliminate the need for XText to internally track what is converted, as that is the caller's job.

## ConversionListener ##

XText applications should define a ConversionListener and implement the handleConversion method.
The unit of currency here is a ConvertedDocument, one for each document found.  Archives and compound 
document collections (web site, mailbox, etc) are not considered original documents -- they are 
containers of documents.  Within reason one might consider large embedded documents (documents 
with embedded content in them) as containers also. The interpretation of Container vs Document is one 
left to the caller.

## ConvertedDocument, Content ##
With that said,  embbedded documents (e.g., powerpoint slide with a spreadsheet and media files), are supported
containers in XText.

ConvertedDocument represents all the metadata about the document, the conversion process, and related children items
that may be derived by the conversion.   Children items usually have some binary or other payload and metadata.
ConvertedDocument will have record all raw children as Content objects, and if so directed, the conversions of
those children will also be recorded on the parent  ConvertedDocument.

Tika and most data conversion tools do not dive deep into embedded content, rather they offer tools for 
developers to traverse and retrieve the content themselves.  And so out of the box, converting embedded
documents may yield very little text content for something that may obviously have text, albeit squirreled 
away in internal formats.

## Containers, Collectors ##

A Collector allows you to "collect()".  Its a simple interface.
The CollectionListener allows you to customize more intricate collection, aka crawling. 
As the duo Collector/CollectionListener imply -- they collect, they do not convert.

DefaultMailCrawl, DefaultSharepointCrawl, and DefaultWebCrawl  are all examples of collectors/listeners.
They demonstrate the mechanics for connecting to more complex data sources while using XText as a means
for the conversion. 

ArchiveNavigator (implements ArchiveUnpacker interface) is really a collector and should be migrated
to that pattern.

TODO: the collection listener "default examples" above implement the conversion and collection listener patterns.
But these need to be re-worked for clarity.  The conversion is handled by an instance of XText and post-processing by a provided listener. 




## ConvertedDocument ##

The unit of currency here. The objective of any XText app is to return ConvertedDocument instances.

ConvertedDocument provides a reflection of the most common Tika fields. 
See the javadoc  for more detail.   

Most common uses here might be:

    XText conv = new XText()
    ..
    doc = conv.convertFile( "/path/to/one/file" )
    
Or  using a listener:

    XText conv = new XText()
    conv.setConversionListener(new MyApp())
    conv.convert("/path/to/folder/input")
    
    ...
    class MyApp implmements ConversionListener{
       public void handleConversion(ConvertedDocument doc){
           //  Here you decide what you want to do with the text content.
           // 
           //if (doc!=null){
           String text = doc.getText()
           //}
        }
       }    
       
       
# Caching #
The original intent of XText was to facilitate and speed up the conversion process.
So, XText can be set up to take an input folder/file and record the conversion 
of all found originals in a "xtext" cache.    If XText "save" = false, then you are not caching. 
To enable caching, 

    XText..getPathManager().enableSaving(true);

The cache could be saved along side the originals, in the very same input folder structure that is found.
OR the cache could be saved in a separate, parallel folder structure.

    input:   /path/to/a/file.doc

 Case 1.  Save in Input:
 
    output:  /path/to/a/xtext/file.doc.txt
    
    "xtext" shadow folders are created at each level to house all *.txt files from the conversion routines.
    
Case 2.  Save in Archive:  A single file.

    (required) archive root = "/other/folder"
    output:  /other/folder/a/file.doc.txt
    
    The resulting path is  (archive root) + (input folder name) + (relative path to file)
    

Case 3.  Save in  Archive: An input folder

    (required) archive root = "/other/folder"     
    input:   /path/to/input-folder
    output:  /other/folder/input-folder
    
    
    Crawl uncovers  /path/to/input-folder/a/b/c/file.doc,  
    which is cached at:
                    /other/folder/input-folder/a/b/c/file.doc.txt
    
    

Caching and interpreting the cache can sometimes be difficult:  
For example: traversing archives inside archives, or archives from email message attachments.
Here you have the original archive or container, then you find the original documents inside the container.
Finally you have the text conversions of the originals.
All of this you might want to save on disk in a structure that reflects the innate original structure.

If you have an 1 email message with 3 attachments in a single RFC822 file, for example, you have 

* a single original
* 4 extracted items
* up to 4 converted items

And so 1 compound document -- the email file -- may yield 9 total objects to cache.

The benefits of caching text conversions needs to be weighed by each user of XText.
Since XText is primarily for conversion, the ConversionListener should be used to retrieve
the conversion of any item found by XText.  To use caching the general pattern is to use the PathManager.

    // The gist...
    //
    XText conv = new XText()
    conv.getPathManager().set....( "some param" )
    conv.getPathManager().enable....( bool )
    // finally, 
    conv.setup()

    // For Example:    

    conv.getPathManager().enableSaving(true);          // Save == cache ON    
    conv.getPathManager().enableSaveWithInput(true);   // Save in input folder in cache folders ".../xtext/"
    conv.getPathManager().enableOverwrite(false);      // reuse cached conversions if possible.
    conv.setup();                                      // Finally, run setup, which double checks settings, folders, etc.
   
    // Alternatively, instead of enableSaveWithInput(true), you want to save in a separate archive,
    //
    // Please be sure not to put your conversion archive under any possible input folders. 
    //  
    conv.getPathManager.setConversionCache("/path/to/conversion/archive/");

   
# Error Handling #

It can be difficult to determine if an error is related to an IO issue or a configuration issue.  Both IOException and ConfigException 
are commonly thrown for conversion and other routines.  You should trap them separately and watch those errors closely.

* IOException -- a failure in the ability of Java and underlying libraries to handled the binary streams.
* ConfigException -- an issue with the XText settings, or your application choices, such as input/output folders.
