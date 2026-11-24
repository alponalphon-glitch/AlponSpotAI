# NEXORA Android 1.5.0 — дастури сохтмон

## Диапазони Android
- `minSdk = 26`: Android 8.0 ва болотар.
- `compileSdk = 36`: SDK-и Android 16 барои компилятсия.
- `targetSdk = 35`: рафтори Android 15; барои пешгирӣ аз тағйироти рафтории target API 36.
- `minSdk = 26`: насб аз Android 8.0 боло.
- JDK 17, Android Gradle Plugin 8.9.4, Gradle 8.11.1.

## Чӣ нигоҳ дошта шудааст
- Модули UI-и Jetpack Compose ва ViewModel-и мавҷуда.
- Binance Spot ва Binance Futures (таҳлилӣ; иҷрои фармоиши воқеӣ илова нашудааст).
- Forex provider-и мавҷуда.
- 46 муҳаррики домении AI/таҳлилӣ.
- Графикҳо, таърихи OHLCV, scanner, backtesting ва paper trading-и мавҷуда.
- Room database ва migration-и мавҷуда.

## Сохтмон дар Android Studio
1. Android Studio-и мувофиқро кушоед.
2. SDK Manager → SDK Platforms: Android 16 / API 36-ро насб кунед.
3. SDK Tools: Android SDK Build-Tools 36.0.0-ро насб кунед.
4. JDK 17 интихоб кунед.
5. Лоиҳаи `AlponSpotAI`-ро кушоед ва Gradle Sync кунед.
6. Барои APK-и санҷишӣ: `:app:assembleDebug`.
7. Барои APK-и release-и бе имзо: `:app:assembleRelease`.

## Сохтмон аз терминал
Лоиҳа Gradle Wrapper-и дохилшуда надорад; Gradle 8.11.1 бояд дар система насб бошад:

```bash
gradle --version
gradle :app:lintDebug
gradle :app:assembleDebug
gradle :app:assembleRelease
```

APK-ҳо дар `app/build/outputs/apk/` ҷойгир мешаванд.

## Муҳим
- APK-и release-и ин баста unsigned аст; барои паҳнкунӣ бояд бо калиди худи таҳиягар имзо шавад.
- Лоиҳа `minSdk 26` дорад: дастгирии насб аз Android 8.0 боло. Ин кафолати санҷиши воқеӣ дар ҳар дастгоҳ нест.
- CI барои сохтани Debug APK дар GitHub Actions танзим шудааст; натиҷаи ниҳоӣ танҳо пас аз иҷрои workflow маълум мешавад.
- Ин барнома воситаи таҳлил аст; натиҷаи AI кафолати фоида нест ва иҷрои фармоиши воқеӣ фаъол карда нашудааст.
