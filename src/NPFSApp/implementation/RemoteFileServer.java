package NPFSApp.implementation;
import org.omg.CORBA.ORB;
import org.omg.CORBA.ORBPackage.InvalidName;
import org.omg.CosNaming.NamingContextExt;
import org.omg.CosNaming.NamingContextExtHelper;
import org.omg.CosNaming.NamingContextPackage.CannotProceed;
import org.omg.CosNaming.NamingContextPackage.NotFound;

import NPFSApp.FileServerHelper;
import NPFSApp.FileServerPOA;


public class RemoteFileServer extends FileServerPOA {

    String ip;
    NPFSApp.FileServer remote;
    
    public RemoteFileServer(String ip) {
        this.ip = ip;
        
        try{
            // create and initialize the ORB
            ORB orb = ORB.init(new String[0], null);
    
            // get the root naming context
            org.omg.CORBA.Object objRef = orb.resolve_initial_references("FileServer");
            
            // Use NamingContextExt instead of NamingContext. This is 
            // part of the Interoperable naming Service.  
            NamingContextExt ncRef = NamingContextExtHelper.narrow(objRef);
     
            // resolve the Object Reference in Naming
            String name = "NPFS";
            remote = FileServerHelper.narrow(ncRef.resolve_str(name));
        } catch (InvalidName | NotFound | CannotProceed | org.omg.CosNaming.NamingContextPackage.InvalidName e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean openFile(String filename) {
        // IGNORE ACTION ON REMOTE SERVER
        return false;
    }

    @Override
    public void closeFile() {
        // IGNORE ACTION ON REMOTE SERVER
    }

    @Override
    public String[] getFiles() {
        return remote.getFiles();
    }

    @Override
    public void modifyFile() {
        // IGNORE ACTION ON REMOTE SERVER
    }

    @Override
    public boolean hasFile(String filename) {
        return remote.hasFile(filename);
    }

    @Override
    public String getIpAddress() {
        return ip;
    }

    @Override
    public boolean testResponse() {
        return true;
    }

}
