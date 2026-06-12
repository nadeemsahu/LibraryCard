# LibraryNFC (Modernized Edition)

A modernized Android project emulating NXP's MIFARE DESFire-based library ID cards using Host Card Emulation (HCE). 

This repository is an independently modernized architectural fork of the original `LibraryNFC` project. The core NFC APDU transceive logic has been retained from the original author, while the Android application layers have been **completely redesigned**.

## Modern Architecture Updates (Phase 2-15)
- **Dependency Injection:** Fully migrated to **Hilt / Dagger** (`@HiltAndroidApp`, `@Inject`), removing legacy tight coupling.
- **Reactive State:** Converted synchronous Java view models to fully reactive **Kotlin Coroutines and StateFlow**.
- **Domain Abstraction:** Created standard `Authenticator` interfaces for decoupled biometric and PIN verification logic.
- **UI & UX:** Implemented a ground-up redesign featuring a modern Glassmorphism design system, gradient vector assets, and enhanced navigation flows.
- **Quality Assurance:** Introduced `kotlinx-coroutines-test` and mockito tests for APDU parsers and ViewModel streams.

---

## Original Modules

### LibraryHCE
This module offers emulation of a contactless library ID card. Emulation uses native MIFARE DESFire command set. The DESFire protocol implemented here is [this reverse engineered version](https://github.com/revk/DESFireAES/blob/master/DESFire.pdf). 

[See the list of supported commands.](nfc/src/main/java/com/piotrekwitkowski/nfc/desfire/Commands.java)


## Configuration

### HCE AID
The "Android" AID used by both HCE and Reader Android applications can be configured in the [strings.xml](nfc/src/main/res/values/strings.xml) file of the nfc helper library.

### Emulated library ID
The data (Application AID, AES key, Data Files) of the emulated DESFire Application can be configured within the HCE application module logic.


## Deployment
To deploy the applications, two NFC-capable Android phones are needed. Originally tested with a Motorola One (Emulator) and Nexus 4 (Reader). You can use [CTSVerifier](https://source.android.com/compatibility/cts/verifier) to test your phone's NFC capabilities.

## Issues & Acknowledgements
Feel free to open Issues if you need clarification or help with NFC. A major thanks to the original author (Piotr Witkowski) and all others that provided resources for the core NFC APDU frameworks. 

Please consider starring the repo if you like the modernized architecture!