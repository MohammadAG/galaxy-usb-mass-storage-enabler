package com.mohammadag.samsungusbmassstorageenabler;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.ads.AdRequest;
import com.google.ads.AdView;
import com.stericson.RootTools.RootTools;
import com.stericson.RootTools.execution.CommandCapture;

public class MainActivity extends Activity {
	
    private String _lunFilePath = null;
    private boolean s3cAvailable = true;
    private String _blockPath = "";
    private boolean _isCreatingShortcuts = false;
    private boolean _isUsingShortcut = false;
    private SharedPreferences mPreferences;
	
    private String[] listOfLunFiles = {
            "/sys/devices/platform/s3c-usbgadget/gadget/lun0/file",
            "/sys/devices/virtual/android_usb/android0/f_mass_storage/lun_ex/file",
            "/sys/devices/virtual/android_usb/android0/f_mass_storage/lun/file",
            "/sys/devices/virtual/android_usb/android0/f_mass_storage/lun0/file",
            "/sys/devices/platform/msm_hsusb/gadget/lun0/file" // Galaxy S4 (SCH-I545)
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
    
    private boolean runRootCommand(String... commandString) {
    	Log.d("SGUSB", "Executing command:" + commandString[0]);
    	CommandCapture command = new CommandCapture(0, commandString);
    	try {
			RootTools.getShell(true).add(command).waitForFinish();
		} catch (Exception e) {
			e.printStackTrace();
		}
    	return true;
    }
    
    public String readOutputFromCommand(String command) {
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
            	RootTools.offerBusyBox(MainActivity.this);
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
        
        // Don't slow down shortcut startup
        if (!_isUsingShortcut) {
            if (!RootTools.isRootAvailable()) {
        		AlertDialog.Builder alertDialog = new AlertDialog.Builder(MainActivity.this)
        		.setTitle(getString(R.string.error))
        		.setMessage(getString(R.string.error_root_not_available))
        		.setPositiveButton(R.string.dialog_text_quit, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog,int which) {
                    	dialog.dismiss();
                    	finish();
                    }
                });

                alertDialog.show();
            } else {
                if (!RootTools.isBusyboxAvailable()) {
                	showInstallBusybox();
                }
            }
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
    	
    	CommandCapture command = new CommandCapture(0,
    			"echo \"\" > " + _lunFilePath,
    			"setprop persist.sys.usb.config mtp,adb",
    			"vold");
    	try {
			RootTools.getShell(true).add(command).waitForFinish();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    
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
    	
    	String oldBlockPath = getBlockPath();
    	
    	final String blockPathVar = "/dev/block/vold/";
    	String blockId = readOutputFromCommand("busybox mountpoint -d /mnt/extSdCard/");
    	Log.d("SGUSB", blockId);
    	setBlockPath(blockPathVar + blockId);
    	
    	if (blockId.startsWith("0:")) {
    		if (oldBlockPath != null && oldBlockPath.isEmpty()) {
				AlertDialog.Builder alertDialog = new AlertDialog.Builder(MainActivity.this)
				.setTitle(getString(R.string.error))
				.setMessage(getString(R.string.error_getting_block_id_no_sd_card))
				.setPositiveButton(R.string.dialog_text_ok, new DialogInterface.OnClickListener() {
			        public void onClick(DialogInterface dialog,int which) {
			        	dialog.dismiss();
			        }
			    });
    
			    alertDialog.show();
			    return;
    		} else if (oldBlockPath != null && !oldBlockPath.isEmpty()) {
    			// Hide this so as not to confuse the user.
    			// createToast(getString(R.string.error_getting_block_id_retrying_old_one));
    			setBlockPath(oldBlockPath);
    		}
    	}
    	
    	// boolean unmount = runRootCommand("busybox umount /mnt/extSdCard/");
    	CommandCapture command = new CommandCapture(0, "busybox umount /mnt/extSdCard/");
    	try {
			RootTools.getShell(true).add(command).waitForFinish();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    	
    	try {
			if (command.exitCode() != 0) {
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
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
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
