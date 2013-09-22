package com.mohammadag.samsungusbmassstorageenabler;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;

import android.content.Context;
import android.os.Build;
import android.util.Log;
import android.widget.Toast;

import com.stericson.RootTools.RootTools;
import com.stericson.RootTools.execution.CommandCapture;

public class Utils {

    public static String readOutputFromCommand(String command) {
    	StringBuffer theRun = null;
    	try {
    	    Process process = Runtime.getRuntime().exec(command);

    	    BufferedReader reader = new BufferedReader(
    	            new InputStreamReader(process.getInputStream()));
    	    int read;
    	    char[] buffer = new char[4096];
    	    StringBuffer output = new StringBuffer();
    	    while ((read = reader.read(buffer)) > 0) {
    	        theRun = output.append(buffer, 0, read);
    	    }
    	    reader.close();
    	    process.waitFor();
    	    process.destroy();

    	} catch (Exception e) {
    	    e.printStackTrace();
    	}
    	
    	if (theRun != null) {
    	    return theRun.toString().trim();
    	} else {
    		return "";
    	}
    }
    
    public static boolean runRootCommand(String... commandString) {
    	Log.d(Common.TAG, "Executing command:" + commandString[0]);
    	CommandCapture command = new CommandCapture(0, commandString);
    	try {
			RootTools.getShell(true).add(command).waitForFinish();
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
    	return true;
    }
    
	public static void createToast(String text, Context context) {
		Toast.makeText(context, text, Toast.LENGTH_LONG).show();
	}
	
	public static void createToast(int resId, Context context) {
		createToast(context.getString(resId), context);
	}
	
	public static String getStringFromException(Exception e) {
		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw);
		e.printStackTrace(pw);
		return sw.toString();
	}
	
	public static String getDeviceName() {
		String manufacturer = Build.MANUFACTURER;
		String model = Build.MODEL;
		if (model.startsWith(manufacturer)) {
			return capitalize(model);
		} else {
			return capitalize(manufacturer) + " " + model;
		}
	}


	private static String capitalize(String s) {
		if (s == null || s.length() == 0) {
			return "";
		}
		char first = s.charAt(0);
		if (Character.isUpperCase(first)) {
			return s;
		} else {
			return Character.toUpperCase(first) + s.substring(1);
		}
	}
	
	public static boolean isMtpActivated() {
		String config = Utils.readOutputFromCommand("getprop persist.sys.usb.config");
		return config.contains("mtp");
	}

	public static boolean isUmsActivated() {
		String config = Utils.readOutputFromCommand("getprop persist.sys.usb.config");
		return config.contains("mass_storage");
	}
	
	public static boolean isFileEmpty(String pathToFile) {
		int size = getFileSize(pathToFile);
		// 0 length or 4096 bytes.
		return size == 0 || size == 4096;
	}
	
	public static int getFileSize(String pathToFile) {
		File file = new File(pathToFile);
		if (file.canRead()) {
			try {
				String contents = readFileAsString(pathToFile);
				return contents.length();
			} catch (IOException e) {
				
			}
		}
		String lunContents = Utils.readOutputFromCommand("cat " + pathToFile);
		return lunContents.length();
	}
	
	private static String readFileAsString(String filePath) throws IOException {
		StringBuffer fileData = new StringBuffer();
		BufferedReader reader = new BufferedReader(new FileReader(filePath));
		char[] buf = new char[1024];
		int numRead = 0;
		if (reader != null) {
			while ((numRead = reader.read(buf)) != -1) {
				String readData = String.valueOf(buf, 0, numRead);
				fileData.append(readData);
			}
		}
		reader.close();
		return fileData.toString();
    }
}
