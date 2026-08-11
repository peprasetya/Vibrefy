package id.prasetya.vibrefy.beans;

import id.prasetya.vibrefy.Portal;
import id.prasetya.vibrefy.SessionTracker;
import id.prasetya.vibrefy.tools.Pairing;

/**
 * Moves a login onto a device that cannot practically run the OAuth popup, in either
 * direction: the device asks and a signed-in user approves, or a signed-in user offers
 * and the device claims.
 *
 * One command serves both callers. It is registered at access type 0 so the device that
 * is not signed in can reach it at all - Portal only rewrites to welcome above that -
 * and every order that acts on an identity checks for one itself.
 *
 * In both directions the identity is written by the device that ends up signed in, on
 * its own session, which is what lets the session id be rotated the same way a normal
 * login rotates it. The approver only marks the record.
 */
public class PairBean extends BeanObject
{
  public static final String CMDPair="pair";

  public static final String ORDRequest="Request";
  public static final String ORDPoll="Poll";
  public static final String ORDFind="Find";
  public static final String ORDApprove="Approve";
  public static final String ORDOffer="Offer";
  public static final String ORDClaim="Claim";

  /** Signed in, nothing in flight: the Devices page. */
  public static final String MODEHome="H";
  /** Signed in, a device code was found and is waiting to be confirmed. */
  public static final String MODEConfirm="C";
  /** Signed in, a code was minted for another device to claim. */
  public static final String MODEOffer="O";
  /** Not signed in, a code was minted and the device is waiting for approval. */
  public static final String MODEWait="W";
  /** Not signed in, entering a code that a signed-in device is showing. */
  public static final String MODEClaim="E";
  /** A poll tick. Renders only the status block, never the whole panel. */
  public static final String MODEPoll="P";

  public static final String POLLWaiting="W";
  public static final String POLLDone="D";
  public static final String POLLExpired="X";

  private String code=null;
  private String mode=MODEHome;
  private String pollState=POLLWaiting;
  private Pairing.Entry found=null;

  public void setCode(String newValue){code=newValue;}

  public String getMode(){return mode;}
  public String getPollState(){return pollState;}
  public String getCode(){return code==null?"":code;}
  public String getDisplayCode(){return Pairing.format(code);}
  public Pairing.Entry getFound(){return found;}

  public long getRemaining()
  {
    return found==null?0:found.getRemaining();
  }

  protected void processData()
  {
    String order=getOrder();
    if (order==null)
    {
      mode=(account==null)?MODEClaim:MODEHome;
      return;
    }

    if (ORDRequest.equals(order))
    {
      requestCode();
      return;
    }
    if (ORDPoll.equals(order))
    {
      poll();
      return;
    }
    if (ORDClaim.equals(order))
    {
      claim();
      return;
    }
    if (ORDFind.equals(order))
    {
      find();
      return;
    }
    if (ORDApprove.equals(order))
    {
      approve();
      return;
    }
    if (ORDOffer.equals(order))
    {
      offer();
      return;
    }
    mode=(account==null)?MODEClaim:MODEHome;
  }

  /** A device asking to be signed in: mint a code bound to this browser and wait. */
  private void requestCode()
  {
    if (account!=null)
    {
      mode=MODEHome;
      return;
    }
    found=Pairing.createDevice(session.getId(),address(),request.getHeader("User-Agent"));
    if (found==null)
    {
      mode=MODEClaim;
      message="Too many pairings are in progress. Try again in a few minutes.";
      return;
    }
    code=found.getCode();
    mode=MODEWait;
  }

  /**
   * The waiting device checking whether it has been approved. Deliberately silent: this
   * runs every few seconds, and anything left in message would become a browser alert
   * on every tick.
   */
  private void poll()
  {
    mode=MODEPoll;
    pollState=POLLExpired;
    Pairing.Entry entry=Pairing.get(code);
    if (entry==null)return;
    if (entry.getKind()!=Pairing.KINDDEVICE)return;
    // The code alone is not enough to collect an approval - it has to be the browser
    // that asked for it.
    if (entry.getSessionId()==null || !entry.getSessionId().equals(session.getId()))return;
    if (!entry.isApproved())
    {
      found=entry;
      pollState=POLLWaiting;
      return;
    }
    signIn(entry.getEmail(),entry.getAccountId(),entry.isAdmin());
    Pairing.remove(entry.getCode());
    pollState=POLLDone;
  }

  /** A signed-in user looking up the code a device is displaying, before approving it. */
  private void find()
  {
    mode=MODEHome;
    if (account==null)return;
    if (Pairing.tooManyAttempts(address()))
    {
      message="Too many incorrect codes. Wait a few minutes and try again.";
      return;
    }
    Pairing.Entry entry=Pairing.get(code);
    if (entry==null || entry.getKind()!=Pairing.KINDDEVICE)
    {
      Pairing.noteFailure(address());
      message="That code is not valid, or it has expired.";
      return;
    }
    Pairing.clearFailures(address());
    found=entry;
    code=entry.getCode();
    mode=MODEConfirm;
  }

  /** Marks the record approved. The waiting device signs itself in on its next poll. */
  private void approve()
  {
    mode=MODEHome;
    if (account==null)return;
    Pairing.Entry entry=Pairing.get(code);
    if (entry==null || entry.getKind()!=Pairing.KINDDEVICE)
    {
      message="That code is not valid, or it has expired.";
      return;
    }
    entry.approve(account,(String)session.getAttribute(Portal.SessionAccountID),isAdmin());
    success=true;
    message="Approved. The other device is signing in now.";
  }

  /** A signed-in user minting a code for another device to type in. */
  private void offer()
  {
    mode=MODEHome;
    if (account==null)return;
    found=Pairing.createUser(account,(String)session.getAttribute(Portal.SessionAccountID),isAdmin());
    if (found==null)
    {
      message="Too many pairings are in progress. Try again in a few minutes.";
      return;
    }
    code=found.getCode();
    mode=MODEOffer;
  }

  /** A device typing in the code a signed-in device is showing. */
  private void claim()
  {
    mode=MODEClaim;
    if (account!=null)
    {
      mode=MODEHome;
      return;
    }
    if (Pairing.tooManyAttempts(address()))
    {
      message="Too many incorrect codes. Wait a few minutes and try again.";
      return;
    }
    Pairing.Entry entry=Pairing.get(code);
    if (entry==null || entry.getKind()!=Pairing.KINDUSER)
    {
      Pairing.noteFailure(address());
      message="That code is not valid, or it has expired.";
      return;
    }
    Pairing.clearFailures(address());
    Pairing.remove(entry.getCode());
    signIn(entry.getEmail(),entry.getAccountId(),entry.isAdmin());
    mode=MODEPoll;
    pollState=POLLDone;
  }

  /**
   * Puts the paired identity on this request's own session, rotating the session id
   * exactly as AuthOpenIDBean does after a token exchange.
   */
  private void signIn(String email,String accountId,boolean admin)
  {
    session.setAttribute(Portal.SessionEmail,email);
    session.setAttribute(Portal.SessionAccountID,accountId);
    if (admin)session.setAttribute(Portal.SessionAdmin,true);
    request.changeSessionId();
    SessionTracker.sessionCheck(session);
    account=email;
  }

  /**
   * The client address as the approver should see it. Behind a proxy the direct address
   * is the proxy's, so the forwarded list is read first - its first entry is the client.
   */
  private String address()
  {
    String forwarded=request.getHeader("X-Forwarded-For");
    if (forwarded!=null && forwarded.trim().length()>0)
    {
      int comma=forwarded.indexOf(',');
      return (comma>0?forwarded.substring(0,comma):forwarded).trim();
    }
    return request.getRemoteAddr();
  }

  /**
   * A short, human description of the device asking to sign in, so approving is a
   * decision rather than a reflex. The raw agent string is shown alongside it.
   */
  public String getDeviceName()
  {
    if (found==null)return "Unknown device";
    String agent=found.getUserAgent();
    if (agent.length()==0)return "Unknown device";
    String os="Unknown";
    if (agent.contains("Android"))os="Android";
    else if (agent.contains("iPhone"))os="iPhone";
    else if (agent.contains("iPad"))os="iPad";
    else if (agent.contains("Vision"))os="Vision Pro";
    else if (agent.contains("Mac OS X"))os="Mac";
    else if (agent.contains("SteamOS") || agent.contains("Steam Deck"))os="Steam Deck";
    else if (agent.contains("Windows"))os="Windows";
    else if (agent.contains("Linux"))os="Linux";
    String browser="browser";
    if (agent.contains("Edg/"))browser="Edge";
    else if (agent.contains("OPR/"))browser="Opera";
    else if (agent.contains("Firefox/"))browser="Firefox";
    else if (agent.contains("Chrome/"))browser="Chrome";
    else if (agent.contains("Safari/"))browser="Safari";
    return os+" · "+browser;
  }

  public String getDeviceAgent()
  {
    if (found==null)return "";
    String agent=found.getUserAgent();
    if (agent.length()>120)agent=agent.substring(0,120)+"...";
    return agent;
  }

  public String getDeviceAddress()
  {
    return found==null?"":found.getRemoteAddr();
  }
}
