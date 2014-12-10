import org.omg.CORBA.ORB;
import org.omg.CosNaming.NameComponent;
import org.omg.CosNaming.NamingContextExt;
import org.omg.CosNaming.NamingContextExtHelper;
import org.omg.PortableServer.POA;
import org.omg.PortableServer.POAHelper;

import NPFSApp.implementation.LocalFileServer;

public class Runner {
    
    public static void main(String... args) {
        try {
            // create and initialize the ORB
            final ORB orb = ORB.init(args, null);

            // get reference to rootpoa & activate the POAManager
            POA rootpoa = POAHelper.narrow(orb.resolve_initial_references("RootPOA"));
            rootpoa.the_POAManager().activate();

            // create servant and register it with the ORB
            int port = 1050;
            for (int i = 0; i < args.length; i++) {
                if (args[i].equals("-port")) {
                    port = Integer.parseInt(args[i+1]);
                }
            }
            final LocalFileServer server = new LocalFileServer(port);

            // get object reference from the servant
            org.omg.CORBA.Object ref = rootpoa.servant_to_reference(server);
            NPFSApp.FileServer href = NPFSApp.FileServerHelper.narrow(ref);

            // get the root naming context
            org.omg.CORBA.Object objRef = orb.resolve_initial_references("NameService");
            // Use NamingContextExt which is part of the Interoperable
            // Naming Service (INS) specification.
            NamingContextExt ncRef = NamingContextExtHelper.narrow(objRef);
            
            // bind the Object Reference in Naming
            String name = "NPFS";
            NameComponent path[] = ncRef.to_name(name);
            ncRef.rebind(path, href);

            // build references
            
            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                if (arg.equals("-remote")) {
                    String hosts = args[i+1];
                    for (String addr : hosts.split(",")) {
                        String rhost = addr.split(":")[0];
                        String rport = addr.split(":")[1];
                        System.out.println("connecting to " + rhost + ":" + rport);
                        
                        server.addServer(LocalFileServer.getRemoteServer(rhost, rport));
                    }
                }
            }
            
            System.out.println("NPFileServer ready and waiting ...");
            
            // wait for invocations from clients
            orb.run();
        }

        catch (Exception e) {
            System.err.println("ERROR: " + e);
            e.printStackTrace(System.out);
        }

        System.out.println("NPFileServer Exiting ...");
    }
}
