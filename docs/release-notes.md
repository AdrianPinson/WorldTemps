# WorldTemps 1.0.0

WorldTemps is a JavaFX desktop application for exploring and comparing historical
temperature data.

- Search for cities and load Open-Meteo ERA5 daily mean temperatures without an API key.
- Compare up to three locations with date filters and daily, monthly, or yearly means.
- Inspect raw readings, summary cards, chart tooltips, and data-source details.
- Import/export CSV and use the labeled synthetic demo without a network connection.
- Reuse validated cached weather responses, with explicit labels for offline recovery.
- Query chronological and location indexes, with a reproducible performance report.

Choose the bundled ZIP matching your OS and architecture to run without installing
Java or Maven. Portable ZIPs require Java 17+. See `README.txt` inside the download.
Packages are unsigned; Linux requires GTK 3/X11 libraries.

Weather data is reanalysis from Open-Meteo / ECMWF ERA5 (CC BY 4.0), not current
forecasts or direct station records. Location search uses GeoNames. The app shows
source and cache provenance; API archive loads are limited to three cities and
3,660 days, through six days before the current UTC date.
