package id.prasetya.vibrefy;

import java.util.HashMap;

import id.prasetya.vibrefy.beans.*;


public class Command
{

  private static final HashMap<String,Command> commands=new HashMap<String,Command>();
  static
  {
    String cmd;
    cmd=BeanObject.CMDWelcome;commands.put(cmd,new Command(cmd,BeanObject.class,0,true,true));
    cmd=AuthOpenIDBean.COMMAND;commands.put(cmd,new Command(cmd,AuthOpenIDBean.class,0,true,true));
    cmd=LogoutBean.COMMAND;commands.put(cmd,new Command(cmd,LogoutBean.class,0,true,true));
    cmd=BeanObject.CMDMenu;commands.put(cmd,new Command(cmd,BeanObject.class,1,true,true));
    cmd=BeanObject.CMDHome;commands.put(cmd,new Command(cmd,BeanObject.class,1,true,true));
    cmd=HomeBean.COMMAND;commands.put(cmd,new Command(cmd,HomeBean.class,1,true,true));
    cmd=HomeBean.CMDPart;commands.put(cmd,new Command(cmd,HomeBean.class,1,true,true));
    cmd=BrowseBean.CMDBrowse;commands.put(cmd,new Command(cmd,BrowseBean.class,1,true,true));
    cmd=BrowseBean.CMDPartWatch;commands.put(cmd,new Command(cmd,BrowseBean.class,1,true,true));
    cmd=BrowseBean.CMDCoverMP4;commands.put(cmd,new Command(cmd,BrowseBean.class,1,true,false));
    cmd=BrowseBean.CMDThumbMP4;commands.put(cmd,new Command(cmd,BrowseBean.class,1,true,false));
    cmd=StreamBean.CMDStream;commands.put(cmd,new Command(cmd,StreamBean.class,0,false,false));
    cmd=ProgressBean.CMDProgress;commands.put(cmd,new Command(cmd,ProgressBean.class,1,true,true));
    cmd=LibraryBean.CMDLibrary;commands.put(cmd,new Command(cmd,LibraryBean.class,1,true,true));
    cmd=SubtitleBean.CMDSubtitle;commands.put(cmd,new Command(cmd,SubtitleBean.class,0,false,false));
    cmd=SubtitleBean.CMDSubtitleList;commands.put(cmd,new Command(cmd,SubtitleBean.class,0,false,false));
    cmd=MediaListBean.CMDMediaList;commands.put(cmd,new Command(cmd,MediaListBean.class,0,false,false));
    cmd=SetupBean.CMDSetup;commands.put(cmd,new Command(cmd,SetupBean.class,0,true,true));
    cmd=CloudBean.CMDCloud;commands.put(cmd,new Command(cmd,CloudBean.class,1,true,true));
    cmd=CloudAuthBean.COMMAND;commands.put(cmd,new Command(cmd,CloudAuthBean.class,1,true,true));
    // Access type 0: the device being paired has no login yet. PairBean checks for an
    // identity itself on every order that needs one.
    cmd=PairBean.CMDPair;commands.put(cmd,new Command(cmd,PairBean.class,0,true,true));
  }

  public static Command getCommand(String name){return commands.getOrDefault(name,commands.get(BeanObject.CMDWelcome));}
  
  private String command;
  private Class<?> bean;
  private int accessType;
  private boolean createSession=true;
  private boolean preventCache=true;
  
  private Command(String command,Class<?> bean,int accessType,boolean createSession,boolean preventCache)
  {
    this.command=command;
    this.bean=bean;
    this.accessType=accessType;
    this.createSession=createSession;
    this.preventCache=preventCache;
  }
  
  public String getCommand(){return command;}
  public Class<?> getBean(){return bean;}
  public int getAccessType(){return accessType;}
  public boolean isCreateSession() {return createSession;}
  public boolean isPreventCache() {return preventCache;}
}
