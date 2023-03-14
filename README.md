# Seald SDK demo app Android

This is a basic app, demonstrating use of the Seald SDK for Android.

The main file you could be interested in reading is `app/src/main/java/io/seald/seald_sdk_demo_app_android/MainActivity.kt`.

Before running the app, you have to download the files `seald-sdk-internals-xxx.aar` and `seald-sdk-xxx.aar`,
and place them in the `app/libs` directory (which you should create).
Check the `app/build.gradle` file for up-to-date version numbers and download URLs.

Also, it is recommended to create your own Seald team on [https://www.seald.io/create-sdk](https://www.seald.io/create-sdk),
and change the values of `appId`, `JWTSharedSecretId`, and `JWTSharedSecret`
at the start of `app/src/main/java/io/seald/seald_sdk_demo_app_android/MainActivity.kt`,
so that the example runs in your own Seald team.
