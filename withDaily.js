const { withPlugins, withAppBuildGradle, withAndroidManifest, AndroidConfig, createRunOncePlugin } = require('@expo/config-plugins');

// Optional: Import your iOS plugins if you have them

const withAppBuildGradleModified = (config) => {
  return withAppBuildGradle(config, async (file) => {
    const modResults = file.modResults;
    modResults.contents = modResults.contents + '\nandroid.packagingOptions.jniLibs.useLegacyPackaging = true\n';
    return file;
  });
};

const withDaily = (config, props = {}) => {
  const { enableScreenShare = false } = props;
  // Fixing the issue from Expo with Hermes
  config = withAppBuildGradleModified(config);
  // Android permissions
  config = AndroidConfig.Permissions.withPermissions(config, [
    'android.permission.FOREGROUND_SERVICE',
    'android.permission.FOREGROUND_SERVICE_CAMERA',
    'android.permission.FOREGROUND_SERVICE_MICROPHONE',
    'android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION',
    'android.permission.POST_NOTIFICATIONS',
  ]);
  config = withAndroidManifest(config, (config) => {
    const application = AndroidConfig.Manifest.getMainApplication(config.modResults);
    if (application && !application?.service) {
      application.service = [];
    }
    application?.service?.push({
      $: {
        'android:name': 'com.daily.reactlibrary.DailyOngoingMeetingForegroundService',
        'android:exported': 'false',
        'android:foregroundServiceType': 'camera|microphone',
      },
    });
    return config;
  });
  // iOS plugin chaining (add your custom plugins if needed)
  // if (enableScreenShare) {
  //   config = withPlugins(config, [withIosScreenCapture, withIosBroadcastExtension]);
  // }
  return config;
};

module.exports = createRunOncePlugin(withDaily, 'withDaily', '0.0.1');
