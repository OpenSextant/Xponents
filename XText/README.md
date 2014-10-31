XText v2.5 README
=================

    Author: Marc. C. Ubaldino, MITRE Corporation
    Contributors: Tim Allison, David Lutz, MITRE Corporation
    Date: 2013-March
    Updated: 2014-July
    Copyright MITRE Corporation, 2012-2014

#Apache Tika is awesome#
Tika  provides all sorts of solid content conversion and parsing capabilities.  XText wraps around Tika basics but 
also provides a lean design for added other extractors/converters and customizing the output of any converter.   
XText API is intended to be a simple core:  filter and convert documents to textual versions.   It provides a 
number of parameters for how the conversions are managed including where to cache conversions, and file size limits.  
Beyond the conversion of a single item, XText attempts to look at the larger topic of converting compound 
documents (email archive/account, archival formats, web page, embedded documents).

#Supported Document Conversions#
Major file conversions supported include:
* Anything Tika can do. By default the Tika AutoDetectParser is employed.
* Email message archiving (traverse RFC822 MIME message, saving attachments, etc) and traversal, conversion, etc.
* Limited web crawl and archiving crawls
* JPEG EXIF parsing (saves full EXIF header as text; EXIF location & time as metadata)
* Support for Embedded Object extraction

#Major features added beyond Tika#

Conversion caching/archiving: conversions can be maintained close to originals or in parallel structure
Metadata preservation: metadata about original and the conversion process are persisted with conversions
XText adds some typical conventions for integrators who wish to use a document conversion tool rather than the 
bare Tika library.   Such features include:

-document file type filters
-logging and metrics
-input/output options for saving converted documents and related metadata
-lightweight listener design so you can unpack, convert and process all in the same loop
-formalizing the document meta-data practices: that is, what metadata is really important and how do we store it with the converted document


Supported customizations:
*PDF metadata harvesting (from Tika/PDFBox);  Detecting of encrypted PDFs
*Web content scrapping;  Default HTML parser is Tika's, but for web articles, Boilerplate parser is better.
*Decomposing and extracting text from compound documents
*Content is normalized to UTF-8 with unix line endings ('\n') only.
*Java Documentation contains what you need to know for development.

Basic Usage

    Running it: From a release, see

       ./script/README_convert.txt
       ./script/convert.sh or convert.bat script

    //compile:
    
    mvn install
    //run out of box tests if you are using a release of Xponents:

    Run a unit test on a good size PDF doc
    ant -f script/xtest-test.xml  test-default
    // Convert a single file, a folder or a compressed archive or TAR.
    // If you provide -Doutput=/path/to/xyz/  then XText conversions are saved to that path
    ant -Dinputfile=./test/doc.docx  convert 
    ant -Dinputfile=./test/somestuff.zip  convert 
    ant -Dinputfile=./test/somestuff/  convert

    // Ant is not required for these scripts:
    See ./script/convert.sh or convert.bat  
  

#RELEASE NOTES#


##v2.5.1  SUMMER, 2014##
- PDFBox updated
- JavaDoc improvements, looking to Java 8 stringent javadoc checking
- Added Outlook PST support (initial). via java-libpst.  This support is planned for Tika 1.6.
- PathManager construct added to offload complexities of dealing with caching, crawling, collecting.

##v1.4  ST PATRICK's DAY, 2014##

- Added Tika 1.5 as primary conversion tool
- Introduced content collectors: Email, web, Sharepoint
- Added MessageConverter for email traversal, conversion and archiving. 
- Added OLEConverter to support MS object conversion, e.g. Outlook message files (untested)
- Added ImageConverter which saves full EXIF header as text and preserves interesting GPS location and date/time as formal metadata that can be retrieved later.

##v1.0  ST PATRICK's  DAY, 2013##
- initial design
- added Testing archive -- not released;  UBL Letters from SOCOM where released Fall 2012.  They are PDFs and Word docs in English and Arabic.  They offer a good test opportunity.


