package id.prasetya.vibrefy;

import jakarta.servlet.annotation.WebListener;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import javax.crypto.*;
import javax.crypto.spec.*;

import org.json.JSONObject;

import jakarta.servlet.*;
import jakarta.servlet.http.*;

@WebListener
public class SessionTracker implements HttpSessionListener,HttpSessionIdListener,ServletContextListener
{
  private static final String PropEncryption="KeySet";
  private static final String PROP_MASTER_KEY_HEX="KeyID";
  private static final String PROP_FILENAME_SALT="NameID";
  private static final String USER_DATA_FILE_EXTENSION=".vibed";
  private static final int GCM_IV_LENGTH=12; // 96 bits
  private static final int GCM_TAG_LENGTH=16; // 128 bits, in bytes
  private static final String ENCRYPTION_ALGORITHM="AES/GCM/NoPadding";
  private static final int KEY_LENGTH_BITS=256; // AES-256
  private static final int PBKDF2_ITERATIONS=65536; // Number of iterations for PBKDF2
  private static final int PBKDF2_SALT_LENGTH=16; // 16 bytes for PBKDF2 salt
  
  public static final String DataProgress="progress";
  public static final String DataProgressFile="file";
  public static final String DataProgressTime="time";
  public static final String DataProgressUpdate="last";
  public static final String DataClouds="clouds";
  

  private static final ConcurrentHashMap<String,HttpSession> sessionMap=new ConcurrentHashMap<>();
  private static final ConcurrentHashMap<String,JSONObject> jsonMap=new ConcurrentHashMap<>();
  private static final ConcurrentHashMap<String, Integer> issSubIdSessions = new ConcurrentHashMap<>();
    
  private static SecretKey serverMasterKey;
  private static String serverSecretSaltForFilenames;


  @Override
  public void sessionCreated(HttpSessionEvent event)
  {
    HttpSession session=event.getSession();
    sessionMap.put(session.getId(),session);
    System.out.println("Session Created: "+session.getId());
  }

  @Override
  public void sessionDestroyed(HttpSessionEvent event)
  {
    HttpSession sessionBeingDestroyed=event.getSession();
    String sessionId=sessionBeingDestroyed.getId();
    sessionMap.remove(sessionId);

    String issSubId=(String)sessionBeingDestroyed.getAttribute(Portal.SessionAccountID);
    if (issSubId==null)return;
    issSubIdSessions.compute(issSubId,(key,count) ->
    {
      if (count==null||count<=1)
      {
        JSONObject userData=jsonMap.remove(issSubId);
        if (userData!=null)saveUserJsonData(issSubId,userData);
        // Cached cloud providers hold a listener bound to a session; with the user's
        // last session gone they would pin it in heap, so let go of them too.
        id.prasetya.vibrefy.tools.cloud.CloudConfig.releaseUser(issSubId);
        return null;
      }else return count-1;
    });
  }
  
  public static void sessionCheck(HttpSession session)
  {
    String sessionId=session.getId();
    if (!sessionMap.containsKey(sessionId))
    {
      sessionMap.put(sessionId,session);
      System.out.println("Session Tracked: "+sessionId);
    }
    String issSubId=(String)session.getAttribute(Portal.SessionAccountID);
    if (issSubId==null)return;
    issSubIdSessions.merge(issSubId,1,Integer::sum);
    
    if (jsonMap.containsKey(issSubId))return;
    jsonMap.put(issSubId,loadUserJsonData(issSubId));
  }

  @Override
  public void sessionIdChanged(HttpSessionEvent event,String oldSessionId)
  {
    HttpSession session=event.getSession();
    sessionMap.remove(oldSessionId);
    sessionMap.put(session.getId(),session);
  }

  public static HttpSession getSessionById(String sessionId)
  {
    return sessionMap.get(sessionId);
  }

  /**
   * Writes this user's data to disk straight away instead of waiting for their last
   * session to end. Playback progress can afford to be lazy; cloud credentials cannot,
   * because a refresh token lost to a crash means the account has to be re-linked, and
   * some providers rotate that token on every use.
   */
  public static void flush(HttpSession session)
  {
    if (session==null)return;
    try
    {
      String issSubId=(String)session.getAttribute(Portal.SessionAccountID);
      if (issSubId==null)return;
      JSONObject data=jsonMap.get(issSubId);
      if (data!=null)saveUserJsonData(issSubId,data);
    } catch (IllegalStateException e)
    {
      // A token refresh can fire from a cached provider whose session has since been
      // invalidated; there is nothing to flush against in that case.
    }
  }
  
  public static JSONObject getSessionData(HttpSession session)
  {
    String issSubId=(String) session.getAttribute(Portal.SessionAccountID);
    if (issSubId==null)return new JSONObject();
    
    JSONObject data=jsonMap.get(issSubId);
    if (data!=null)return data;
    data=loadUserJsonData(issSubId);
    jsonMap.put(issSubId,data);
    return data;
  }
  
  public static void setSessionData(HttpSession session,JSONObject data)
  {
    String issSubId=(String) session.getAttribute(Portal.SessionAccountID);
    if (issSubId==null)return;
    jsonMap.put(issSubId,data);
  }


  public static void listSessions()
  {
    System.out.println("Active Sessions:");
    sessionMap.forEach((id,session)->System.out.println("Session ID: "+id+", Created: "+session.getCreationTime()));
    System.out.println("User Cache:");
    jsonMap.forEach((issSubId,data)->System.out.println("User: "+issSubId+" (Sessions: "+issSubIdSessions.getOrDefault(issSubId,0)+"), Data Size: "+data.length()+" keys."));
  }

  public static int getActiveSessionCount()
  {
    return sessionMap.size();
  }
  
  @Override
  public void contextInitialized(ServletContextEvent sce)
  {
    Portal.reloadProperty();
    JSONObject encryption=Portal.getProperty(PropEncryption);
    if (encryption == null)encryption = new JSONObject();

    String masterKeyHex = encryption.optString(PROP_MASTER_KEY_HEX, null);
    String filenameSalt = encryption.optString(PROP_FILENAME_SALT, null);
    
    boolean saveProp=false;
    SecureRandom secureRandom = new SecureRandom();
    if (masterKeyHex == null || masterKeyHex.isEmpty())
    {
      byte[] masterKeyBytes = new byte[KEY_LENGTH_BITS / 8]; // 32 bytes for AES-256
      secureRandom.nextBytes(masterKeyBytes);
      masterKeyHex = byteArrayToHexString(masterKeyBytes);
      encryption.put(PROP_MASTER_KEY_HEX, masterKeyHex);
      saveProp=true;
    }
    if (filenameSalt == null || filenameSalt.isEmpty())
    {
      byte[] filenameSaltBytes = new byte[16]; // 16 bytes for a good salt (32 hex characters)
      secureRandom.nextBytes(filenameSaltBytes);
      filenameSalt = byteArrayToHexString(filenameSaltBytes);
      encryption.put(PROP_FILENAME_SALT, filenameSalt);
      saveProp=true;
    }
    if (saveProp)Portal.setProperty(PropEncryption,encryption);


    try
    {
      byte[] masterKeyBytes=hexStringToByteArray(masterKeyHex);
      if (masterKeyBytes.length*8!=KEY_LENGTH_BITS)  throw new IllegalArgumentException("Master key (from hex) must be "+KEY_LENGTH_BITS+"-bit long.");
      serverMasterKey=new SecretKeySpec(masterKeyBytes,"AES");
      serverSecretSaltForFilenames=filenameSalt;
    }catch (IllegalArgumentException e)
    {
      System.err.println("FATAL ERROR: Failed to initialize secure data management components. User data cannot be secured.");
      e.printStackTrace(System.out);
    }

    System.out.println("Context Initialized.");
  }
    
    
   @Override
  public void contextDestroyed(ServletContextEvent sce)
  {
    System.out.println("Application stopped. Cleaning up.");
    for (Map.Entry<String,JSONObject> entry:jsonMap.entrySet())
    {
      String issSubId=entry.getKey();
      JSONObject userData=entry.getValue();
      if (userData==null)continue;
      saveUserJsonData(issSubId,userData);
    }
    sessionMap.clear();
    jsonMap.clear();
    issSubIdSessions.clear();
    // Drop everything the cloud layer keeps statically, HttpClient included, so a hot
    // redeploy cannot leak this deployment's classloader into the next one.
    id.prasetya.vibrefy.tools.cloud.CloudConfig.shutdown();
    id.prasetya.vibrefy.tools.cloud.HttpTool.shutdown();
  }
   
   private static String byteArrayToHexString(byte[] bytes)
   {
     StringBuilder sb=new StringBuilder();
     for (byte b:bytes)
     {
       sb.append(String.format("%02x",b));
     }
     return sb.toString();
   }

   private static byte[] hexStringToByteArray(String hexString)
   {
     int len=hexString.length();
     byte[] data=new byte[len/2];
     for (int i=0;i<len;i+=2)
     {
       data[i/2]=(byte)((Character.digit(hexString.charAt(i),16)<<4)+Character.digit(hexString.charAt(i+1),16));
     }
     return data;
   }
   
   private static String getFilenameForUser(String issSubId) throws NoSuchAlgorithmException
   {
     MessageDigest digest=MessageDigest.getInstance("SHA-256");
     // Combine iss+sub ID with a server-side secret salt before hashing
     byte[] hashedBytes=digest.digest((issSubId+serverSecretSaltForFilenames).getBytes(StandardCharsets.UTF_8));
     return byteArrayToHexString(hashedBytes);
   }

   private static SecretKey deriveUserKey(String issSubId,byte[] salt) throws NoSuchAlgorithmException,InvalidKeySpecException
   {
     SecretKeyFactory factory=SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
     // Use the serverMasterKey's encoded form as the password for PBKDF2
     PBEKeySpec spec=new PBEKeySpec(Base64.getEncoder().encodeToString(serverMasterKey.getEncoded()).toCharArray(),salt,PBKDF2_ITERATIONS,KEY_LENGTH_BITS);
     SecretKey tmp=factory.generateSecret(spec);
     return new SecretKeySpec(tmp.getEncoded(),"AES");
   }

   public static void saveUserJsonData(String issSubId,JSONObject jsonObject) 
   {
     if (serverMasterKey==null||serverSecretSaltForFilenames==null)
     {
       System.out.println("Secure data management components are not initialized.");
       return;
     }
     
     String plainTextJson=jsonObject.toString(); 
     
     SecureRandom secureRandom=new SecureRandom();
     byte[] iv=new byte[GCM_IV_LENGTH];
     secureRandom.nextBytes(iv);

     byte[] pbkdf2Salt;
     try
     {
       pbkdf2Salt=MessageDigest.getInstance("SHA-256").digest(issSubId.getBytes(StandardCharsets.UTF_8));
       pbkdf2Salt=java.util.Arrays.copyOf(pbkdf2Salt,PBKDF2_SALT_LENGTH);
       SecretKey userKey=deriveUserKey(issSubId,pbkdf2Salt);
       Cipher cipher=Cipher.getInstance(ENCRYPTION_ALGORITHM);
       GCMParameterSpec gcmParameterSpec=new GCMParameterSpec(GCM_TAG_LENGTH*8,iv);
       cipher.init(Cipher.ENCRYPT_MODE,userKey,gcmParameterSpec);

       cipher.updateAAD(issSubId.getBytes(StandardCharsets.UTF_8));

       byte[] cipherText=cipher.doFinal(plainTextJson.getBytes(StandardCharsets.UTF_8));

       ByteBuffer byteBuffer=ByteBuffer.allocate(GCM_IV_LENGTH+PBKDF2_SALT_LENGTH+cipherText.length);
       byteBuffer.put(iv);
       byteBuffer.put(pbkdf2Salt);
       byteBuffer.put(cipherText);
       byte[] encryptedData=byteBuffer.array();

       String filename=getFilenameForUser(issSubId)+USER_DATA_FILE_EXTENSION;
       try (FileOutputStream fos=new FileOutputStream(filename))
       {
         fos.write(encryptedData);
       }catch (IOException e)
       {
         System.out.println("IOException on saving user data.");
         e.printStackTrace(System.out);
       }
     }catch (NoSuchAlgorithmException|InvalidKeySpecException|NoSuchPaddingException|InvalidKeyException|InvalidAlgorithmParameterException|IllegalBlockSizeException|BadPaddingException e)
     {
       System.out.println("Encryption exception on saving user data.");
       e.printStackTrace(System.out);
     }


   }

   public static JSONObject loadUserJsonData(String issSubId)
   {
     if (serverMasterKey==null||serverSecretSaltForFilenames==null)
     {
       System.out.println("Secure data management components are not initialized.");
       return new JSONObject();
     }
     try
     {

       String filename=getFilenameForUser(issSubId)+USER_DATA_FILE_EXTENSION;
       File userFile=new File(filename);
       if (userFile.exists())
       {
         byte[] encryptedData;
         try (FileInputStream fis=new FileInputStream(userFile))
         {
           encryptedData=fis.readAllBytes();
         }

         ByteBuffer byteBuffer=ByteBuffer.wrap(encryptedData);

         byte[] iv=new byte[GCM_IV_LENGTH];
         byteBuffer.get(iv);

         byte[] pbkdf2Salt=new byte[PBKDF2_SALT_LENGTH];
         byteBuffer.get(pbkdf2Salt);

         byte[] cipherText=new byte[byteBuffer.remaining()];
         byteBuffer.get(cipherText);

         SecretKey userKey=deriveUserKey(issSubId,pbkdf2Salt);

         Cipher cipher=Cipher.getInstance(ENCRYPTION_ALGORITHM);
         GCMParameterSpec gcmParameterSpec=new GCMParameterSpec(GCM_TAG_LENGTH*8,iv);
         cipher.init(Cipher.DECRYPT_MODE,userKey,gcmParameterSpec);

         cipher.updateAAD(issSubId.getBytes(StandardCharsets.UTF_8));

         byte[] plainText=cipher.doFinal(cipherText);
         String plainTextJson=new String(plainText,StandardCharsets.UTF_8);

         return new JSONObject(plainTextJson);
       }
     }catch (NoSuchAlgorithmException|InvalidKeySpecException|NoSuchPaddingException|InvalidKeyException|InvalidAlgorithmParameterException|IllegalBlockSizeException|BadPaddingException|IOException e)
     {
       System.out.println("Exception on loading user data.");
       e.printStackTrace(System.out);
     }
     System.out.println("No data found for user "+issSubId);
     return new JSONObject();
   }

 }
