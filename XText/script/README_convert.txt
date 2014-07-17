XText Script
------------
As a user you can run the XText program using the "convert" shell script or BAT script.
Usage is the following, and you may need to write your own version of the script to adapt
command line arguments for ease of use.

Terminology:   

   Conversion   = binary to text or any markup to text conversion.
   Collect, Crawl = inventory and identify binary documents in containers


default usage:
   
       convert.bat   \input-dir\      \output-dir  \originals-dir
       convert.bat   \input-file.xyz  \output-dir  \originals-dir
       
       convert.sh   /input-dir/       /output-dir  /originals-dir
       convert.sh   /input-file.xyz   /output-dir  /originals-dir
       
       (Ant)   ant -f ./script/xtext-test.xml -Dinput=<input> -Doutput=<output> convert
       
See other options below as the "convert" script wraps around the Java app org.opensextant.xtext.XText.
Guidance:
   
- Be sure to quote paths that have special characters.
- Create output folders ahead of time.
- Use Full pathnames as arguments
- Default mode is to separate the caching of conversions & collection from input folder.
   
       
XText tries to keep the collecting/crawling and conversion organized.  
You as a user should organize your own data and review how you will manage the
output.  And so when XText hits archival documents (Zip, PST, TAR, etc) it
crawls them collecting the original, individual items in an crawl output folder.
Above the "\originals-dir" argument denotes this folder.


Example Use:

   mkdir  ./output
   mkdir  ./crawls

   # Just one file
   ./script/convert.sh   "./test/Asia_Fdn_Afghanistan_2009.pdf"   ./output    ./crawls

   # A whole folder
   ./script/convert.sh   ./test  ./output    ./crawls
       

The Arguments to Java program, org.opensextant.xtext.XText, are as follows
       
Input:
=================

  -i  "input dir or file"    Path to input item to convert or crawl


Conversion Output
==================
   -o  "output dir"           Path to output folder. It must exist.
   
    OR
    
   -e                         Conversions of all content will be cached with input

  Original documents that need to be converted will be saved in the ouput dir or 
  embedded in the input directory structure.  "-e" and "-o" are exclusive options.
   "-e" option will override "-o dir".  
  

Collector/Crawler Output
=======================
   -x  ".\export\folder"      Export PST or Zip archive contents to the named folder.
   -c                         Children objects will be stored with input

  "-c" option will override "-x dir".  As "-c" saves binaries with input; -x exports to external dir.
  
  
  
Advance Uses for Archives:
==========================

Customize BAT or SH script accordingly.   For example, To process/convert an PST file try adding
arguments as such:
   
 To dump the original contents of the PST to "PST-crawls", it might look like this:
   
   java .... org.opensextant.xtext.XText  -i ...\MyMail.pst -o C:\work\PST-text\ -x C:\work\PST-crawls\
 
The PST file "MyMail.pst"  will be extracted to "PST-crawls" folder
and the text conversions of any converted items will be saved in "PST-text".
 
Text content that does not require conversion will remain in place in the original input folder or 
in crawl output folders.  Plain text files will not typically be saved in conversion output folders.
 
TAR and Zip archives are similar containers. If you have a single archive, it is certainly easier
to unpack such things yourself. However in cases where you want this unpacking to happen
automatically as XText crawls through many files in a folder, its better to use XText for the 
archive expansion and conversion.
 
In the event you have Zips, TAR, PST, etc. along with all your other Office docs, etc. you might have 
a single "container crawl" folder and a single "xtext conversion" folder.

As you run XText on input folders from various paths  the relative structure of the input folder
will be maintained in such output folders.
 
 
