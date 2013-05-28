package com.mohammadag.samsungusbmassstorageenabler;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

import com.google.ads.AdRequest;
import com.google.ads.AdView;

import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
	
	private String _lunFilePath = null;
	private boolean s3cAvailable = true;
	private String _blockPath;
	private boolean _isCreatingShortcuts = false;
	private boolean _isUsingShortcut = false;
	private SharedPreferences mPreferences;
	
    private String[] listOfLunFiles = {
            "/sys/devices/platform/s3c-usbgadget/gadget/lun0/file",
            "/sys/devices/virtual/android_usb/android0/f_mass_storage/lun_ex/file",
            "/sys/devices/virtual/android_usb/android0/f_mass_storage/lun/file",
            "/sys/devices/virtual/android_usb/android0/f_mass_storage/lun0/file"
    };
	
    public void createToast(String text) {
    	Toast.makeText(getApplicationContext(), text, Toast.LENGTH_LONG).show();
    }
    
    public void testIfs3cExists() {
    	for (String lunFilePath: listOfLunFiles) {
    		File file = new File(lunFilePath);
    		if (file.exists()) {
    			_lunFilePath = lunFilePath;
    			Log.d("UMSEnabler", "Found lun file: " + lunFilePath);
    			setS3cAvailable(true);
    			return;
    		}
    	}
    	
    	setS3cAvailable(false);
    	_lunFilePath = "";
    }
    
    private boolean runRootCommand(String command) {
        Process process = null;
        DataOutputStream os = null;
        try {
        	process = Runtime.getRuntime().exec("su");
        	os = new DataOutputStream(process.getOutputStream());
        	os.writeBytes(command+"\n");
        	os.writeBytes("exit\n");
        	os.flush();
        	process.waitFor();
        	if (process.exitValue() != 0) {
        		return false;
        	}
        } catch (Exception e) {
        	Log.d("*** DEBUG ***", "Unexpected error - Here is what I know: "+e.getMessage());
        	
        	if (command.contains("busybox"))
        		showInstallBusybox();
        	
        	return false;
        }
        finally {
        	try {
        		if (os != null) {
        			os.close();
        		}
        		process.destroy();
        	} catch (Exception e) {
        		e.printStackTrace();
        	}
        }
        return true;
    }
    
    public String readOutputFromCommand(String command) {
    	StringBuffer theRun = null;
    	boolean shouldShowError = false;
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

    	} catch (IOException e) {
    		shouldShowError = true;
    	    e.printStackTrace();
    	} catch (InterruptedException e) {
    		shouldShowError = true;
    	    e.printStackTrace();
    	} catch (RuntimeException e) {
    		shouldShowError = true;
    	}
    	if (shouldShowError)
    		showInstallBusybox();
    	
    	if (theRun != null)
    	    return theRun.toString().trim();
    	else
    		return "";
    }
    
    private void showInstallBusybox() {
    	AlertDialog.Builder alertDialog =
				new AlertDialog.Builder(MainActivity.this)
    	.setTitle(getString(R.string.error))
    	.setMessage(getString(R.string.is_busybox_installed))
    	.setPositiveButton(R.string.dialog_text_yes, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
            	Toast.makeText(getApplicationContext(), R.string.failed_to_run_command, Toast.LENGTH_LONG).show();
            }
        })
        .setNegativeButton(R.string.dialog_text_no, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(Uri.parse("market://details?id=stericson.busybox"));
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NO_ANIMATION);

                startActivity(intent);
            	return;
            }
        });
		
        alertDialog.show();
	}

	public boolean isMtpActivated() {
    	String config = readOutputFromCommand("getprop persist.sys.usb.config");
    	return config.contains("mtp");
    }
    
    public boolean isUmsActivated() {
    	String config = readOutputFromCommand("getprop persist.sys.usb.config");
    	return config.contains("mass_storage");
    }
    
    public String getStateText() {
    	String text = getString(R.string.current_state) + " ";
    	
    	if (isMtpActivated())
    		text = text + "MTP";
    	else if (isUmsActivated())
    		text = text + "Mass Storage";
    	
    	if (!isS3cAvailable()) {
    		text = text + " - " + getString(R.string.incompatible_kernel);
    	}
    	return text;
    }
    
    public void refreshState() {
    	TextView view = (TextView)findViewById(R.id.stateTextView);
    	view.setText(getStateText());
    }
    
    private void createShortcutForUSBMode(String mode) {
    	String shortcutName = null;
    	
    	int suffixText = 0;
    	if (mode.equals("UMS"))
    		suffixText = R.string.on_text;
    	else if (mode.equals("MTP"))
    		suffixText = R.string.off_text;
    	
    	shortcutName = String.format(getString(R.string.ums_shorthand), getString(suffixText));
    	Parcelable iconResource = Intent.ShortcutIconResource.fromContext(this, R.drawable.ic_launcher);
		
    	Intent intent = new Intent(this, MainActivity.class);
    	intent.putExtra("USBMode", mode);
    	intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		
    	Intent shortcutIntent = new Intent();
    	shortcutIntent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, intent);
    	shortcutIntent.putExtra(Intent.EXTRA_SHORTCUT_NAME, shortcutName);
    	shortcutIntent.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, iconResource);
    	setResult(RESULT_OK, shortcutIntent);
    	finish();
    }
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        mPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        boolean enableAds = mPreferences.getBoolean("enable_ads", true);
        
        testIfs3cExists();
        
        if (!isS3cAvailable()) {
        	Button mtpButton = (Button)findViewById(R.id.mtpButton);
        	Button umsButton = (Button)findViewById(R.id.umsButton);
        	mtpButton.setEnabled(false);
        	umsButton.setEnabled(false);
        }
        
        refreshState();
        
        toggleAds(enableAds);
        
        Intent intent = getIntent();
        String action = intent.getAction();
        Bundle extras = intent.getExtras();
        
        if (action != null && action.equals("android.intent.action.CREATE_SHORTCUT")) {
        	_isCreatingShortcuts = true;
        }
        	
		if (extras != null && extras.containsKey("USBMode")) {
			_isUsingShortcut = true;
			String USBMode = extras.getString("USBMode");
			if (USBMode.equals("UMS"))
				onUMSButtonClicked(null);
			else if (USBMode.equals("MTP"))
				onMTPButtonClicked(null);
		}
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_main, menu);
        
        MenuItem item = menu.findItem(R.id.enable_ads);
        item.setChecked(mPreferences.getBoolean("enable_ads", true));
        
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.about:
                showAbout();
                return true;
            case R.id.force_remount:
            	forceRemount();
            	return true;
            case R.id.menu_donate:
    			Intent intent = new Intent(Intent.ACTION_VIEW, 
    					Uri.parse("https://www.paypal.com/cgi-bin/webscr?cmd=_s-xclick&hosted_button_id=N2ZF2MJFAVFUA"));
    			startActivity(intent);
            	return true;
            case R.id.enable_ads:
            	toggleAds(!item.isChecked());
            	mPreferences.edit().putBoolean("enable_ads", !item.isChecked()).commit();
            	item.setChecked(!item.isChecked());
            	return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }
    
    private void forceRemount() {
    	runRootCommand("vold");
	}

	public void activateMTP() {
    	if (isMtpActivated()) {
    		runRootCommand("setprop persist.sys.usb.config mtp,adb");
    		createToast(String.format(getString(R.string.already_in), getString(R.string.mtp_mode)));
    		return;
    	}
    	runRootCommand("echo \"\" > " + _lunFilePath + "\nsetprop persist.sys.usb.config mtp,adb\nvold");
    	createToast(getString(R.string.mtp_success));
    	
    	refreshState();
    	
    	if (_isUsingShortcut)
    		finish();
    }
    
    public void onMTPButtonClicked(View v) {
    	if (_isCreatingShortcuts) {
    		createShortcutForUSBMode("MTP");
    		return;
    	}
    	String lunContents = readOutputFromCommand("cat " + _lunFilePath);
    	if (lunContents.length() > 1) {
    		AlertDialog.Builder alertDialog = new AlertDialog.Builder(MainActivity.this)
    		.setTitle(getString(R.string.confirm_unmount_title))
    		.setMessage(getString(R.string.confirm_unmount))
    		.setPositiveButton(R.string.dialog_text_yes, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog,int which) {
                	activateMTP();
                	dialog.dismiss();
                }
            })
            .setNegativeButton(R.string.dialog_text_no, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                	return;
                }
            });

            alertDialog.show();
    	} else {
    		activateMTP();
    	}
    }
    
    public void activateUms() {
    	runRootCommand("setprop persist.sys.usb.config mass_storage,adb");
    	runRootCommand("echo " + getBlockPath() + " > " + _lunFilePath);
    	createToast(getString(R.string.ums_success));
    	refreshState();
    	
    	if (_isUsingShortcut)
    		finish();
    }
    
    public void onUMSButtonClicked(View v) {
    	if (_isCreatingShortcuts) {
    		createShortcutForUSBMode("UMS");
    		return;
    	}
    	String lunContents = readOutputFromCommand("cat " + _lunFilePath);
    	if (lunContents.length() > 1) {
    		if (isUmsActivated()) {
    			createToast(String.format(getString(R.string.already_in), getString(R.string.ums_mode)));
    			return;
    		}
    	}
    	
    	String blockPathVar = "/dev/block/vold/" + readOutputFromCommand("busybox mountpoint -d /mnt/extSdCard/");
    	setBlockPath(blockPathVar);
    	boolean unmount = runRootCommand("busybox umount /mnt/extSdCard/");
    	if (!unmount) {
    		AlertDialog.Builder alertDialog = new AlertDialog.Builder(MainActivity.this)
    		.setTitle(getString(R.string.force_unmount_title))
    		.setMessage(getString(R.string.force_unmount_body))
    		.setPositiveButton(R.string.dialog_text_yes, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog,int which) {
                	runRootCommand("busybox umount -l /mnt/extSdCard/");
                	activateUms();
                }
            })
 
            .setNegativeButton(R.string.dialog_text_no, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                	return;
                }
            });
     
            alertDialog.show();
    	} else {
    		activateUms();
    	}
    }

	public boolean isS3cAvailable() {
		return s3cAvailable;
	}

	public void setS3cAvailable(boolean s3cAvailable) {
		this.s3cAvailable = s3cAvailable;
	}

	public String getBlockPath() {
		return _blockPath;
	}

	public void setBlockPath(String blockPath) {
		_blockPath = blockPath;
	}
	
	public void showAbout() {
		AlertDialog.Builder alertDialog = new AlertDialog.Builder(MainActivity.this)
		.setTitle(R.string.about_dialog_title)
		.setMessage(R.string.about_text);
		
        alertDialog.show();
	}
	
	private void toggleAds(boolean enable) {
		if (enable) {
			showAd();
		} else {
			hideAd();
		}
	}
	 
	private void showAd() {
		final AdView adLayout = (AdView) findViewById(R.id.adView);
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				adLayout.setEnabled(true);
				adLayout.setVisibility(View.VISIBLE);
				adLayout.loadAd(new AdRequest());
			}
		});
	}
	 
	 
	private void hideAd() {
		final AdView adLayout = (AdView) findViewById(R.id.adView);
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				adLayout.setEnabled(false);
				adLayout.setVisibility(View.GONE);
			}
		});
	}
}
