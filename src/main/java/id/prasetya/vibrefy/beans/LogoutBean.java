package id.prasetya.vibrefy.beans;

import id.prasetya.vibrefy.Command;

public class LogoutBean extends BeanObject
{
  public static final String COMMAND="logout";
  
  protected void processData()
  {
    session.invalidate();
    account=null;
    command=Command.getCommand("welcome");
  }
}
