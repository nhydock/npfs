package NPFSApp.implementation;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Properties;
import java.util.Set;

import org.omg.CORBA.ORB;
import org.omg.CosNaming.NamingContextExt;
import org.omg.CosNaming.NamingContextExtHelper;
import org.omg.PortableServer.POA;
import org.omg.PortableServer.POAHelper;

import util.HideHidden;
import util.OpenFilter;
import util.PingComparator;
import util.TerminalColors;
import util.Versioning;
import NPFSApp.FileServer;
import NPFSApp.FileServerHelper;
import NPFSApp.FileServerPOA;

public class LocalFileServer extends FileServerPOA {

    //list of all servers in the same network
    // servers are sorted by closeness
    ArrayList<FileServer> servers;
    Set<String> connectedAddresses;
    
    File myDirectory;
    File openedFile;
    InputStream openedFileStream;
    
    Versioning versionDB;
    
    String ip;
    
    public LocalFileServer() {
        myDirectory = new File(".");
        versionDB = new Versioning(new File(".versions"));
        servers = new ArrayList<FileServer>();
        connectedAddresses = new HashSet<String>();
        try {
            ip = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            ip = "localhost";
        }
    }
    
    @Override
    public String[] myFiles() {
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
        return ip;
    }

    @Override
    public boolean testResponse() {
        return true;
    }

    /**
     * Adds this ip to the list of other seen nodes
     * @param ip
     */
    @Override
    public void addServer(FileServer server) {
        servers.add(server);
        connectedAddresses.add(server.getIpAddress());
        System.out.println("Connected to remote server: " + server.getIpAddress());
        System.out.println("Remote server is also connected to " + server.getConnectedServers());
        boolean addMe = true;
        for (String addr : server.getConnectedServers()) {
            if (addr.equals(this.getIpAddress())) {
                addMe = false;
            }
            if (!connectedAddresses.contains(addr)) {
                String host = addr.split(":")[0];
                String port = addr.split(":")[1];
                try {
                    addServer(getRemoteServer(host, port));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        //Collections.sort(servers, new PingComparator());
        if (addMe) {
            server.addServer(_this());
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
        //file, ip
        HashMap<String, String> allFiles = new HashMap<String, String>();
        for (FileServer s : servers) {
            for (String file : s.myFiles()) {
                allFiles.put(file, s.getIpAddress());
            }
        }
        for (String file : myFiles()) {
            allFiles.put(file, getIpAddress());
        }
        String[] fileList = new String[allFiles.size()];
        Iterator<String> files = allFiles.keySet().iterator();
        for (int i = 0; files.hasNext(); i++) {
            String file = files.next();
            String host = allFiles.get(file);
            fileList[i] = host + " " + file;
        }
        for (String file : fileList) {
            System.out.println(file);
        }
        
        return fileList;
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

    @Override
    public String[] getConnectedServers() {
        String[] addr = new String[connectedAddresses.size()];
        return connectedAddresses.toArray(addr);
    }
    
    public static FileServer getRemoteServer(String host, String port) throws Exception {
        Properties prop = new Properties();
        prop.put("org.omg.CORBA.ORBInitialHost", host);
        prop.put("org.omg.CORBA.ORBInitialPort", ""+(Integer.valueOf(port) + 1));
        String[] args = {};
        // create and initialize the ORB
        ORB orb = ORB.init(args, prop);

        // get reference to rootpoa & activate the POAManager
        POA rootpoa = POAHelper.narrow(orb.resolve_initial_references("RootPOA"));
        rootpoa.the_POAManager().activate();

        // get the root naming context
        org.omg.CORBA.Object objRef = orb.resolve_initial_references("NameService");
        // Use NamingContextExt which is part of the Interoperable
        // Naming Service (INS) specification.
        NamingContextExt ncRef = NamingContextExtHelper.narrow(objRef);

        // resolve the Object Reference in Naming
        String name = "NPFS";
        return FileServerHelper.narrow(ncRef.resolve_str(name));
    }
}
