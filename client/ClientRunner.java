import java.util.Arrays;
import java.util.Scanner;

import org.omg.CORBA.ORB;
import org.omg.CosNaming.NamingContextExt;
import org.omg.CosNaming.NamingContextExtHelper;

import util.TerminalColors;
import NPFSApp.FileServer;
import NPFSApp.FileServerHelper;

public class ClientRunner {
    static FileServer fsImpl;

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
            
            try (Scanner input = new Scanner(System.in)) {
                for (String file : fsImpl.getFiles()) {
                    System.out.println(file);
                }
                String filename = "";
                do {
                    System.out.print("\nPlease enter a file name to open: ");
                    filename = input.nextLine();
                } while (!fsImpl.openFile(filename));
            }
            fsImpl.closeFile();
            System.out.println();

        } catch (Exception e) {
            System.out.println("ERROR : " + e);
            e.printStackTrace(System.out);
        }
    }
}
