WorldTemps 1.0.0

Bundled-runtime package (recommended):
  Windows: open WorldTemps\WorldTemps.exe
  macOS: open WorldTemps.app
  Linux: run WorldTemps/bin/WorldTemps
  Java and Maven are not required. Use the package for your OS and architecture.
  Linux requires GTK 3 / X11 desktop libraries (Ubuntu: libgtk-3-0t64 libxtst6).

Portable package (requires Java 17+, but not Maven):
  Windows: run start-worldtemps.cmd
  macOS/Linux: run ./start-worldtemps.sh

The app opens city search. No API key is needed for the free noncommercial
Open-Meteo endpoint. Choose up to three locations and historical dates, or
cancel and choose Offline demo. CSV import/export is also available.

Offline startup: append --demo to a launcher command.
Open a CSV directly: append the CSV path as one quoted argument.

Historical data is Open-Meteo / ECMWF ERA5 reanalysis, CC BY 4.0,
not live forecasts or direct station records. Location search uses GeoNames.
https://open-meteo.com/en/docs/historical-weather-api
https://open-meteo.com/en/docs/geocoding-api
https://www.geonames.org/
https://creativecommons.org/licenses/by/4.0/

Weather cache: ~/.worldtemps/weather-cache. Source details identify cached and
offline entries. The demo is synthetic and labeled accordingly.

Packages are unsigned. Operating-system signing/notarization is not included.
See the repository README and docs/release.md for build, provenance, and checksums.
https://github.com/AdrianPinson/WorldTemps
