package com.mohammadag.samsungusbmassstorageenabler;

public class Common {
	public static final String TAG = "SG USB Mass Storage Enabler";
	public static final String DEV_BLOCK_PATH = "/dev/block/vold/";
	public static final String SETTINGS_KEY_ENABLE_ADS = "enable_ads";
	
	public static final String[] LIST_OF_LUN_FILES = {
			"/sys/devices/platform/s3c-usbgadget/gadget/lun0/file",
			"/sys/devices/virtual/android_usb/android0/f_mass_storage/lun_ex/file",
			"/sys/devices/virtual/android_usb/android0/f_mass_storage/lun/file",
			"/sys/devices/virtual/android_usb/android0/f_mass_storage/lun0/file",
			"/sys/devices/platform/msm_hsusb/gadget/lun0/file" // Galaxy S4 (SCH-I545)
	};
	
	public static final boolean DEBUG_BUILD = false;
	
}
