package id.prasetya.vibrefy.tools.google;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

import org.json.*;

import id.prasetya.vibrefy.beans.BeanObject;

public class Token
{
  static final String ContentTypeURLEncoded="application/x-www-form-urlencoded";
  static final String ContentTypeJSON="application/json; charset=UTF-8";
  
  static final String APIOAuth="https://accounts.google.com/o/oauth2/";
  
  private String clientId;
  private String clientSecret;
  private String refreshToken=null;
  private String accessToken=null;
  private String lastTokenMessage=null;
  private long tokenExpire=0;

  public Token(String clientId,String clientSecret, String refreshToken)
  {
    this.clientId=clientId;
    this.clientSecret=clientSecret;
    if (refreshToken!=null)refreshToken(refreshToken);
  }
  
  public Token(File serviceAccountJson, String scope) {
    try {
        JSONObject json = new JSONObject(new JSONTokener(new FileReader(serviceAccountJson)));

        String clientEmail = json.getString("client_email");
        String privateKeyPem = json.getString("private_key");
        String tokenUri = json.getString("token_uri");

        // Clean up the PEM
        String privateKeyClean = privateKeyPem
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replaceAll("\\s+", "");

        byte[] pkcs8EncodedBytes = Base64.getDecoder().decode(privateKeyClean);

        // Build PrivateKey
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(pkcs8EncodedBytes);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        PrivateKey privateKey = kf.generatePrivate(keySpec);

        long now = System.currentTimeMillis() / 1000;
        JSONObject jwtHeader = new JSONObject();
        jwtHeader.put("alg", "RS256");
        jwtHeader.put("typ", "JWT");

        JSONObject jwtClaimSet = new JSONObject();
        jwtClaimSet.put("iss", clientEmail);
        jwtClaimSet.put("scope", scope);
        jwtClaimSet.put("aud", tokenUri);
        jwtClaimSet.put("exp", now + 3600);
        jwtClaimSet.put("iat", now);

        String headerBase64 = Base64.getUrlEncoder().withoutPadding().encodeToString(jwtHeader.toString().getBytes(StandardCharsets.UTF_8));
        String claimBase64 = Base64.getUrlEncoder().withoutPadding().encodeToString(jwtClaimSet.toString().getBytes(StandardCharsets.UTF_8));
        String unsignedJWT = headerBase64 + "." + claimBase64;

        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(privateKey);
        signature.update(unsignedJWT.getBytes(StandardCharsets.UTF_8));
        String signatureBase64 = Base64.getUrlEncoder().withoutPadding().encodeToString(signature.sign());

        String jwt = unsignedJWT + "." + signatureBase64;

        String requestBody = "grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer&assertion=" + encode(jwt);
        String tokenResponse = httpPost(tokenUri, requestBody, ContentTypeURLEncoded, false);

        JSONObject tokenJson = new JSONObject(tokenResponse);
        this.accessToken = tokenJson.getString("access_token");
        this.tokenExpire = System.currentTimeMillis() + tokenJson.getInt("expires_in") * 1000;

    } catch (Exception e) {
        e.printStackTrace(System.out);
        throw new RuntimeException("Service account auth failed", e);
    }
  }
  
  /* Usage:
File jsonKey = new File("my-service-account.json");
String scopes = "https://www.googleapis.com/auth/drive https://www.googleapis.com/auth/spreadsheets";
Token token = new Token(jsonKey, scopes);

Drive drive = new Drive(token);
String result = drive.about();
System.out.println(result);

   */

  
  protected static String encode(String data)
  {
    try
    {
      return URLEncoder.encode(data,StandardCharsets.UTF_8.name());
    }catch (UnsupportedEncodingException e) {return data;}
  }
  
  
  public String httpGet(String url)
  {
    try
    {
      HttpURLConnection urlConn=(HttpURLConnection) new URL(url).openConnection();
      urlConn.setRequestMethod("GET");
      String bearer=getAccessToken();
      if (bearer!=null)
        urlConn.setRequestProperty( "Authorization", "Bearer "+bearer);
      urlConn.setUseCaches( false );
      StringBuilder resp=new StringBuilder();
      BufferedReader br=new BufferedReader(new InputStreamReader(urlConn.getInputStream()));
      String inputLine;
      while ((inputLine=br.readLine())!=null)
      {
        resp.append(inputLine+"\r\n");
      }
      urlConn.disconnect();
      return resp.toString();
    }catch(Exception e){e.printStackTrace(System.out);}
    return null;
  }
  
  public String httpPost(String url,String message,String contentType,boolean useBearer)
  {
    try
    {
      byte[] body=message.getBytes(StandardCharsets.UTF_8);
      HttpURLConnection urlConn=(HttpURLConnection) new URL(url).openConnection();
      urlConn.setRequestMethod("POST");
      urlConn.setRequestProperty( "Content-Type", contentType);
      urlConn.setDoInput(true);
      urlConn.setDoOutput(true);
      urlConn.setRequestProperty( "Charset", StandardCharsets.UTF_8.name());
      urlConn.setRequestProperty( "Content-Length", Integer.toString(body.length));
      if (useBearer)
      {
        String bearer=getAccessToken();
        if (bearer!=null)
          urlConn.setRequestProperty( "Authorization", "Bearer "+bearer);
      }
      urlConn.setUseCaches( false );
      OutputStream os=urlConn.getOutputStream();
      os.write(body);
      os.flush();
      os.close();
      StringBuilder resp=new StringBuilder();
      BufferedReader br=new BufferedReader(new InputStreamReader(urlConn.getInputStream()));
      String inputLine;
      while ((inputLine=br.readLine())!=null)
      {
        resp.append(inputLine+"\r\n");
      }
      urlConn.disconnect();
      return resp.toString();
    }catch(Exception e){e.printStackTrace(System.out);}
    return null;
  }
  
  public String httpPost(String url,String message,String contentType)
  {
    return httpPost(url,message,contentType,true);
  }
    
  public String getAccessToken()
  {
    if (refreshToken!=null && tokenExpire<System.currentTimeMillis())
    {
      accessToken=null;
      refreshToken(refreshToken);
    }
    return accessToken;
  }

  
  private void grabAccessToken(String data)
  {
    lastTokenMessage=data;
    JSONObject authObj=new JSONObject(new JSONTokener(data));
    accessToken=authObj.getString("access_token");
    if (accessToken!=null)
      tokenExpire=System.currentTimeMillis()+(authObj.getInt("expires_in")*1000);
    if (authObj.has("refresh_token"))refreshToken=authObj.getString("refresh_token");
  }
  
  public String getLastAccessMessage() {return lastTokenMessage;}
    
  public String refreshToken(String refreshToken)
  {
    this.refreshToken=refreshToken;
    String rst=httpPost(APIOAuth+"token","refresh_token="+refreshToken+"&client_id="+clientId+"&client_secret="+clientSecret+"&grant_type=refresh_token",ContentTypeURLEncoded,false);
    grabAccessToken(rst);
    return rst;
  }
  
  public String getRefreshToken()
  {
    return refreshToken;
  }

}
