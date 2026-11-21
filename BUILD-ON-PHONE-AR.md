# استخراج APK من الهاتف عبر GitHub

1. أنشئ حسابًا أو سجّل الدخول إلى https://github.com من المتصفح.
2. أنشئ مستودعًا جديدًا (New repository) باسم `winamp-reborn` واجعله Private إن رغبت.
3. فك ضغط ملف WINAMP_REBORN_ANDROID.zip على الهاتف، ثم ارفع **محتويات مجلد winamp-reborn** إلى المستودع مع الحفاظ على المجلدات، ومنها `.github/workflows/android-apk.yml`.
4. افتح تبويب Actions داخل المستودع. اختر `Build WINAMP REBORN APK` ثم `Run workflow`.
5. انتظر انتهاء البناء. إذا ظهر أخضر، افتح نتيجة التشغيل وانزل إلى Artifacts ثم نزّل `WINAMP-REBORN-debug-apk`.
6. فك ضغط الملف الناتج، واضغط `app-debug.apk` وثبّته. قد يطلب Android السماح بالتثبيت من هذا المصدر.

إذا فشل البناء، افتح سجل التشغيل وانسخ رسالة الخطأ كاملة؛ لا تثبّت APK قبل نجاح البناء.
