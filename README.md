# PulseCare – ECG & Health Monitor (installable app)

Static PWA – no build step. Upload these files to the root of the GitHub Pages repo (replacing the old `index.html`).

- `index.html` – the app (Home · Pulse · ECG · BP · History, plus Report)
- `manifest.webmanifest`, `sw.js` – install to home screen + offline
- `icons/` – app icons

Earlier readings saved by v1 (localStorage key `mobileEcgVitalsHistoryV1`) carry over automatically.
