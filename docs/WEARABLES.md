# Wearables and Sensors

Which devices carry which sensors, and how their readings reach Bios. The
tables were compiled from vendor specifications in early 2026; check the
vendor before relying on a cell. A sensor on a device does not mean the
reading reaches Bios: that depends on what the vendor's app writes to
Health Connect or exposes through its API.

## How readings reach Bios

| Path | Covers |
|---|---|
| Health Connect | Any app that writes there (watch and band companion apps, the handset pedometer) |
| Gadgetbridge | Bands and watches supported by Gadgetbridge, without the vendor app |
| Vendor API the owner signs into | Oura, WHOOP, Garmin, Withings, Dexcom, Polar, COROS |
| Phone sensors | Step counter, accelerometer, camera (fingertip PPG) |
| Bluetooth LE | An air-quality peripheral that speaks Environmental Sensing |
| Import | FHIR bundle, Apple Health ECG file |

## Devices and their sensors

### Smartwatches

| Device | HR | HRV | ECG | SpO2 | Temp | Blood Pressure | Sleep Stages | Accelerometer | GPS | Other |
|---|---|---|---|---|---|---|---|---|---|---|
| **Apple Watch Series 11** | Yes | Yes | Yes | Yes | Yes (wrist) | No | Yes | Yes | Yes | No Health Connect; ECG reaches Bios by file import |
| **Samsung Galaxy Watch 7 / Ultra** | Yes | Yes | Yes | Yes | Yes (skin) | Yes (calibration required) | Yes | Yes | Yes | Body composition (BIA) |
| **Google Pixel Watch 3** | Yes | Yes | Yes | Yes | Yes | No | Yes | Yes | Yes | |
| **Garmin Venu 3 / Fenix 8** | Yes | Yes | No | Yes | No | No | Yes | Yes | Yes (multi-band) | Respiration rate |
| **COROS / Kiprun GPS 500** (COROS M1D firmware) | Yes | Yes (nightly) | No | No | No | No | Yes | Yes | Yes | Resting HR; **connects only through the COROS app, see caveat below** |

#### COROS / Kiprun connection caveat (verified 2026-10-22)

The watch Bios is tested on daily is a Kiprun GPS 500 (Decathlon,
COROS M1D firmware, COROS app `com.yf.smart.coros.dist`). It does not
reach Bios directly, and **monitoring its HRV requires both the COROS app
and a COROS account**:

- **Getting data off the watch needs the COROS app.** The watch syncs
  only over Bluetooth to the COROS app. There is no app-free path: the
  watch's own BLE login is tied to the COROS account (a per-session
  credential the watch validates and that is not reproducible from the
  watch itself). The investigation and why it was stopped are in
  [specs/coros-direct-ble.md](specs/coros-direct-ble.md).
- **HRV and resting HR reach Bios through the COROS cloud, not Health
  Connect.** The COROS app writes steps, heart rate and sleep to Health
  Connect, but **not HRV** (it holds the permission and never uses it;
  stable across app updates). Bios reads nightly sleep HRV and resting HR
  from the COROS Training Hub web API (`CorosApiAdapter`), which is the
  COROS cloud. So HRV travels watch → COROS app → COROS cloud → Bios.
- **Steps, heart rate and sleep** reach Bios through the Health Connect
  mirror the COROS app writes, under a per-writer "COROS via Health
  Connect" source.

Net: to monitor HRV on this watch the owner must keep the COROS app
installed, signed in, and opened often enough to sync; the account must
stay active for the Training Hub path. The app need not be actively used
beyond syncing. Dropping COROS entirely for HRV means a different device —
one that writes HRV to Health Connect directly, or has an open or
Gadgetbridge-supported protocol. If a future COROS app version starts
writing HRV to Health Connect, the cloud dependency for HRV falls away
(the app would still be needed to sync); recheck after COROS app updates.

### Smart Rings

| Device | HR | HRV | ECG | SpO2 | Temp | Sleep Stages | Other |
|---|---|---|---|---|---|---|---|
| **Oura Ring Gen 4** | Yes | Yes | No | Yes | Yes | Yes | Vendor readiness score |
| **Samsung Galaxy Ring** | Yes | Yes | No | Yes | Yes | Yes | |
| **Ultrahuman Ring Air** | Yes | Yes | No | Yes | Yes | Yes | |

### Fitness Bands

| Device | HR | HRV | ECG | SpO2 | Temp | Sleep Stages | Other |
|---|---|---|---|---|---|---|---|
| **WHOOP 5.0 / MG** | Yes | Yes | Yes (MG) | Yes | Yes | Yes | Vendor strain and recovery scores, blood pressure (MG), respiratory rate |
| **Fitbit Charge 6** | Yes | Yes | Yes | Yes | No | Yes | EDA |
| **Xiaomi Smart Band 9 Pro** | Yes | Yes | No | Yes | No | Yes | |
| **Amazfit Band 7** | Yes | No | No | Yes | No | Yes | |

### Continuous Glucose Sensors

| Device | Type | Wear Time | Connectivity |
|---|---|---|---|
| **Dexcom G7** | Prescription | 15.5 days | Bluetooth, phone app |
| **Dexcom Stelo** | Over the counter | 15 days | Bluetooth, phone app |
| **Abbott FreeStyle Libre 3 Plus** | Prescription | 15 days | Bluetooth |
| **Abbott Lingo** | Over the counter | 14 days | Bluetooth, phone app |
| **Abbott Libre Rio** | Over the counter | 15 days | Bluetooth, phone app |

Bios has an adapter for the Dexcom API only.

### Other devices

| Device | Sensors |
|---|---|
| **Withings ScanWatch 2** | ECG, SpO2, temperature, respiratory rate |
| **Polar H10 / Verity Sense** | Chest-strap or arm-band heart rate and HRV |

### Phone built-in sensors

| Sensor | What Bios reads from it |
|---|---|
| **Step counter** | Steps |
| **Accelerometer** | Movement overnight, for the phone-sleep window |
| **Camera and flash** | Fingertip PPG: heart rate and HRV for one capture ([CAMERA_PPG.md](CAMERA_PPG.md)) |

Bios does not use the microphone or GPS.
