
import org.opensextant.examples.*
import org.opensextant.extractors.test.*

static void usage(){
  println "Xponents help          -- this help message."
  println "Xponents <TEST> <ARGS....>"
  println ""
  println "  each TEST has different contextual ARGS for command line usage."
  println "  these are all demonstrational tests."
  println ""
  println "Xponents <TEST> --help | -h  -- displays help on that command, if available."
  println ""
  menu()
  System.exit(-1)
}

static void menu(){  

   println '''Tests available for basic tests or adhoc interaction 
   ======================
      gazetteer  -- Gazetteer queries and experiments.
      xcoord     -- XCoord coordinate extraction tests
      geocoder   -- Geotagging and Geocoding
      geotemp    -- Geotagging and temporal extraction
      poli       -- Pattern-based extraction
      xtemp      -- Temporal extraction tests
      xtext      -- XText demonstration - crawl and convert (files to text) local folders
      web-crawl  -- Content crawl and convert. Advanced XText demo, e.g., feed convert and extraction pipeline.
      social-geo -- Social geo-inferencing on tweets (for now).
      =======================
     '''
}


static void main(String[] args){

  def test 
  def app
  if (args.length < 2) {
    usage()
  }
  test = args[0]

  switch(test) {
      case 'gazetteer':
        app = new XponentsGazetteerQuery()
        //  --lookup "San Francisco, CA, USA"  
        break;
      case 'xcoord':
        app = new TestXCoord()
         
        //-u input
        //-f tests
        //-t input-lines
        break;
      
      case 'geocoder':
        app = new PlaceGeocoderTester()
        // -i input -o output
        break;
      case 'geotemp':
        app = new BasicGeoTemporalProcessing()
        // -i input -o output -f fmt
        break;
      case 'xtext':
        app = new org.opensextant.xtext.XText()
        // -i input -o output -e -h
        break;
      case 'poli':
        app = new TestPoLi()
        // -c cfg -u userinput
        // or 
        // -f 
        break;    
      case 'xtemp':
        app = new TestXTemporal()
        // -f 
        // OR 
        // file
        break;
      case 'web-crawl':
        app = new org.opensextant.examples.WebCrawl()
        // -l $WEBSITE -o $OUTPUT -d
        break;
        
      case 'social-geo':
        app = new SocialDataApp()
        break;
      default:
        usage();
  }
  args_len=args.length-1
  app.main(args[1..args_len] as String[])
}
