# Julia weather widget

Android home-screen widget: Julia's weather, rain verdict and what to wear. Same design and rules as the Home Assistant floorplan panel.

- Rules: `app/src/main/java/uk/zeffertt/juliaweather/Rules.kt` (port of HA automation `julia_weather_outfit`). Change a rule in HA and here.
- Forecasts: Met.no + Met Office DataHub (API key entered in the app, never in the code).
- Tests compare the rules with HA's own output for four sample days: `gradle testDebugUnitTest`.

## Getting the APK
Every push to `main` builds and tests the app, then attaches `julia-weather.apk` to the latest release (Releases, right-hand side). Install it over the old one - settings and the widget are kept.
