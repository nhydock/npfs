package util;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Scanner;


/**
 * Hashmap file keeping track of version numbers
 * of files on a server
 * @author nhydock
 *
 */
public class Versioning {
    HashMap<String, Integer> versions;
    
    File versionFile;
    
    public Versioning(File versionFile, File directory) {
        this.versionFile = versionFile;
        this.versions = new HashMap<String, Integer>();
        if (!this.versionFile.exists()) {
            try {
                this.versionFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
	        // read the file
	        try (BufferedReader reader = Files.newBufferedReader(versionFile.toPath(), Charset.defaultCharset())) {
	        	String line;
	            while ((line = reader.readLine()) != null) {
	            	//System.out.println(line);
	                String[] params = line.split("\\|");
	                String filename = params[0];
	                String ver = params[1];
	                //System.out.println(filename + " " + ver);
	                int version = Integer.parseInt(ver);
	                
	                versions.put(filename, version);
	            }
	        } catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
        }
        
        for (File f : directory.listFiles(HideHidden.instance)) {
        	if (!versions.containsKey(f.getName())) {
        		versions.put(f.getName(), 1);
        	}
        }
        
        update();
    }
    
    public int getVersion(String filename) {
        return versions.get(filename);
    }
    
    /**
     * Updates a file to a new version number
     * @param filename
     * @param version - current version number
     * @return
     */
    public void updateFile(String filename, int version) {
    	if (version == -1) {
    		versions.remove(filename);
    	} else {
    		versions.put(filename, version);
    	}
    	update();
    }
    
    /**
     * Saves the current state of the version table to the versioning file
     */
    public void update() {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(versionFile))) {
            String output = "";
            for (String key : this.versions.keySet()) {
                Integer val = this.versions.get(key);
                output += String.format("%s|%d\n", key, val);
            }
            writer.write(output);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
