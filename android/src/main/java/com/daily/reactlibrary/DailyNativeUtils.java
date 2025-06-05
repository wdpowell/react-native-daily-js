package com.daily.reactlibrary;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Process;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.BaseActivityEventListener;
import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.facebook.react.modules.core.PermissionAwareActivity;
import com.facebook.react.modules.core.PermissionListener;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DailyNativeUtils extends ReactContextBaseJavaModule implements PermissionListener {

    private static final String TAG = DailyNativeUtils.class.getName();
    private static int PERMISSION_REQUEST_CODE = 666;

    private final ReactApplicationContext reactContext;
    private DeviceEventManagerModule.RCTDeviceEventEmitter eventEmitter;
    private Set<String> requestersKeepingDeviceAwake = new HashSet<>();
    private Set<String> requestersShowingOngoingMeetingNotification = new HashSet<>();

    // Info's received to crete the notification
    private String title;
    private String subtitle;
    private String iconName;

    public DailyNativeUtils(ReactApplicationContext reactContext) {
        super(reactContext);
        this.reactContext = reactContext;
        reactContext.addLifecycleEventListener(new LifecycleEventListener() {
            @Override
            public void onHostResume() {
            }

            @Override
            public void onHostPause() {
            }

            @Override
            public void onHostDestroy() {
                eventEmitter.emit("EventOnHostDestroy", Arguments.createMap());
                DailyOngoingMeetingForegroundService.stop(reactContext);
            }
        });
    }

    @Override
    public void initialize() {
        this.eventEmitter = reactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class);
    }

    @Override
    public String getName() {
        return "DailyNativeUtils";
    }

    @ReactMethod
    public void setKeepDeviceAwake(boolean keepDeviceAwake, String requesterId) {
        Activity activity = getCurrentActivity();
        if (activity != null) {
            activity.runOnUiThread(() -> {
                if (keepDeviceAwake) {
                    requestersKeepingDeviceAwake.add(requesterId);
                } else {
                    requestersKeepingDeviceAwake.remove(requesterId);
                }
                updateKeepScreenOnFlag(activity);
            });
        }
    }

    // Note: notification properties (e.g. title) can't be changed while it is ongoing.
    @ReactMethod
    public void setShowOngoingMeetingNotification(boolean showOngoingMeetingNotification, String title, String subtitle, String iconName, String requesterId) {
        Activity activity = getCurrentActivity();
        if (activity != null) {
            activity.runOnUiThread(() -> {
                if (showOngoingMeetingNotification) {
                    requestersShowingOngoingMeetingNotification.add(requesterId);
                } else {
                    requestersShowingOngoingMeetingNotification.remove(requesterId);
                }
                this.title = title;
                this.subtitle = subtitle;
                this.iconName = iconName;
                updateOngoingMeetingForegroundService();
            });
        }
    }

    private void updateKeepScreenOnFlag(Activity activity) {
        Window window = activity.getWindow();
        if (requestersKeepingDeviceAwake.size() > 0) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    @Override
    public boolean onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == PERMISSION_REQUEST_CODE &&
                Arrays.stream(grantResults).allMatch(result -> result == PackageManager.PERMISSION_GRANTED)) {
            this.initializeOngoingMeetingForegroundService();
        } else {
            System.err.println("Failed to grant permissions.");
        }
        return true;
    }

    private List<String> getDeclaredPermissions(Context context) {
        List<String> declaredPermissions = new ArrayList<>();
        try {
            PackageInfo packageInfo = context.getPackageManager().getPackageInfo(
                    context.getPackageName(),
                    PackageManager.GET_PERMISSIONS
            );
            // Check if permissions are requested
            if (packageInfo.requestedPermissions != null) {
                declaredPermissions = Arrays.asList(packageInfo.requestedPermissions);
            }
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        return declaredPermissions;
    }

    private void checkPermissions() {
        Context applicationContext = this.reactContext.getApplicationContext();
        List<String> declaredPermissions = this.getDeclaredPermissions(applicationContext);
        List<String> permissionList = new ArrayList<String>();
        if (declaredPermissions.contains(Manifest.permission.FOREGROUND_SERVICE_CAMERA)) {
            permissionList.add(Manifest.permission.CAMERA);
        }
        if (declaredPermissions.contains(Manifest.permission.FOREGROUND_SERVICE_MICROPHONE)) {
            permissionList.add(Manifest.permission.RECORD_AUDIO);
        }
        if (Build.VERSION.SDK_INT >= 33) {
            permissionList.add(Manifest.permission.POST_NOTIFICATIONS);
        }

        boolean allPermissionsGranted = true;
        for (String permission : permissionList) {
            if (ContextCompat.checkSelfPermission(this.reactContext.getApplicationContext(), permission) != PackageManager.PERMISSION_GRANTED) {
                allPermissionsGranted = false;
                break;
            }
        }

        if (!allPermissionsGranted) {
            Activity currentActivity = this.reactContext.getCurrentActivity();
            if (currentActivity instanceof PermissionAwareActivity) {
                PermissionAwareActivity permissionAware = (PermissionAwareActivity) currentActivity;
                permissionAware.requestPermissions(permissionList.toArray(new String[0]), PERMISSION_REQUEST_CODE, this);
            }
        } else {
            // Permissions are already granted, we can initialize
            initializeOngoingMeetingForegroundService();
        }
    }

    private void initializeOngoingMeetingForegroundService(){
        Activity activity = getCurrentActivity();
        if (activity != null) {
            activity.runOnUiThread(() -> {
                DailyOngoingMeetingForegroundService.start(activity.getClass(), title, subtitle, iconName, reactContext);
            });
        }
    }

    private void updateOngoingMeetingForegroundService() {
        if (!requestersShowingOngoingMeetingNotification.isEmpty()) {
            this.checkPermissions();
        } else {
            DailyOngoingMeetingForegroundService.stop(reactContext);
        }
    }

    @ReactMethod
    public void presentSystemScreenCapturePrompt() {
        Log.d(TAG, "presentSystemScreenCapturePrompt is not available on Android");
    }

    @ReactMethod
    public void requestStopSystemScreenCapture() {
        Log.d(TAG, "requestStopSystemScreenCapture is not available on Android");
    }

    @ReactMethod
    public void isScreenBeingCaptured(Promise promise) {
        Log.d(TAG, "isScreenBeingCaptured is not available on Android");
        promise.resolve(false);
    }
}
