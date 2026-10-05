# Phone by Google UX Redesign & Feature Completion Plan

Redesign the AI Call Translator app interface to match the modern Android **Phone by Google** UX and Material 3 design language, while fully integrating and preserving real WebRTC calling, real contacts, real call history, real favorites, real settings, and our unique AI Live Translation capability (STT -> Gemini -> TTS).

## User Review Required

> [!IMPORTANT]
> - **Design Alignment**: UI will mirror Google Phone UX (rounded search bar, bottom navigation / tab bar with Recents/Favorites/Contacts/Keypad, large dial pad with real suggested contacts, modern call screens, clean Material 3 dark/light themes).
> - **Zero Hardcoding**: All contacts, favorites, call logs, and search queries will use real device contacts / Firestore users / Room DB.
> - **AI Translation & WebRTC**: Preserving existing WebRTC signaling, STT (`SpeechRecognitionManager`), TTS (`TextToSpeechManager`), and Gemini backend integration without duplication.

## Proposed Changes

### Design System & Tokens
#### [MODIFY] [Theme.kt](file:///C:/Users/acer/OneDrive/Documents/project/AI_Call_Translator/app/src/main/java/com/example/calltranslator/ui/theme/Theme.kt) & [Color.kt](file:///C:/Users/acer/OneDrive/Documents/project/AI_Call_Translator/app/src/main/java/com/example/calltranslator/ui/theme/Color.kt)
- Define centralized Material 3 design tokens (colors, typography scales for font size settings, shapes, spacing, elevation) matching Phone by Google dark/light styles.

### Home / Recents & Filters
#### [MODIFY] [PhoneScreen.kt](file:///C:/Users/acer/OneDrive/Documents/project/AI_Call_Translator/app/src/main/java/com/example/calltranslator/ui/PhoneScreen.kt)
- Update Home/Recents screen with modern rounded search bar, Filter chips (All, Missed, Contacts), Favorites section, and Recent call rows with avatars, call direction, time, call count, and call/AI call actions using real data.

### Contacts & Favorites
#### [MODIFY] [ContactsScreen.kt](file:///C:/Users/acer/OneDrive/Documents/project/AI_Call_Translator/app/src/main/java/com/example/calltranslator/ui/ContactsScreen.kt) & [FavoritesScreen.kt](file:///C:/Users/acer/OneDrive/Documents/project/AI_Call_Translator/app/src/main/java/com/example/calltranslator/ui/FavoritesScreen.kt)
- Modernize contact presentation with search by name/number, favorite toggle, normal call action, AI translation call action, and recent calls integration.
- Implement real persistence for favorites in Firestore/Room.

### Keypad & Dialpad
#### [MODIFY] [PhoneScreen.kt](file:///C:/Users/acer/OneDrive/Documents/project/AI_Call_Translator/app/src/main/java/com/example/calltranslator/ui/PhoneScreen.kt) (Keypad tab or bottom sheet)
- Large Mute/dial pad layout (`1-9, *, 0, #`), number display, backspace, suggested contacts updating in real-time as digits are entered, and call button triggering the real outgoing call flow.

### Call Experience & AI Translation
#### [MODIFY] [CallScreen.kt](file:///C:/Users/acer/OneDrive/Documents/project/AI_Call_Translator/app/src/main/java/com/example/calltranslator/ui/CallScreen.kt) & [IncomingCallScreen.kt](file:///C:/Users/acer/OneDrive/Documents/project/AI_Call_Translator/app/src/main/java/com/example/calltranslator/ui/IncomingCallScreen.kt)
- Incoming call screen with real ringtone, accept, and reject.
- Outgoing call / Active call screen with contact name, duration timer, mute, speaker, earpiece, AI Translation toggle (ON/OFF), source/target languages, and live pipeline status (Listening, Translating, Speaking, Reconnecting, Error).

### Settings
#### [MODIFY] [SettingsScreen.kt](file:///C:/Users/acer/OneDrive/Documents/project/AI_Call_Translator/app/src/main/java/com/example/calltranslator/ui/SettingsScreen.kt) & [SettingsSubScreens.kt](file:///C:/Users/acer/OneDrive/Documents/project/AI_Call_Translator/app/src/main/java/com/example/calltranslator/ui/SettingsSubScreens.kt)
- Fully functional settings: Theme, Font Size, Hear In Language, AI Translation Model, Translation Speed, Audio Quality, Ringtone, Vibration, Encryption (real WebRTC security state), Privacy & Data, Call History management, Notifications, and Logout.

## Verification Plan

### Automated Tests
- Gradle build (`app:assembleDebug`) to verify compilation and resource validity.

### Manual Verification
- Deploy to two devices/emulators to test real incoming/outgoing calls, AI live translation (STT -> Gemini -> TTS), keypad dialing with suggested contacts, filters, and settings.
