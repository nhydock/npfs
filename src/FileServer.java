import java.io.DataInputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.Socket;
import java.net.URL;
import java.net.URLConnection;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

public class FileServer extends NPFSApp.FileServerPOA {

    /**
     * Prevents hidden files from appearing in our list
     * @author nhydock
     *
     */
    private static class HideHidden implements FileFilter {

        private static final FileFilter instance = new HideHidden();
        
        @Override
        public boolean accept(File pathname) {
            //ignore hidden files
            return !pathname.isHidden();
        }
        
    }
    
    private static class OpenFilter implements FileFilter {
        
        String name;
        public OpenFilter(String name) {
            this.name = name;
        }
        @Override
        public boolean accept(File pathname) {
            return !pathname.isHidden() && pathname.getName().equals(name);
        }
        
    }
    
    //list of all servers in the same network
    // servers are sorted by closeness
    ArrayList<FileServer> servers;
    String ip;
    
    File myDirectory;
    File openedFile;
    InputStream openedFileStream;
    
    Versioning versionDB;
    
    public FileServer(String ip) {
        this.ip = ip;
        myDirectory = new File(".");
        versionDB = new Versioning(new File(".versions"));
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
        // TODO check against a version sql database
        return myDirectory.listFiles(new OpenFilter(filename)).length > 0;
    }

    /**
     * Modifies the open file
     */
    @Override
    public void modifyFile() {
        // TODO prevent writing if another server has a newer version number
        
        // TODO Increment this file's version number
        
    }
    
    /**
     * Copies a file from a remote server to this local instance
     * @param filename
     */
    private void copyFile(String filename) {
        File file = new File(filename);
        try {
            file.createNewFile();
            for (FileServer other : this.servers) {
                if (other.hasFile(filename)) {
                    URL url = new URL(other.ip + "/" + filename);
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
     * Pings this file server instance from the current host
     * @return
     */
    public long ping() {
        long elapsed;
        
        Socket t;
        try {
            t = new Socket(ip, 7);
            DataInputStream dis = new DataInputStream(t.getInputStream());
            PrintStream ps = new PrintStream(t.getOutputStream());
            long start = System.currentTimeMillis();
            ps.println("ping");
            String str = dis.readLine();
            long end = System.currentTimeMillis();
            if (str.equals("ping")) {
                System.out.println("Server alive");
                elapsed = end - start; 
            } else {
                System.out.println("Could not reach server");
                elapsed = Long.MAX_VALUE;
            }
            t.close();
        } catch (IOException e) {
            e.printStackTrace();
            elapsed = Long.MAX_VALUE;
        }
        
        return elapsed;
    }
    
    /**
     * Adds this ip to the list of other seen nodes
     * @param ip
     */
    public void addServer(String ip) {
        FileServer s = new FileServer(ip);
        servers.add(s);
        Collections.sort(servers, new PingComparator());
    }
    
    private class PingComparator implements Comparator<FileServer>
    {

        @Override
        public int compare(FileServer o1, FileServer o2) {
            return ((Long)o1.ping()).compareTo((Long)o2.ping());
        }
        
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
}
