# Masir Android build

Repo layout after you copy these files in:

    index.html            <- the web app (edit this one; CI copies it into the APK)
    icon.png              <- used as launcher icon if present
    android/              <- native shell (WebView + home-screen widget)
    .github/workflows/android.yml

Download the APK: GitHub -> Actions -> latest run -> Artifacts -> masir-apk.

## Keep your data between updates (recommended, one time)
Without a fixed signing key every build is signed differently, so Android refuses to update the old install and you must uninstall (losing data).

    keytool -genkeypair -v -keystore masir.jks -alias masir -keyalg RSA -keysize 2048 -validity 10000
    base64 -w0 masir.jks      # macOS: base64 -i masir.jks

Repo -> Settings -> Secrets and variables -> Actions -> New secret:
KS_B64 = (output of base64), KS_PASS = (the password), KS_ALIAS = masir.
Keep masir.jks somewhere safe. Never commit it.
