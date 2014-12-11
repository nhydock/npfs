package util;

import java.util.Comparator;

import NPFSApp.FileServer;

/**
 * Compator used for sorting file servers by their ping/load times
 * @author nhydock
 *
 */
public class PingComparator implements Comparator<FileServer>
{
    /**
     * Pings this file server instance from the current host
     * @param server - file server to compare against
     * @return time it took to call the server
     */
    public long ping(FileServer server) {
        long elapsed;
        long start = System.currentTimeMillis();
        server.testResponse();
        long end = System.currentTimeMillis();
        elapsed = end - start;
        return elapsed;
    }
    
    @Override
    public int compare(FileServer o1, FileServer o2) {
        Long time1 = new Long(ping(o1));
        Long time2 = new Long(ping(o2));
        
        return time1.compareTo(time2);
    }
}