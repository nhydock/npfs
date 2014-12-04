package util;

import java.util.Comparator;

import NPFSApp.implementation.RemoteFileServer;

public class PingComparator implements Comparator<RemoteFileServer>
{
    /**
     * Pings this file server instance from the current host
     * @return
     */
    public long ping(RemoteFileServer server) {
        long elapsed;
        long start = System.currentTimeMillis();
        server.testResponse();
        long end = System.currentTimeMillis();
        elapsed = end - start;
        return elapsed;
    }
    
    @Override
    public int compare(RemoteFileServer o1, RemoteFileServer o2) {
        Long time1 = new Long(ping(o1));
        Long time2 = new Long(ping(o2));
        
        return time1.compareTo(time2);
    }
}