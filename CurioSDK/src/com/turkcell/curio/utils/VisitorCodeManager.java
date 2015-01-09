package com.turkcell.curio.utils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.UUID;

import android.content.Context;

/**
 * !!Legacy class from 8digits API. May be Changed.!!
 * 
 * @Changed Can Ciloglu
 *
 */
public class VisitorCodeManager {
  private static String sID = null;
  private static final String INSTALLATION = "INSTALLATION";
private static final String TAG = "VisitorCodeManager";

  public synchronized static String id(String trackingCode, Context context) {
      if (sID == null) {  
          File installation = new File(context.getFilesDir(), INSTALLATION + "-" + trackingCode);
          try {
              if (!installation.exists())
                  writeInstallationFile(installation);
              sID = readInstallationFile(installation);
          } catch (Exception e) {
              throw new RuntimeException(e);
          }
      } else {
    	  CurioLogger.d(TAG, "Curio installation file exists.");
      }
      return sID;
  }

  private static String readInstallationFile(File installation) throws IOException {
      RandomAccessFile f = new RandomAccessFile(installation, "r");
      byte[] bytes = new byte[(int) f.length()];
      f.readFully(bytes);
      f.close();
      return new String(bytes);
  }

  private static void writeInstallationFile(File installation) throws IOException {
      FileOutputStream out = new FileOutputStream(installation);
      String id = UUID.randomUUID().toString();
      out.write(id.getBytes());
      out.close();
  }
}
