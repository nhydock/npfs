import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Scanner;

import org.omg.CORBA.ORB;
import org.omg.CosNaming.NamingContextExt;
import org.omg.CosNaming.NamingContextExtHelper;

import NPFSApp.FileServer;
import NPFSApp.FileServerHelper;

/**
 * Client Runner
 * @author nhydock
 *
 */
public class ClientRunner {
    /**
     * Reference to the file server
     */
    static FileServer fsImpl;

    /**
     * 
     * @param args
     */
    public static void main(String args[]) {
        try {
            // create and initialize the ORB
            ORB orb = ORB.init(args, null);

            // get the root naming context
            org.omg.CORBA.Object objRef = orb.resolve_initial_references("NameService");
            // Use NamingContextExt instead of NamingContext. This is
            // part of the Interoperable naming Service.
            NamingContextExt ncRef = NamingContextExtHelper.narrow(objRef);

            // resolve the Object Reference in Naming
            String name = "NPFS";
            fsImpl = FileServerHelper.narrow(ncRef.resolve_str(name));

            System.out.println("Obtained a handle on server object: " + fsImpl.getIpAddress());
            System.out.println("Remote server is also connected to ");
            for (String addr : fsImpl.getConnectedServers())
            {
                System.out.println(addr);
            }
            
            boolean run = true;
            try (Scanner input = new Scanner(System.in)) {
            	final int sessionID = fsImpl.getSessionID();
            	
            	while (run) {
	                for (String file : fsImpl.getAllFiles()) {
	                    System.out.println(file);
	                }
	                String filename = "";
	                do {
	                    System.out.print("\nPlease enter a file name to open: ");
	                    filename = input.nextLine();
	                } while (!fsImpl.getFile(filename));
	                
	                long byteLength = fsImpl.getFileSize(filename);
	                
	                long start, end;
	                do {
	                	System.out.println("\nPlease enter a char range between 0 and " + byteLength);
	                	System.out.print("start: ");
	                	start = input.nextLong();
	                	System.out.print("end: ");
	                	end = input.nextLong();
	                	
	                } while (end <= start || start < 0 || start > byteLength || end < 0 || end > byteLength);
	                
	                byte[] data = fsImpl.openFile(filename, start, end, sessionID);
	                System.out.println(data);
	                
	                //work with a temp file version of the data
	                Path tmp = (new File("~"+filename)).toPath();
	                try (OutputStream out = Files.newOutputStream(tmp, StandardOpenOption.CREATE)) {
	                	System.out.println("making tmp: " + tmp.toString());
	                	//string to bytes
	                	out.write(data);
	                }
	                
	                //write read in section to a hidden tmp file
	                //Process proc = Runtime.getRuntime().exec("nano " + tmp.toString());
	                
	                //proc.waitFor();
	                
	                String readIn = new String(data);
	                System.out.println(readIn);
	                
	                //reread the file
	                try(InputStream tmpStream = Files.newInputStream(tmp, StandardOpenOption.READ)) {
	                    File f = tmp.toFile();
	                    int len = (int)f.length();
	                    
	                    byte[] out = new byte[len];
	                    tmpStream.read(out);
	                    
	                    System.out.println("Saving file to server");
	                    fsImpl.closeFile(out, sessionID);
	                }
	                
	                Files.delete(tmp);
	                System.out.println("tmp removed, changes pushed upstream");
	                
            	}
            }
            System.out.println();

        } catch (Exception e) {
            System.out.println("ERROR : " + e);
            e.printStackTrace(System.out);
        }
    }
}
