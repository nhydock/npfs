package NPFSApp;

/**
* NPFSApp/FileServerHolder.java .
* Generated by the IDL-to-Java compiler (portable), version "3.2"
* from npfs.idl
* Friday, December 5, 2014 3:46:40 PM EST
*/

public final class FileServerHolder implements org.omg.CORBA.portable.Streamable
{
  public NPFSApp.FileServer value = null;

  public FileServerHolder ()
  {
  }

  public FileServerHolder (NPFSApp.FileServer initialValue)
  {
    value = initialValue;
  }

  public void _read (org.omg.CORBA.portable.InputStream i)
  {
    value = NPFSApp.FileServerHelper.read (i);
  }

  public void _write (org.omg.CORBA.portable.OutputStream o)
  {
    NPFSApp.FileServerHelper.write (o, value);
  }

  public org.omg.CORBA.TypeCode _type ()
  {
    return NPFSApp.FileServerHelper.type ();
  }

}
