package id.prasetya.vibrefy.tools;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

import org.json.*;

public class OpenID
{
  private char providerCode;
  private String domain=null;
  private String configURL=null;
  private String clientID=null;
  private String secret=null;
  private String scope=null;
  private String authOption=null;
  private String redirectURI=null;
  private JSONObject configuration=null;
  private JSONObject jwks=null;
  
  public char getProviderCode() {return providerCode;}
  public String getDomain() {return domain;}
  public String getConfigURL() {return configURL;}
  public String getClientID() {return clientID;}
  
  public OpenID(char provider,String domain,String configURL,String clientID,String secret,String scope,String authOption) throws IOException
  {
    this.providerCode=provider;
    this.domain=domain;
    this.configURL=configURL;
    this.clientID=clientID;
    this.secret=secret;
    this.scope=scope;
    this.authOption=authOption;
    refresh();
  }
  
  protected static String encode(String data)
  {
    try
    {
      return URLEncoder.encode(data,StandardCharsets.UTF_8.name());
    }catch (UnsupportedEncodingException e) {return data;}
  }
  
  public void refresh() throws IOException
  {
    configuration=new JSONObject(new JSONTokener(new URL(configURL).openConnection().getInputStream()));
    if (configuration!=null)
    {
      jwks=new JSONObject(new JSONTokener(new URL(configuration.getString("jwks_uri")).openConnection().getInputStream()));
    }
  }
  
  public String getAuthorizationEndpoint(String redirectURI,String state) 
  {
    if (configuration!=null)
    {
      String url=configuration.getString("authorization_endpoint");
      if (url!=null)
      {
        this.redirectURI=encode(redirectURI);
        return url+"?scope="+encode(scope)+"&response_type=code&state="+state+"&client_id="+clientID+"&redirect_uri="+this.redirectURI+(authOption==null?"":"&"+authOption);
      }
    }
    return "#";
  }

  public JSONObject exchangeCode(String code) throws IOException
  {
    if (configuration!=null)
    {
      String data="client_id="+clientID+"&client_secret="+encode(secret)+"&grant_type=authorization_code&redirect_uri="+redirectURI+"&code="+code;
      HttpURLConnection urlConn=(HttpURLConnection)new URL(configuration.getString("token_endpoint")).openConnection();
      urlConn.setRequestMethod("POST");
      urlConn.setDoInput(true);
      urlConn.setDoOutput(true);
      urlConn.setRequestProperty("Accept", "*/*");
      urlConn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
      
      OutputStream os=urlConn.getOutputStream();
      os.write(data.getBytes());
      os.flush();
      
      JSONObject result=new JSONObject(new JSONTokener(urlConn.getInputStream()));
      return result;

    }
    return null;
  }
  
  public String getIssuer()
  {
    if (configuration!=null)
    {
      return configuration.getString("issuer");
    }
    return null;
  }
  
  private JSONObject checkKey(String kid)
  {
    if (jwks!=null)
    {
      JSONArray keys=jwks.getJSONArray("keys");
      if (keys!=null)for (int i=0;i<keys.length();i++)if (kid.equals(keys.getJSONObject(i).getString("kid")))return keys.getJSONObject(i);
    }
    return null;
  }
  
  public JSONObject getKey(String kid)
  {
    JSONObject rst=checkKey(kid);
    if (rst==null)
    {
      try{refresh();}catch (IOException e){}
      rst=checkKey(kid);
    }
    return rst;
  }



}
