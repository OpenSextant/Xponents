package org.opensextant.xlayer.server;

import org.opensextant.processing.Parameters;

public class RequestParameters extends Parameters {
    /**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	public String format = "JSON";
	
    public boolean tag_taxons = false;
    public boolean tag_patterns = false;
    public boolean output_taxons = true;
    public boolean output_patterns = true;

    public RequestParameters() {
        super();
    }
}