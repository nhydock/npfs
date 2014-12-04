package util;

import java.io.File;
import java.io.FileFilter;

public class OpenFilter implements FileFilter {
    
    String name;
    public OpenFilter(String name) {
        this.name = name;
    }
    @Override
    public boolean accept(File pathname) {
        return !pathname.isHidden() && pathname.getName().equals(name);
    }
    
}