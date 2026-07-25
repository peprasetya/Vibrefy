package id.prasetya.vibrefy.tools;

import java.security.SecureRandom;

public class Random
{
  private static java.util.Random random=null;

  private static void init()
  {
    if (random==null) random=new SecureRandom();
  }

  public static String getAlphanumeric(int length)
  {
    String rst="";
    init();
    for (int i=0;i<length;i++)
    {
      int rnd=random.nextInt(36)+48;
      if (rnd>57) rnd+=7;
      rst+=(char)rnd;
    }
    return rst;
  }

  public static String getNumeric(int length)
  {
    String rst="";
    init();
    for (int i=0;i<length;i++)
    {
      int rnd=random.nextInt(10)+48;
      rst+=(char)rnd;
    }
    return rst;
  }

  public static java.util.Random getRandomizer()
  {
    init();
    return random;
  }
}
