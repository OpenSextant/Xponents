/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.opensextant.xtext;

import java.io.File;
import java.io.IOException;

/**
 *
 * @author ubaldino
 */
public interface ArchiveUnpacker {
        public void unpack(File archive) throws IOException;
}
