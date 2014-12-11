package util;

import java.io.File;
import java.io.FileFilter;

/**
 * File filter that restricts a selection to only a specific file if it's not hidden
 * @author nhydock
 *
 */
public class OpenFilter implements FileFilter {
    
    /**
     * Name of the file to look for
     */
    String name;
    
    /**
     * Creates a new OpenFilter
     * @param name - the file name to look for
     */
    public OpenFilter(String name) {
        this.name = name;
    }
    
    @Override
    public boolean accept(File pathname) {
        return !pathname.isHidden() && pathname.getName().equals(name);
    }
    
}