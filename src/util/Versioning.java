package util;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
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
    
    public Versioning(File versionFile) {
        this.versionFile = versionFile;
        if (!this.versionFile.exists()) {
            try {
                this.versionFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        
        this.versions = new HashMap<String, Integer>();
        // read the file
        try (Scanner reader = new Scanner(new FileInputStream(versionFile))) {
            while (reader.hasNextLine()) {
                String[] params = reader.nextLine().split("|");
                String filename = params[0];
                int version = Integer.parseInt(params[1]);
                
                versions.put(filename, version);
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
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
    public boolean updateFile(String filename, int version) {
        if (getVersion(filename) == version) {
            versions.put(filename, version + 1);
            return true;
        }
        return false;
    }
    
    /**
     * Saves the current state of the version table to the versioning file
     */
    public void update() {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(versionFile))) {
            String output = "";
            for (String key : this.versions.keySet()) {
                Integer val = this.versions.get(key);
                output += String.format("%s %d\n", key, val);
            }
            writer.write(output);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
