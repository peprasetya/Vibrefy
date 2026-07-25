package id.prasetya.vibrefy.beans;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javax.imageio.ImageIO;

import org.json.*;

/*
import org.mp4parser.IsoFile;
import org.mp4parser.boxes.apple.*;
import org.mp4parser.boxes.iso14496.part12.*;
*/
import com.coremedia.iso.IsoFile;
import com.coremedia.iso.boxes.*;
import com.coremedia.iso.boxes.apple.AppleItemListBox;
import com.googlecode.mp4parser.boxes.apple.*;

import id.prasetya.vibrefy.Portal;
import id.prasetya.vibrefy.SessionTracker;
import id.prasetya.vibrefy.data.CloudItem;
import id.prasetya.vibrefy.data.CloudLink;
import id.prasetya.vibrefy.data.FileItem;
import id.prasetya.vibrefy.tools.cloud.CloudConfig;
import id.prasetya.vibrefy.tools.cloud.CloudProvider;
import id.prasetya.vibrefy.tools.cloud.HttpTool;
import id.prasetya.vibrefy.tools.cloud.RemoteSource;

public class BrowseBean extends BeanObject implements Comparator<Object>
{
  public static final String CMDBrowse="files";
  public static final String CMDPartWatch="fileswatch";
  public static final String CMDCoverMP4="covermp4";
  public static final String CMDThumbMP4="thumbmp4";
  
  FileItem[] fileItem=null;
  ByteArrayInputStream cover=null;
  String sidecarType="image/jpeg";
  
  /** Media types the browser lists and plays. */
  public static boolean isMedia(String name)
  {
    if (name==null)return false;
    String lower=name.toLowerCase();
    return lower.endsWith(".mp4") || lower.endsWith(".m4v");
  }

  private static String baseName(String name)
  {
    if (name==null)return "";
    int dot=name.lastIndexOf('.');
    return dot>0?name.substring(0,dot):name;
  }

  protected void processData()
  {
    if (path.getLength()<2)return;
    if (path.isCloud())
    {
      processCloud();
      return;
    }
    if (path.getRealPath()!=null)
    {
      File file=new File(path.getRealPath());
      if (file!=null && file.exists())
      {
        if (file.isDirectory())
        {
          Map<String,JSONObject> progressMap=new HashMap<>();
          JSONObject userData=SessionTracker.getSessionData(session);
          JSONArray progress=userData.optJSONArray(SessionTracker.DataProgress);
          if (progress!=null)for (int i=0;i<progress.length();i++)
          {
            JSONObject item=progress.getJSONObject(i);
            progressMap.put(item.optString(SessionTracker.DataProgressFile),item);
            //System.out.println("WATCHED: "+item.optString(SessionTracker.DataProgressFile));
          }

          File[] files=file.listFiles();
          if (files!=null)
          {
            ArrayList<FileItem> items=new ArrayList<>();
            for (File aFile:files)
            {
              String mapPath=path.getLibrary()+aFile.getPath().substring(path.getPrefix().length()).replaceAll(File.separator,"/");
              if (aFile.isDirectory() && !aFile.getName().startsWith("."))
              {
                items.add(new FileItem(aFile.getName(),aFile.getName(),mapPath,FileItem.TYPEDIRECTORY));
              } if (aFile.isFile() && !aFile.getName().startsWith(".") && (aFile.getName().endsWith(".mp4") || aFile.getName().endsWith(".m4v")))
              {
                String lookupPath = CMDBrowse+"/"+mapPath;
                //System.out.println("Checking: "+lookupPath);
                JSONObject progressItem = progressMap.get(lookupPath);
                if (progressItem!=null)
                {
                  //System.out.println("Found Progress on "+mapPath);
                  items.add(new FileItem(aFile.getName(),aFile.getName(),mapPath,FileItem.TYPEFILE,progressItem.optInt(SessionTracker.DataProgressTime),progressItem.optLong(SessionTracker.DataProgressUpdate)));
                } else items.add(new FileItem(aFile.getName(),aFile.getName(),mapPath,FileItem.TYPEFILE));
              }
            }
            fileItem=items.toArray(new FileItem[0]);
          } else
          {
            message="Unable to read directory "+path.getSuffix();
          }
        } else if (file.isFile())
        {
          if (CMDBrowse.equalsIgnoreCase(path.getCommand()))
          {
            
          } else if (CMDThumbMP4.equalsIgnoreCase(path.getCommand()))
          {
            IsoFile ifile=null;

            try
            {
              contentType="image/jpeg";
              // A sidecar image beside the video wins over the embedded cover art.
              byte[] sidecar=findLocalSidecar(file);
              if (sidecar!=null)
              {
                cover=scaleThumb(new ByteArrayInputStream(sidecar));
                return;
              }
              ifile=new IsoFile(path.getRealPath());
              MovieBox moov=ifile.getBoxes(MovieBox.class).get(0);
              if (moov==null)throw new Exception("ISO Parse Exception Stage 1");
              UserDataBox udta=moov.getBoxes(UserDataBox.class).get(0);
              if (udta==null)throw new Exception("ISO Parse Exception Stage 2");
              MetaBox mbox=udta.getBoxes(MetaBox.class).get(0);
              if (mbox==null)throw new Exception("ISO Parse Exception Stage 3");
              AppleItemListBox aplbox=mbox.getBoxes(AppleItemListBox.class).get(0);
              if (aplbox==null)throw new Exception("ISO Parse Exception Stage 4");
              AppleCoverBox covrbox=aplbox.getBoxes(AppleCoverBox.class).get(0);
              if (covrbox==null)throw new Exception("ISO Parse Exception Stage 5");
              cover=new  ByteArrayInputStream(covrbox.getCoverData());
              BufferedImage bufferedThumb = ImageIO.read(cover);
              int currentWidth=bufferedThumb.getWidth();
              int currentHeight=bufferedThumb.getHeight();
              int width=320;
              int height=(width*currentHeight)/currentWidth;
              double scaling=Math.pow((double)width/(double)currentWidth,1f/(currentWidth>9060?3f:2f));
              while ((currentWidth*scaling)>(width) && (currentHeight*scaling)>(height))
              {
                currentWidth*=scaling;
                currentHeight*=scaling;
                BufferedImage temp=new BufferedImage(currentWidth,currentHeight,bufferedThumb.getType());
                Graphics2D gTemp=temp.createGraphics();
                gTemp.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                gTemp.setRenderingHint(RenderingHints.KEY_RENDERING,RenderingHints.VALUE_RENDER_QUALITY);
                gTemp.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                gTemp.drawImage(bufferedThumb,0,0,currentWidth,currentHeight,null);
                gTemp.dispose();
                bufferedThumb = temp;
              }
              if (currentWidth!=width && currentHeight!=height)
              {
                BufferedImage temp = new BufferedImage(width,height,bufferedThumb.getType());
                Graphics2D gTemp = temp.createGraphics();
                gTemp.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                gTemp.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                gTemp.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                gTemp.drawImage(bufferedThumb,0,0,width,height, null);
                gTemp.dispose();
                bufferedThumb = temp;
              }
              ByteArrayOutputStream bos=new ByteArrayOutputStream();
              ImageIO.write(bufferedThumb,"JPG",bos);
              cover.close();
              cover=new ByteArrayInputStream(bos.toByteArray());
            } catch(IndexOutOfBoundsException e)
            {
            } catch(IOException e)
            {
              System.out.println("ERRCOVERIO:"+e.getMessage());
            } catch(Exception e)
            {
              System.out.println("ERRCOVERCMN:"+e.getMessage());
              e.printStackTrace(System.out);
            } finally 
            {
              if (ifile!=null)try
              {
                ifile.close();
              }catch (IOException e)
              {
                e.printStackTrace();
              }
            }
          } else if (CMDCoverMP4.equalsIgnoreCase(path.getCommand()))
          {
            IsoFile ifile=null;
            try
            {
              contentType="image/jpeg";
              byte[] sidecar=findLocalSidecar(file);
              if (sidecar!=null)
              {
                cover=new ByteArrayInputStream(sidecar);
                contentType=sidecarType;
                return;
              }
              ifile=new IsoFile(path.getRealPath());
              MovieBox moov=ifile.getBoxes(MovieBox.class).get(0);
              if (moov==null)throw new Exception("1");
              UserDataBox udta=moov.getBoxes(UserDataBox.class).get(0);
              if (udta==null)throw new Exception("2");
              MetaBox mbox=udta.getBoxes(MetaBox.class).get(0);
              if (mbox==null)throw new Exception("3");
              AppleItemListBox aplbox=mbox.getBoxes(AppleItemListBox.class).get(0);
              if (aplbox==null)throw new Exception("4");
              AppleCoverBox covrbox=aplbox.getBoxes(AppleCoverBox.class).get(0);
              if (covrbox==null)throw new Exception("5");
              cover=new  ByteArrayInputStream(covrbox.getCoverData());
              if (covrbox.getDataType()==13) contentType="image/jpeg";
              else if (covrbox.getDataType()==14) contentType="image/png";
            } catch(IndexOutOfBoundsException e)
            {
            } catch(IOException e)
            {
              System.out.println("ERRCOVERIO:"+e.getMessage());
            } catch(Exception e)
            {
              System.out.println("ERRCOVERCMN:"+e.getMessage());
              e.printStackTrace(System.out);
            } finally 
            {
              if (ifile!=null)try
              {
                ifile.close();
              }catch (IOException e)
              {
                e.printStackTrace();
              }
            }
          }
          return;
        }
      }
    }
    
    if (fileItem!=null)return;
    if (CMDBrowse.equalsIgnoreCase(path.getCommand()))
    {
      ArrayList<FileItem> items=new ArrayList<>();
      if (isAdmin())
      {
        File[] drives = File.listRoots();
        if (drives != null && drives.length > 0) 
        {
          for (File aDrive : drives)
          {
            String fullPath=aDrive.getAbsolutePath();
            items.add(new FileItem("/".equals(fullPath)?Portal.ROOTFOLDER:fullPath,fullPath,"/".equals(fullPath)?(Portal.ROOTFOLDER+"/"):fullPath,FileItem.TYPEVOLUMES));
          }
        } 
      }
      final String currentAccount = getAccount(); // Get current user's account once
      JSONArray libraries=Portal.getProperties(Portal.PropLibraries);

      if (libraries!=null&&libraries.length()>0&&currentAccount!=null)
      {
        List<FileItem> accessibleLibraryItems=IntStream.range(0,libraries.length()).mapToObj(libraries::getJSONObject) 
            .filter(library -> 
            {
              // Filter 1: Check if the current user's account has access
              JSONArray accs=library.optJSONArray(Portal.LibraryAccounts);
              if (accs==null||accs.isEmpty()) return false;
              return IntStream.range(0,accs.length()).mapToObj(accs::optString).filter(s -> s!=null&&!s.trim().isEmpty()).map(String::trim).anyMatch(currentAccount::equalsIgnoreCase);
            }).filter(library -> 
            {
              String libPath=library.optString(Portal.LibraryPath);
              if (libPath==null||libPath.trim().isEmpty())return false;
              File check=new File(libPath.trim());
              return check.exists()&&check.isDirectory();
            }).map(library ->
            {
              String name=library.optString(Portal.LibraryName);
              String libPath=library.optString(Portal.LibraryPath);
              return new FileItem(name,libPath,name+"/",FileItem.TYPEVOLUMES);
            }).collect(Collectors.toList());
        items.addAll(accessibleLibraryItems);
      }

      // Cloud mounts are per-user and live in .vibed, so they are listed alongside the
      // shared libraries rather than being added to them.
      JSONArray clouds=CloudConfig.getClouds(session);
      for (int i=0;i<clouds.length();i++)
      {
        JSONObject entry=clouds.getJSONObject(i);
        String label=entry.optString(CloudConfig.KeyLabel);
        if (label==null || label.length()==0)continue;
        String display=entry.optString(CloudConfig.KeyRootName,label);
        // Prefer the account/label the user gave; fall back to the provider name.
        String name=label;
        String account=entry.optString(CloudConfig.KeyAccount,null);
        if (account!=null && account.length()>0)display=label+" ("+account+")";
        items.add(new FileItem(display,name,CloudConfig.CLOUDPREFIX+label+"/",FileItem.TYPECLOUD));
      }
      fileItem=items.toArray(new FileItem[0]);
    }
  }

  /**
   * Lists a folder on a linked cloud account, or renders cover art for one file there.
   * Mirrors the local branch so the JSPs need no cloud-specific rendering.
   */
  private void processCloud()
  {
    CloudProvider provider=path.getProvider();
    if (provider==null)
    {
      message="Cloud storage '"+path.getCloudLabel()+"' is not connected.";
      return;
    }
    if (!provider.isValid())
    {
      message="Cloud storage '"+path.getCloudLabel()+"' needs to be reconnected.";
      return;
    }
    try
    {
      String itemId=path.getCloudId();
      if (itemId==null)
      {
        message="Not found in "+path.getCloudLabel();
        return;
      }
      if (CMDThumbMP4.equalsIgnoreCase(path.getCommand()) || CMDCoverMP4.equalsIgnoreCase(path.getCommand()))
      {
        loadCloudCover(provider,itemId,CMDThumbMP4.equalsIgnoreCase(path.getCommand()));
        return;
      }

      CloudItem[] children=provider.listChildren(itemId);
      CloudConfig.cacheChildren(session,provider,path.getCloudPath(),children);

      Map<String,JSONObject> progressMap=new HashMap<>();
      JSONObject userData=SessionTracker.getSessionData(session);
      JSONArray progress=userData.optJSONArray(SessionTracker.DataProgress);
      if (progress!=null)for (int i=0;i<progress.length();i++)
      {
        JSONObject item=progress.getJSONObject(i);
        progressMap.put(item.optString(SessionTracker.DataProgressFile),item);
      }

      String prefix=CloudConfig.CLOUDPREFIX+path.getCloudLabel();
      String base=path.getCloudPath();
      if (base.length()>0)base="/"+base;

      // This fresh listing is authoritative for the folder, so watch-list entries that
      // point at direct children no longer present are pruned here for free - the
      // moved or deleted file drops off the watch list without any extra provider call.
      if (progress!=null)
      {
        HashSet<String> present=new HashSet<>();
        for (CloudItem child:children)present.add(child.getName());
        String entryPrefix=CMDBrowse+"/"+prefix+base+"/";
        for (int i=progress.length()-1;i>=0;i--)
        {
          String key=progress.getJSONObject(i).optString(SessionTracker.DataProgressFile);
          if (key==null || !key.startsWith(entryPrefix))continue;
          String rest=key.substring(entryPrefix.length());
          // Only this folder's own files are decidable from this listing.
          if (rest.indexOf('/')>=0)continue;
          if (!present.contains(rest))progress.remove(i);
        }
      }

      ArrayList<FileItem> items=new ArrayList<>();
      for (CloudItem child:children)
      {
        String name=child.getName();
        if (name==null || name.startsWith("."))continue;
        String mapPath=prefix+base+"/"+name;
        if (child.isFolder())
        {
          items.add(new FileItem(name,name,mapPath,FileItem.TYPEDIRECTORY));
          continue;
        }
        if (!isMedia(name))continue;
        JSONObject progressItem=progressMap.get(CMDBrowse+"/"+mapPath);
        if (progressItem!=null)
          items.add(new FileItem(name,name,mapPath,FileItem.TYPEFILE,
              progressItem.optInt(SessionTracker.DataProgressTime),progressItem.optLong(SessionTracker.DataProgressUpdate)));
        else items.add(new FileItem(name,name,mapPath,FileItem.TYPEFILE));
      }
      fileItem=items.toArray(new FileItem[0]);
    } catch (IOException e)
    {
      message="Cloud error: "+e.getMessage();
    }
  }

  /**
   * Cover art for a cloud file: a sidecar image beside it wins, otherwise the embedded
   * Apple 'covr' atom is read over HTTP ranges.
   */
  private void loadCloudCover(CloudProvider provider,String itemId,boolean thumbnail) throws IOException
  {
    contentType="image/jpeg";
    String tag="COVER["+path.getCloudLabel()+"/"+path.getCloudPath()+"]";
    byte[] sidecar=findCloudSidecar(provider);
    if (sidecar!=null)
    {
      cover=new ByteArrayInputStream(sidecar);
      if (thumbnail)cover=scaleThumb(cover);
      else contentType=sidecarType;
      return;
    }

    CloudItem item=provider.getItem(itemId);
    if (item==null)
    {
      System.out.println(tag+" no metadata (getItem null) - cannot read embedded cover");
      return;
    }
    if (item.getSize()<=0)
    {
      System.out.println(tag+" size unknown ("+item.getSize()+") - cannot read embedded cover");
      return;
    }
    RemoteSource source=new RemoteSource(provider,itemId,item.getSize());
    IsoFile ifile=null;
    try
    {
      ifile=source.openIso();
      AppleCoverBox covrbox=findCoverBox(ifile);
      if (covrbox==null)
      {
        System.out.println(tag+" file has no embedded cover art");
        return;
      }
      cover=new ByteArrayInputStream(covrbox.getCoverData());
      if (thumbnail)cover=scaleThumb(cover);
      else if (covrbox.getDataType()==14)contentType="image/png";
    } catch (IndexOutOfBoundsException e)
    {
      System.out.println(tag+" no embedded cover box");
    } catch (Exception e)
    {
      // For FileLu this is normally the premium-only download link being refused.
      System.out.println(tag+" embedded cover read failed: "+e.getMessage());
    } finally
    {
      if (ifile!=null)try
      {
        ifile.close();
      } catch (IOException e)
      {
      }
      source.close();
    }
  }

  /** An image beside the video with the same base name, e.g. movie.mp4 + movie.jpg. */
  private byte[] findLocalSidecar(File file)
  {
    File parent=file.getParentFile();
    if (parent==null)return null;
    String stem=baseName(file.getName());
    if (stem.length()==0)return null;
    String[] extensions=new String[]{".jpg",".jpeg",".png"};
    for (String extension:extensions)
    {
      File candidate=new File(parent,stem+extension);
      if (!candidate.exists() || !candidate.isFile())continue;
      try
      {
        byte[] data=java.nio.file.Files.readAllBytes(candidate.toPath());
        sidecarType=".png".equals(extension)?"image/png":"image/jpeg";
        return data;
      } catch (IOException e)
      {
        return null;
      }
    }
    return null;
  }

  private byte[] findCloudSidecar(CloudProvider provider) throws IOException
  {
    String cloudPath=path.getCloudPath();
    int slash=cloudPath.lastIndexOf('/');
    String parentPath=slash>=0?cloudPath.substring(0,slash):"";
    String fileName=slash>=0?cloudPath.substring(slash+1):cloudPath;
    String stem=baseName(fileName);
    if (stem.length()==0)return null;

    String parentId=CloudConfig.resolveId(session,provider,parentPath);
    if (parentId==null)return null;
    String[] extensions=new String[]{".jpg",".jpeg",".png"};
    for (String extension:extensions)
    {
      CloudItem sidecar=provider.findChild(parentId,stem+extension);
      if (sidecar==null || sidecar.isFolder())continue;
      // A refused link on the sidecar (e.g. FileLu without premium) must not abort the
      // whole cover attempt - fall through to the embedded cover instead.
      try
      {
        CloudLink link=provider.getDownloadLink(sidecar.getId());
        int size=(int)Math.min(sidecar.getSize()>0?sidecar.getSize():8L*1024*1024,8L*1024*1024);
        byte[] data=HttpTool.getRange(link.getURL(),link.getHeaders(),0,size);
        if (data!=null)
        {
          sidecarType=".png".equals(extension)?"image/png":"image/jpeg";
          return data;
        }
        System.out.println("SIDECAR["+stem+extension+"] range read returned nothing ("+HttpTool.getLastStatus()+")");
      } catch (IOException e)
      {
        System.out.println("SIDECAR["+stem+extension+"] link failed: "+e.getMessage());
      }
    }
    return null;
  }

  private AppleCoverBox findCoverBox(IsoFile ifile)
  {
    try
    {
      MovieBox moov=ifile.getBoxes(MovieBox.class).get(0);
      UserDataBox udta=moov.getBoxes(UserDataBox.class).get(0);
      MetaBox mbox=udta.getBoxes(MetaBox.class).get(0);
      AppleItemListBox aplbox=mbox.getBoxes(AppleItemListBox.class).get(0);
      return aplbox.getBoxes(AppleCoverBox.class).get(0);
    } catch (IndexOutOfBoundsException e)
    {
      return null;
    }
  }

  /** Downscales cover art to the browse-strip size, matching the local thumb path. */
  private ByteArrayInputStream scaleThumb(ByteArrayInputStream source) throws IOException
  {
    BufferedImage bufferedThumb=ImageIO.read(source);
    if (bufferedThumb==null)return source;
    int currentWidth=bufferedThumb.getWidth();
    int currentHeight=bufferedThumb.getHeight();
    int width=320;
    int height=(width*currentHeight)/currentWidth;
    if (currentWidth>width)
    {
      BufferedImage temp=new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB);
      Graphics2D gTemp=temp.createGraphics();
      gTemp.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
      gTemp.setRenderingHint(RenderingHints.KEY_RENDERING,RenderingHints.VALUE_RENDER_QUALITY);
      gTemp.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
      gTemp.drawImage(bufferedThumb,0,0,width,height,null);
      gTemp.dispose();
      bufferedThumb=temp;
    }
    ByteArrayOutputStream bos=new ByteArrayOutputStream();
    ImageIO.write(bufferedThumb,"JPG",bos);
    source.close();
    return new ByteArrayInputStream(bos.toByteArray());
  }
  
  public String getPrePath()
  {
    return path.getLibrary();
  }

  /** Cloud folders cannot become libraries - LibraryBean validates with new File(). */
  public boolean isCloudPath()
  {
    return path!=null && path.isCloud();
  }
  
  public String[] getSubPaths()
  {
    return path.getSubPaths();
  }
  
  public FileItem[] listFiles()
  {
    Arrays.sort(fileItem,this);
    return fileItem;
  }

  public FileItem[] getWatchedFiles()
  {
    if (fileItem==null)return new FileItem[0];
    List<FileItem> watched=new ArrayList<>();
    for (FileItem f:fileItem)
    {
      if (f.getTimeProgress()>0)
      {
        watched.add(f);
      }
    }
    if (watched.isEmpty())
    {
      return new FileItem[0];
    }
    Collections.sort(watched,new Comparator<FileItem>()
    {
      @Override
      public int compare(FileItem f1,FileItem f2)
      {
        return Long.compare(f2.getSortTime(),f1.getSortTime());
      }
    });
    return watched.toArray(new FileItem[0]);
  }

  public ByteArrayInputStream getCover()
  {
    return cover;
  }
  
  @Override
  public int compare(Object o1,Object o2)
  {
    if (o1 instanceof FileItem)
    {
      FileItem f1=(FileItem)o1;
      FileItem f2=(FileItem)o2;
      if (f1.getType()!=f2.getType())return f1.getType()-f2.getType();
      else return f1.getName().compareToIgnoreCase(f2.getName());
    }
    return 0;
  }

}
