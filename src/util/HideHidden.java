package util;

import java.io.File;
import java.io.FileFilter;

/**
 * Prevents hidden files from appearing in our list of files
 * @author nhydock
 *
 */
public class HideHidden implements FileFilter {

    /**
     * Shared instance to reference
     */
    public static final FileFilter instance = new HideHidden();
    
    @Override
    public boolean accept(File pathname) {
        //ignore hidden files
        return !pathname.isHidden();
    }
    
}