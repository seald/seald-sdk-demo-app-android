# Seald SDK demo app Android

This is a basic app, demonstrating use of the Seald SDK for Android.

You can check the reference documentation at <https://docs.seald.io/sdk/seald-sdk-android/>.

The main file you could be interested in reading is [`./app/src/main/java/io/seald/seald_sdk_demo_app_android/MainActivity.kt`](./app/src/main/java/io/seald/seald_sdk_demo_app_android/MainActivity.kt).

Also, it is recommended to create your own Seald team on <https://www.seald.io/create-sdk>,
and change the values of `APP_ID`, `JWT_SHARED_SECRET_ID`, and `JWT_SHARED_SECRET`, that you can get on the `SDK` tab
of the Seald dashboard settings, as well as `SSKS_BACKEND_APP_KEY` that you can get on the `SSKS` tab,
in `./app/src/main/java/io/seald/seald_sdk_demo_app_android/Credentials.kt`,
so that the example runs in your own Seald team.

Finally, to run from the CLI:

```bash
# Install
./gradlew installDebug

# Run
adb shell am start -n io.seald.seald_sdk_demo_app_android/io.seald.seald_sdk_demo_app_android.MainActivity

# Get logs
adb logcat
```
