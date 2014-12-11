package NPFSApp.implementation;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
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
import util.Versioning;
import NPFSApp.FileServer;
import NPFSApp.FileServerHelper;
import NPFSApp.FileServerPOA;

/**
 * Local file server class that runs on our system
 * 
 * @author nhydock
 *
 */
public class LocalFileServer extends FileServerPOA {

    /**
     * Creates a link to a remote server, necessary for copying files to local
     * instances
     * 
     * @param host
     * @param port
     * @return link to a file server
     * @throws Exception
     */
    public static FileServer getRemoteServer(String host, String port) throws Exception {
        Properties prop = new Properties();
        prop.put("org.omg.CORBA.ORBInitialHost", host);
        prop.put("org.omg.CORBA.ORBInitialPort", "" + (Integer.valueOf(port) + 1));
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

    /**
     * Struct used for keeping track of an open file on a session
     * 
     * @author nhydock
     *
     */
    private class OpenFile {
        /**
         * Path of the file on the file system
         */
        final String filename;
        /**
         * Start of the data range
         */
        final long start;
        /**
         * End of our data buffer range
         */
        final long end;

        /**
         * Version number received once opened
         */
        final int version;

        /**
         * Length of the data that can be written
         */
        public long len;

        /**
         * Create an open file record
         * 
         * @param filename
         *            - file we're opening
         * @param start
         *            - start of our data range
         * @param end
         *            - end of our data range
         * @param version
         *            - version number of the file when we opened it
         */
        OpenFile(String filename, long start, long end, int version) {
            this.filename = filename;
            this.start = start;
            this.end = end;
            this.len = end - start;
            this.version = version;
        }

        /**
         * Overwrites and extends data if more has been written than read out
         * 
         * @param data
         */
        public void write(byte[] data) {
            File oldFile = new File(filename);
            Path old = oldFile.toPath();
            Path out = (new File("~" + filename)).toPath();

            try (InputStream oldStream = Files.newInputStream(old, StandardOpenOption.READ);
                    OutputStream outStream = Files.newOutputStream(out, StandardOpenOption.CREATE)) {

                // read the first chunk from the old file and write it to temp
                byte[] head = new byte[(int) start];

                oldStream.read(head);
                outStream.write(head);

                // insert new data into the file
                outStream.write(data);

                // skip over read out chunk
                oldStream.skip(len);

                // write the tail of the file out
                head = new byte[(int) (oldFile.length() - end)];
                while (oldStream.read(head) != -1) {
                    outStream.write(head);
                }

            } catch (IOException e) {
                e.printStackTrace();
            }

            try {
                Files.copy(out, old, StandardCopyOption.REPLACE_EXISTING);
                out.toFile().delete();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Our id distributor for the session connections
     */
    private static int SessionCounter = 0;

    /**
     * list of all servers in the same network servers are sorted by closeness
     */
    ArrayList<FileServer> servers;

    /**
     * list of open sockets used for transferring files;
     */
    HashMap<Integer, ServerSocket> openSockets;
    /**
     * next open socket used for listing for when servers want to transfer files
     * to each other
     */
    private static int AVAILABLE_SOCKET = 15123;
    /**
     * Set of connected ips for servers
     */
    Set<String> connectedAddresses;

    /**
     * The directory the server is distributing files out of
     */
    File myDirectory;
    /**
     * Our file version database
     */
    Versioning versionDB;

    /**
     * IP of this server
     */
    String ip;

    /**
     * Mapping of files open per connection. Only one file per connection is
     * allowed open at a time
     */
    HashMap<Integer, OpenFile> openFiles;

    /**
     * Creates a new LocalFileServer instance on a port
     * 
     * @param port
     */
    public LocalFileServer(int port) {
        myDirectory = new File(".");
        versionDB = new Versioning(new File(".versions"), myDirectory);
        servers = new ArrayList<FileServer>();
        openSockets = new HashMap<Integer, ServerSocket>();
        connectedAddresses = new HashSet<String>();
        try {
            ip = InetAddress.getLocalHost().getHostName() + ":" + port;
        } catch (UnknownHostException e) {
            ip = "localhost:1050";
        }

        openFiles = new HashMap<Integer, OpenFile>();
    }

    /**
     * Gets the list of all files in the root directory of this server
     */
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

    /**
     * Checks to see if this file server has the file
     * 
     * @param filename
     * @return -1 if the file doesn't exist or a version number if it does
     */
    public boolean hasFile(String filename) {
        return myDirectory.listFiles(new OpenFilter(filename)).length > 0;
    }

    @Override
    public String getIpAddress() {
        return ip;
    }

    /**
     * Used for pinging, we can check the connection/load time of another server
     * by doing a simple test response.
     */
    @Override
    public boolean testResponse() {
        return true;
    }

    /**
     * Adds this file server to the list of other seen nodes. Nodes recursively
     * connect to each other when added.
     * 
     * @param server
     *            - file server to monitor
     */
    @Override
    public void addServer(FileServer server) {
        servers.add(server);
        connectedAddresses.add(server.getIpAddress());
        System.out.println("Connected to remote server: " + server.getIpAddress());
        System.out.println("Remote server is also connected to ");
        boolean addMe = true;
        for (String addr : server.getConnectedServers()) {
            if (addr.equals(this.getIpAddress())) {
                addMe = false;
            } else if (!connectedAddresses.contains(addr)) {
                String host = addr.split(":")[0];
                String port = addr.split(":")[1];
                try {
                    addServer(getRemoteServer(host, port));
                    System.out.println(host);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        System.out.println();
        // Collections.sort(servers, new PingComparator());
        if (addMe) {
            server.addServer(_this());
        }
    }

    /**
     * Get a list of all files across all connected servers
     */
    @Override
    public String[] getAllFiles() {
        // file, ip
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

        return fileList;
    }

    /**
     * Copies a file from a remote server to this local instance. Always copies
     * from whichever server has the newest version and is closest.
     * 
     * @param filename
     * @return true if the file was found and copied. false if the file doesn't
     *         exist anywhere
     */
    private boolean copyFile(String filename) {
        try {
            FileServer newest = null;
            int version = -1;
            for (int i = 0; i < servers.size(); i++) {
                FileServer remote = servers.get(i);
                if (remote.hasFile(filename)) {
                    int v = remote.getVersion(filename);
                    if (v > version) {
                        version = v;
                        newest = remote;
                    }
                }
            }
            if (version == -1) {
                System.out.println("File with name " + filename + " doesn't exist");
                return false;
            }

            System.out.println("copying file " + filename + " at version " + version);
            File file = new File(filename);
            file.createNewFile();

            int port = newest.openSocketFile(filename);
            // copy file over port

            // open the socket
            try (Socket socket = new Socket(newest.getIpAddress().split(":")[0], port);
                    InputStream input = socket.getInputStream();
                    OutputStream output = new FileOutputStream(file);) {
                // read the file size
                byte[] data = new byte[Long.SIZE];
                input.read(data, 0, data.length);
                ByteBuffer buffer = ByteBuffer.allocate(Long.SIZE);
                buffer.put(data);
                buffer.flip();// need flip
                int filesize = (int) buffer.getLong();
                System.out.println("filesize of " + filename + " is " + filesize + " bytes");

                data = new byte[filesize];
                input.read(data, 0, data.length);
                output.write(data);
                output.flush();
            }
            newest.closeSocket(port);

            versionDB.updateFile(filename, version);
            System.out.println("file has been copied");
            return true;

        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Get list of connected servers
     */
    @Override
    public String[] getConnectedServers() {
        String[] addr = new String[connectedAddresses.size()];
        return connectedAddresses.toArray(addr);
    }

    /**
     * Attempts to get a file if it exists on other servers.
     * 
     * @return true if file is loaded from self or copied over false if the file
     *         doesn't exist on self or other file servers
     */
    @Override
    public boolean getFile(String filename) {
        if (!hasFile(filename)) {
            return copyFile(filename);
        } else {
            return true;
        }
    }

    /**
     * Gets the size in bytes of a file
     */
    @Override
    public long getFileSize(String filename) {
        return (new File(filename)).length();
    }

    /**
     * Get a new session ID. Clients should request this upon connection
     */
    @Override
    public int getSessionID() {
        int id = SessionCounter;
        SessionCounter++;
        openFiles.put(id, null);
        return id;
    }

    /**
     * Attempts to save the file on close. If the version of the file opened by
     * a session is out of date, then a warning is thrown.
     */
    @Override
    public boolean closeFile(byte[] data, int sessionID) {
        OpenFile file = openFiles.get(sessionID);

        if (!massCheckVersion(file.filename, file.version)) {
            System.out.println("file was out of date");
            return false;
        }

        ArrayList<FileServer> had = new ArrayList<FileServer>();
        for (FileServer server : servers) {
            // if the server has a file, we should make it copy this one
            if (server.hasFile(file.filename)) {
                server.purgeFile(file.filename);
                had.add(server);
            }
        }

        System.out.println("Saving new version of " + file.filename + " to server.  Version: " + (file.version + 1));
        file.write(data);
        versionDB.updateFile(file.filename, file.version + 1);

        for (FileServer server : had) {
            server.getFile(file.filename);
        }

        return true;
    }

    /**
     * Opens a file and sends the range of data down to a client to modify.
     * Server keeps track of that file being open for that client.
     */
    @Override
    public byte[] openFile(String filename, long start, long end, int sessionID) {
        int version = getVersion(filename);
        OpenFile file = new OpenFile(filename, start, end, version);
        openFiles.put(sessionID, file);

        File f = new File(filename);
        // get byte buffer as string

        try (RandomAccessFile raf = new RandomAccessFile(f, "r"); FileInputStream in = new FileInputStream(f)) {
            raf.seek(start);
            byte[] data = new byte[(int) file.len];
            raf.read(data);
            return data;
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(-1);
        }
        return null;
    }

    /**
     * Checks that the version number matches what's the current value in the
     * server
     */
    @Override
    public boolean checkVersion(String filename, int version) {
        return version == versionDB.getVersion(filename);
    }

    /**
     * Checks the file version of a file across all servers
     * 
     * @param filename
     * @param version
     * @return true if the file version matches what all servers have
     */
    private boolean massCheckVersion(String filename, int version) {
        boolean valid = checkVersion(filename, version);
        for (int i = 0; i < servers.size() && valid; i++) {
            FileServer server = servers.get(i);
            if (server.hasFile(filename)) {
                valid = valid && server.checkVersion(filename, version);
            }
        }
        return valid;
    }

    /**
     * Get the current version number of a file
     */
    @Override
    public int getVersion(String filename) {
        return versionDB.getVersion(filename);
    }

    /**
     * Removes a file from this system
     */
    @Override
    public void purgeFile(String filename) {
        System.out.println("Attempting to delete old version of " + filename);
        File file = new File(filename);
        file.delete();
        versionDB.updateFile(filename, -1);
    }

    /**
     * Opens a socket to copy a file over
     */
    @Override
    public int openSocketFile(final String filename) {
        // transfer a file over a socket
        final int port = AVAILABLE_SOCKET;

        // put file send code in a thread to prevent blocking
        // this way multiple clients can be downloading at the same time
        Thread nonblock = new Thread() {
            public void run() {
                try {
                    ServerSocket serverSocket = new ServerSocket(port);
                    openSockets.put(port, serverSocket);
                    File transferFile = new File(filename);
                    byte[] bytearray = new byte[(int) transferFile.length()];
                    try (Socket socket = serverSocket.accept();
                            InputStream input = Files.newInputStream(transferFile.toPath(), StandardOpenOption.READ)) {
                        System.out.println("Accepted connection : " + socket);

                        // read data into memory
                        input.read(bytearray, 0, bytearray.length);

                        // send file over socket
                        System.out.println("Sending file " + filename + "...");
                        OutputStream os = socket.getOutputStream();

                        // first send filesize over so we know how much to read
                        ByteBuffer buffer = ByteBuffer.allocate(Long.SIZE);
                        buffer.putLong(transferFile.length());
                        os.write(buffer.array());

                        // then write the file data over the socket
                        os.write(bytearray, 0, bytearray.length);
                        os.flush();
                        System.out.println("wrote " + bytearray.length + " bytes to stream.");
                        System.out.println(filename + " File transfer complete");
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    openSockets.remove(port);
                }
            }
        };
        nonblock.start();

        AVAILABLE_SOCKET++;
        if (AVAILABLE_SOCKET > 15200) {
            AVAILABLE_SOCKET = 15123;
        }

        return port;
    }

    /**
     * Closes an open socket when done with file transfering
     */
    @Override
    public void closeSocket(int port) {
        try {
            if (openSockets.containsKey(port)) {
                openSockets.get(port).close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
