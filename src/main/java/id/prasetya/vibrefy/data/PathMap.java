package id.prasetya.vibrefy.data;

import java.io.File;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.Optional; // For modern null handling and optional results
import java.util.stream.IntStream; // For streaming JSONArray

import org.json.JSONArray;

import id.prasetya.vibrefy.Portal;
import id.prasetya.vibrefy.SessionTracker;
import id.prasetya.vibrefy.beans.MediaListBean;
import id.prasetya.vibrefy.beans.StreamBean;
import id.prasetya.vibrefy.beans.SubtitleBean;
import id.prasetya.vibrefy.tools.cloud.CloudConfig;
import id.prasetya.vibrefy.tools.cloud.CloudProvider;
import jakarta.servlet.http.HttpSession;

public class PathMap
{
  private int pathLength=0;
  private String command=null;
  private String[] subPaths=null;
  private String prePath=null;
  private String oriPath=null;
  private String subPath=null;
  private String realPath=null;
  private HttpSession session=null;
  private String cloudLabel=null;
  private String cloudPath=null;

  public int getLength(){return pathLength;}
  public String getCommand(){return command;}
  public String getLibrary(){return prePath;}
  public String getPrefix(){return oriPath;}
  public String getSuffix(){return subPath;}
  public String[] getSubPaths(){return subPaths;}
  public String getRealPath(){return realPath;}
  public HttpSession getSession(){return session;}

  /** A cloud mount keeps realPath null, so every local-path guard still holds. */
  public boolean isCloud(){return cloudLabel!=null;}
  public String getCloudLabel(){return cloudLabel;}
  public String getCloudPath(){return cloudPath==null?"":cloudPath;}

  public CloudProvider getProvider()
  {
    if (cloudLabel==null)return null;
    return CloudConfig.getProvider(session,cloudLabel);
  }

  /**
   * Resolves this path to a provider item id. Deliberately not done in the constructor:
   * PathMap is built for every request including the bursts of range requests that
   * scrubbing produces, and resolution can hit the network.
   */
  public String getCloudId() throws java.io.IOException
  {
    CloudProvider provider=getProvider();
    if (provider==null)return null;
    return CloudConfig.resolveId(session,provider,getCloudPath());
  }
 
  private static String sanitize(String path)
  {
    if (path==null)return "";
    while (path.contains("../"))path=path.replace("../","");
    while (path.contains("..\\"))path=path.replace("..\\","");
    while (path.endsWith("/..") || path.endsWith("\\.."))path=path.substring(0,path.length()-3);
    if (path.equals(".."))path="";
    return path;
  }

  public PathMap(String path,HttpSession currentSession)
  {
    path=sanitize(path);
    String[] paths=path.split("/");
    this.pathLength=paths.length;
    this.session=currentSession;
    
    if (pathLength<1)return;

    int libIdx=2;
    this.command=paths[1];

    if (StreamBean.CMDStream.equals(this.command) || SubtitleBean.CMDSubtitle.equals(this.command) || SubtitleBean.CMDSubtitleList.equals(this.command) || MediaListBean.CMDMediaList.equals(this.command))
    {
      this.session=Optional.ofNullable(paths.length>2?SessionTracker.getSessionById(paths[2]):null).orElse(currentSession);
      libIdx=3;
    }
    if (pathLength<libIdx)return;


    boolean checkAdmin=Optional.ofNullable(this.session).map(s -> (Boolean)s.getAttribute(Portal.SessionAdmin)).orElse(false);
    if (this.pathLength>libIdx)
    {
      this.subPaths=Arrays.copyOfRange(paths,libIdx+1,paths.length);
      this.subPath=(paths.length==(libIdx+1))?"":String.join(File.separator,this.subPaths);
      this.prePath=paths[libIdx];
      String effectiveOriPath=null;
      if (this.prePath.startsWith(CloudConfig.CLOUDPREFIX))
      {
        // Cloud mounts occupy the library slot behind a '~'. They resolve through the
        // provider instead of the filesystem, so oriPath/realPath stay null.
        this.cloudLabel=this.prePath.substring(CloudConfig.CLOUDPREFIX.length());
        this.cloudPath=(this.subPaths==null)?"":String.join("/",this.subPaths);
      }
      else if (Portal.ROOTFOLDER.equals(this.prePath)&&checkAdmin) effectiveOriPath="";
      else
      {
        String account=Optional.ofNullable(this.session).map(s -> (String)s.getAttribute(Portal.SessionEmail)).orElse(null);
        if (account!=null)
        {
          JSONArray libraries=Portal.getProperties(Portal.PropLibraries);
          if (libraries!=null)
          {
            effectiveOriPath = IntStream.range(0, libraries.length())
                .mapToObj(libraries::getJSONObject)
                .filter(lib -> this.prePath.equals(lib.optString(Portal.LibraryName)))
                .filter(lib -> 
                { 
                    JSONArray accs = lib.optJSONArray(Portal.LibraryAccounts);
                    return accs != null && !accs.isEmpty() && IntStream.range(0, accs.length()).mapToObj(accs::optString).filter(s -> s != null && !s.trim().isEmpty()).map(String::trim).anyMatch(account::equalsIgnoreCase);
                })
                .map(lib -> lib.optString(Portal.LibraryPath))
                .filter(s -> s != null && !s.trim().isEmpty()).findFirst().orElse(null);
          }
        }
      }
      this.oriPath=effectiveOriPath;

      if (this.oriPath!=null && this.subPaths.length>0)
      {
        // A filename can reach the server in a different Unicode normalization form than
        // the one actually stored on disk - e.g. a Japanese dakuten mark arriving
        // precomposed (NFC) while the filesystem holds it decomposed into base
        // character + combining mark (NFD), or the reverse, typically because the file
        // crossed over from a different OS or filesystem than the rest of the library. A
        // byte-exact File lookup then fails to find a file that is plainly there, with no
        // exception anywhere to report it. Resolve each segment against its real
        // directory entry so every consumer of realPath - browsing, thumbnails, cover
        // art, streaming, subtitles - gets a name the filesystem will actually match, and
        // keep the corrected name in subPaths so breadcrumbs and further navigation carry
        // it forward instead of re-triggering this on the next click.
        File cursor=new File(this.oriPath);
        for (int i=0;i<this.subPaths.length;i++)
        {
          this.subPaths[i]=resolveSegment(cursor,this.subPaths[i]);
          cursor=new File(cursor,this.subPaths[i]);
        }
        this.subPath=String.join(File.separator,this.subPaths);
      }

      this.realPath=(this.oriPath!=null)?this.oriPath+File.separator+(this.subPath==null?"":this.subPath):null;
    }
  }

  /**
   * Matches a path segment against its real entry in dir, tolerating a Unicode
   * normalization mismatch between what the request carried and what the filesystem
   * stored. Falls back to the segment as given - including when dir cannot be listed -
   * so a genuinely missing file still reports missing exactly as before.
   */
  private static String resolveSegment(File dir,String segment)
  {
    if (new File(dir,segment).exists())return segment;
    String targetNfc=Normalizer.normalize(segment,Normalizer.Form.NFC);
    File[] children=dir.listFiles();
    if (children==null)return segment;
    for (File child:children)
    {
      if (Normalizer.normalize(child.getName(),Normalizer.Form.NFC).equals(targetNfc))return child.getName();
    }
    return segment;
  }
}
