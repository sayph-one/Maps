# Organic Maps - Android Codebase Context

## What is Organic Maps?

**Organic Maps** is a privacy-first, open-source offline maps and GPS navigation application. It's a successor to MAPS.ME and uses OpenStreetMap data.

**Key Features:**
- Offline navigation (hiking, cycling, driving) with turn-by-turn voice guidance
- Privacy-focused (no ads, no tracking, no data collection)
- Offline search on maps
- Bookmarks and track management (KML, KMZ, GPX formats)
- Contour lines, elevation profiles, peaks
- Dark mode support
- Android Auto and Car App support
- Track recording
- OSM map editing

## Project Structure

```
~/AndroidStudioProjects/Maps/
├── android/                    # Android app (YOU ARE HERE)
│   ├── app/                    # Main Android application module
│   ├── sdk/                    # SDK module (JNI wrapper for native code)
│   ├── build.gradle            # Root Gradle build file
│   ├── settings.gradle         # Module configuration
│   └── gradle.properties       # Build properties
├── data/                       # Map data, copyrights, FAQs
├── 3party/                     # Third-party libraries
├── iphone/                     # iOS app
└── CMakeLists.txt              # Native C++ code build
```

## Architecture

### Pattern: MVVM + Fragment-Based + Native JNI Bridge

```
┌─────────────────────────────────────────┐
│         Android UI Layer (Java)         │
│  Activities → Fragments → ViewModels    │
└─────────────────┬───────────────────────┘
                  │ JNI Bridge
┌─────────────────▼───────────────────────┐
│      Native Engine (C++)                │
│  Maps, Routing, Search, Rendering       │
└─────────────────────────────────────────┘
```

### Main Components

1. **Main Activity**: `MwmActivity` (app/src/main/java/app/organicmaps/MwmActivity.java:2409)
   - Central hub for map display and navigation
   - Manages fragment transactions
   - Handles GPS/location state
   - Displays place page (bottom sheet)

2. **Key Fragments**:
   - `SearchFragment` - Search UI and results
   - `RoutingPlanFragment` - Route planning
   - `DownloaderFragment` - Map downloads
   - `BookmarksListFragment` - Bookmark management
   - `EditorFragment` - OSM editing
   - `PlacePageView` - Place details (bottom sheet)

3. **Services**:
   - `NavigationService` - Foreground navigation with voice
   - `TrackRecordingService` - Track recording
   - `DownloaderService` - Background map downloads
   - `CarAppService` - Android Auto/Automotive OS

4. **Native Bridge** (sdk/):
   - `Framework` - Main JNI wrapper (all static native methods)
   - `BookmarkManager` - Bookmark CRUD
   - `RoutingController` - Routing logic
   - `SearchEngine` - Search functionality
   - `LocationHelper` - GPS/location access
   - `TrackRecorder` - Track recording

## Key Directories

### App Module (`app/src/main/java/app/organicmaps/`)

| Directory | Purpose | Notable Files |
|-----------|---------|---------------|
| **/** | Core activities | MwmActivity, SplashActivity, MwmApplication |
| **base/** | Base classes | BaseMwmFragmentActivity, BaseMwmFragment |
| **widget/placepage/** | Place details UI | PlacePageView, PlacePageController |
| **routing/** | Navigation | NavigationService, RoutingPlanFragment |
| **downloader/** | Map downloading | DownloaderAdapter, DownloaderFragment |
| **search/** | Search | SearchFragment, SearchActivity |
| **bookmarks/** | Bookmarks/tracks | BookmarksListFragment |
| **editor/** | OSM editing | EditorFragment |
| **settings/** | Preferences | SettingsActivity, SettingsPrefsFragment |
| **location/** | Location services | TrackRecordingService |
| **car/** | Android Auto | CarAppService, screens |
| **util/** | Utilities | Utils, UiUtils, ThemeSwitcher |

### SDK Module (`sdk/src/main/java/app/organicmaps/sdk/`)

| Directory | Purpose | Notable Files |
|-----------|---------|---------------|
| **/** | Framework bindings | Framework, Map, Router, OrganicMaps |
| **location/** | Location/tracking | LocationHelper, SensorHelper, TrackRecorder |
| **routing/** | Routing data | RoutingController, RoutingInfo |
| **bookmarks/data/** | Bookmarks | BookmarkManager, MapObject |
| **downloader/** | Downloads | Map manager |
| **search/** | Search | SearchEngine |
| **util/** | Utilities | Config, StorageUtils, logging |

## Technology Stack

### Languages
- **Java** - Main Android app (253 classes, ~39K LOC)
- **C++** - Native mapping engine, routing, search, rendering
- **XML** - Layout files, configuration

### Build System
- **Gradle 8.14.3** - Build orchestration
- **CMake 3.22.1+** - Native code compilation
- **Android Gradle Plugin 8.13.0**
- **NDK 29.0.14206865** - Native Development Kit

### Android Framework
- AndroidX AppCompat 1.7.1
- AndroidX Fragment 1.8.9
- AndroidX Lifecycle (LiveData, ViewModel) 2.9.4
- Material Design Components 1.13.0
- ConstraintLayout 2.2.1
- RecyclerView 1.4.0
- AndroidX Preference 1.2.1
- AndroidX Car App 1.8.0-alpha02
- AndroidX Work 2.10.5

### Other Libraries
- Google Guava 33.5.0-android
- MPAndroidChart (JitPack)
- Google Play Services Location (google/huawei/web flavors)
- microG (fdroid flavor)
- Firebase (optional, flavor-specific)

## Build Configuration

### SDK Versions
- **Min SDK**: API 21 (Android 5.0 Lollipop)
- **Target SDK**: API 36 (Android 15)
- **Compile SDK**: API 36 (Android 15)

### Build Flavors
1. **google** - Google Play flavor (with optional Firebase)
2. **fdroid** - F-Droid flavor (no Google services, microG)
3. **huawei** - Huawei AppGallery
4. **web** - Direct web download

### Build Types
1. **debug** - Development
2. **release** - Production (minified, R8, no obfuscation)
3. **beta** - Firebase App Distribution beta testing

### Architecture Support
- arm64-v8a (primary)
- armeabi-v7a (primary)
- x86_64 (emulator)
- x86 (emulator)

## Entry Points

### App Launch Flow
```
SplashActivity (launcher)
  ├─ Checks core initialization
  ├─ Handles deep links (geo:, om:, ge0:)
  ├─ Processes file imports (KML, KMZ, GPX)
  └─ Launches → MwmActivity (main map)
```

### Deep Link Support
- URL schemes: `geo:`, `ge0:`, `om:`
- Web domains: `ge0.me`, `omaps.app`, `www.openstreetmap.org`
- File types: KML, KMZ, KMB, GPX, GeoJSON

### Android Auto Entry
```
CarAppService → CarAppSession
  └─ Screens: Map, Navigation, Search, Bookmarks, Downloads, Settings
```

## Important Files

### Configuration
- `app/build.gradle` - App build configuration, flavors, build types
- `sdk/build.gradle` - SDK build, CMake/NDK setup
- `gradle.properties` - SDK versions, localizations
- `app/src/main/AndroidManifest.xml` - Activities, services, permissions

### Core Classes
- `MwmActivity` - Main activity (2409 LOC)
- `MwmApplication` - Application class, lifecycle management
- `Framework` (sdk) - JNI wrapper for native code
- `OrganicMaps` (sdk) - Core initialization, lifecycle observer
- `PlacePageView` - Place details UI (1103 LOC)
- `PlacePageController` - Place page logic (722 LOC)
- `BookmarksListFragment` - Bookmark UI (853 LOC)
- `DownloaderAdapter` - Map list display (781 LOC)
- `EditorFragment` - OSM editing (695 LOC)
- `SearchFragment` - Search UI (634 LOC)

### Resources
- `app/src/main/res/layout/` - 50+ XML layouts
- `app/src/main/res/values/` - 50+ language localizations
- `app/src/main/res/drawable-*/` - Multi-density drawables
- `app/src/main/res/values-night/` - Dark mode theme

## Permissions

### Runtime Permissions
- Location: `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`
- Notifications: `POST_NOTIFICATIONS`

### Install-Time Permissions
- Network: `INTERNET`, `ACCESS_NETWORK_STATE`
- System: `WAKE_LOCK`
- Foreground services: location, media playback, data sync
- Car: `androidx.car.app.NAVIGATION_TEMPLATES`, `ACCESS_SURFACE`

## Common Tasks

### Running the App
```bash
# Debug build (Google flavor)
./gradlew :app:assembleGoogleDebug

# Release build (all flavors)
./gradlew :app:assembleRelease

# Install on device
./gradlew :app:installGoogleDebug
```

### Native Code
- Native code lives in parent directory (C++)
- Built via CMake through `sdk/build.gradle`
- JNI wrappers in `sdk/src/main/java/app/organicmaps/sdk/`

### Localization
- 50+ languages supported
- Strings in `app/src/main/res/values-*/strings.xml`
- Auto-generated locale list in `gradle.properties`

## Architecture Patterns

### Base Class Hierarchy
```
AppCompatActivity
  └─ BaseMwmFragmentActivity (core initialization)
     └─ BaseToolbarActivity
        └─ Concrete activities

Fragment
  └─ BaseMwmFragment (core setup)
     ├─ BaseMwmToolbarFragment
     ├─ BaseMwmRecyclerFragment
     └─ BaseMwmDialogFragment
```

### ViewModel Pattern
```
ViewModel (AndroidX Lifecycle)
  ├─ PlacePageViewModel
  └─ MapButtonsViewModel

Uses MutableLiveData for reactive state
Observed by UI components (Fragments)
Survives configuration changes
```

### Intent Processing
```
IntentProcessor interface
  ├─ KmzKmlProcessor (KML/KMZ imports)
  ├─ UrlProcessor (geo: schemes, OSM links)
  └─ Other handlers

Factory class - parses and routes intents
```

## Privacy & Analytics

- **Default**: No tracking, no Firebase
- **Optional Firebase**: Enabled per flavor/build type
  - Crashlytics (with NDK symbols)
  - Analytics
  - Conditional on `google-services.json` presence

## Quick Reference

| Aspect | Value |
|--------|-------|
| Package | `app.organicmaps` |
| Min SDK | API 21 (Android 5.0) |
| Target SDK | API 36 (Android 15) |
| Main Language | Java + C++ (native) |
| Primary Activity | MwmActivity |
| Launcher Activity | SplashActivity |
| Build System | Gradle 8.14.3 + CMake + NDK 29 |
| Total Build Variants | 12 (4 flavors × 3 build types) |

## Notes for Development

1. **Native Changes**: Require CMake rebuild, triggered by `sdk/build.gradle`
2. **Localization**: Add strings to all `values-*/strings.xml` files
3. **Flavors**: Be mindful of flavor-specific code (Firebase, Play Services)
4. **ProGuard**: Disabled obfuscation for open-source transparency
5. **Fragments**: Use single-activity pattern with fragment transactions
6. **JNI**: All native calls go through static methods in `Framework` class
7. **Lifecycle**: Core initialization managed by `OrganicMaps` class (lifecycle observer)
8. **Deep Links**: Handle in `SplashActivity` via `IntentProcessor` pattern

---

Last updated: 2025-10-25
Generated by: Claude Code