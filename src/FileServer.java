import java.io.DataInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.Socket;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;


public class FileServer extends NPFSApp.FileServerPOA {

    //list of all servers in the same network
    // servers are sorted by closeness
    ArrayList<FileServer> servers;
    String ip;
    
    public FileServer(String ip) {
        this.ip = ip;
    }
    
    @Override
    public void openFile(String filename) {
        // Look for file through server list
        for (FileServer server : servers) {
            if (server.hasFile(filename)) {
                //copy file to local
                // TODO only grab latest version that is closest
                try {
                    URL connection = new URL(server.ip);
                    File file = new File(filename);
                    file.createNewFile();
                    try (ReadableByteChannel rbc = Channels.newChannel(connection.openStream());
                         FileOutputStream fos = new FileOutputStream(file) ) {
                        fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
                    } catch (IOException e) {
                       e.printStackTrace();
                   }
                    return file;
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        
        return null;
    }
    
    /**
     * Checks to see if this file server has the file
     * @param filename
     * @return -1 if the file doesn't exist or a version number if it does
     */
    public boolean hasFile(String filename) {
        // TODO check against a version sql database
        
        File file = new File(filename);
        return file.exists();
    }

    @Override
    public boolean modifyFile(String filename) {
        // TODO Increment this file's version number
        // TODO prevent writing if another server has a newer version number
        return false;
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
}
