package NPFSApp.implementation;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Collections;

import org.omg.CORBA.ORB;

import util.HideHidden;
import util.OpenFilter;
import util.PingComparator;
import util.Versioning;
import NPFSApp.FileServerPOA;


public class LocalFileServer extends FileServerPOA {

    //list of all servers in the same network
    // servers are sorted by closeness
    ArrayList<RemoteFileServer> servers;
    
    File myDirectory;
    File openedFile;
    InputStream openedFileStream;
    
    Versioning versionDB;
    
    private ORB orb;
    
    public LocalFileServer() {
        myDirectory = new File(".");
        versionDB = new Versioning(new File(".versions"));
    }
    
    public void setORB(ORB orb_val) {
        orb = orb_val;
    }
    
    private String[] getMyFiles() {
        File[] files = myDirectory.listFiles(HideHidden.instance);
        String[] names = new String[files.length];
        for (int i = 0; i < files.length; i++) {
            File f = files[i];
            names[i] = f.getName();
        }
        return names;
    }
    
    @Override
    public boolean openFile(String filename) {
        //look through my files first
        if (!hasFile(filename)) {
            copyFile(filename);
        }
        openedFile = new File(filename);
        try {
            openedFileStream = new FileInputStream(openedFile);
            return true;
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            openedFile = null;
            return false;
        }
    }
    
    /**
     * Checks to see if this file server has the file
     * @param filename
     * @return -1 if the file doesn't exist or a version number if it does
     */
    public boolean hasFile(String filename) {
        // TODO check against a version database
        return myDirectory.listFiles(new OpenFilter(filename)).length > 0;
    }
    

    @Override
    public String getIpAddress() {
        return "127.0.0.1";
    }

    @Override
    public boolean testResponse() {
        return true;
    }

    /**
     * Adds this ip to the list of other seen nodes
     * @param ip
     */
    public void addServer(RemoteFileServer server) {
        servers.add(server);
        Collections.sort(servers, new PingComparator());
    }
    
    @Override
    public void closeFile() {
        openedFile = null;
        if (openedFileStream != null) {
            try {
                openedFileStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public String[] getFiles() {
        return null;
    }
    
    /**
     * Copies a file from a remote server to this local instance
     * @param filename
     */
    private void copyFile(String filename) {
        File file = new File(filename);
        try {
            file.createNewFile();
            for (FileServerPOA other : this.servers) {
                if (other.hasFile(filename)) {
                    URL url = new URL(other.getIpAddress() + "/" + filename);
                    URLConnection conn = url.openConnection();
                    try (FileOutputStream output = new FileOutputStream(file);
                         InputStream input = conn.getInputStream()) {
                        byte[] buffer = new byte[64];
                        while (input.read(buffer) != -1) {
                            output.write(buffer);
                        }
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Modifies the open file
     */
    @Override
    public void modifyFile() {
        // TODO prevent writing if another server has a newer version number
        
        // TODO Increment this file's version number
        
    }
}
