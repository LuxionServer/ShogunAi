# Spec Delta

## MODIFIED Requirements

### Requirement: Check the tools of each project's providers
For each registered project whose base folder exists, the check SHALL read the providers its repository declares and report one check per tool they need: the Gradle wrapper or `gradle` for `gradle`, followed by the Java runtime Gradle uses, the Java of Gradle's daemon, the build's Java toolchains and the Java the Android Gradle Plugin needs; the Maven wrapper or `mvn` for `maven`, followed by the Java runtime Maven uses; the Android SDK and an AVD for `emulator`, the simulators and an iOS runtime for `ios-simulator`, Xcode for `xcode` and `pod` when it declares CocoaPods, `docker compose` for `docker-compose`, followed by Docker's daemon; and `flutter` (or `fvm`) for `flutter`. The Java runtime check SHALL be reported only when the Gradle or Maven wrapper or command was found, and the Docker daemon check only when `docker compose version` ran. The checks of the daemon's Java, the toolchains and the Android Gradle Plugin SHALL be reported only when Gradle was found and the repository's build needs each of them.

#### Scenario: Tools found
- **WHEN** a project declares `docker-compose`, `docker compose version` runs and `docker info` succeeds
- **THEN** a check for that project's Docker Compose passes, naming where `docker` was found and its version, followed by a check for Docker's daemon that passes

#### Scenario: No providers
- **WHEN** a project's `.shogunai.json` declares no providers, or the repository has none
- **THEN** no provider tool check is reported for it

#### Scenario: Wrapper in the repository
- **WHEN** a project declares `gradle`, its base folder has `gradlew` and `gradle` isn't on the `PATH`
- **THEN** the Gradle check passes, naming the wrapper

#### Scenario: Tool missing, no runtime check
- **WHEN** a project declares `maven` and neither `mvnw` nor `mvn` is found
- **THEN** the Maven check warns and no Java check is reported for Maven

#### Scenario: Plain Gradle build
- **WHEN** a project declares `gradle`, its wrapper is found, and its repository has no daemon JVM criteria, no `org.gradle.java.home`, no toolchain and no Android Gradle Plugin
- **THEN** only the Gradle check and the Java check for Gradle are reported for it

## ADDED Requirements

### Requirement: Find the Java the build chooses for Gradle's daemon
The check of the Java for Gradle's daemon SHALL follow Gradle's precedence: the daemon JVM criteria's `toolchainVersion` in `gradle/gradle-daemon-jvm.properties` when the file exists and the project's Gradle is 8.8 or later or its version is unknown; else `org.gradle.java.home` from `gradle.properties` in `GRADLE_USER_HOME` (default `~/.gradle`), else from the repository's `gradle.properties`. With none of them, the check SHALL NOT be reported.

#### Scenario: Criteria take precedence
- **WHEN** a project uses Gradle 8.10.2, its `gradle/gradle-daemon-jvm.properties` has `toolchainVersion=21` and its `gradle.properties` sets `org.gradle.java.home`
- **THEN** the check of the daemon's Java judges Java 21 from the criteria and ignores `org.gradle.java.home`

#### Scenario: Criteria ignored by an old Gradle
- **WHEN** a project uses Gradle 8.5 and has `gradle/gradle-daemon-jvm.properties`, and no `org.gradle.java.home` is set
- **THEN** no check of the daemon's Java is reported

#### Scenario: User's gradle.properties wins
- **WHEN** `GRADLE_USER_HOME/gradle.properties` and the repository's `gradle.properties` both set `org.gradle.java.home`
- **THEN** the check uses the one in `GRADLE_USER_HOME`

### Requirement: Daemon JVM criteria need an installed or downloadable Java
For daemon JVM criteria, the check SHALL pass, naming the Java version and where it was found, when a Java of exactly `toolchainVersion` is installed where Gradle looks; else pass, saying Gradle downloads it, when the file has `toolchainUrl.` entries and downloads aren't turned off. Otherwise it SHALL warn, naming the version and the places searched, with the fix to install that JDK or let Gradle download it. A missing or non-numeric `toolchainVersion` SHALL warn that the criteria can't be read.

#### Scenario: Requested Java installed
- **WHEN** the criteria ask for Java 21 and a JDK whose `release` file says `JAVA_VERSION="21.0.5"` is in `/Library/Java/JavaVirtualMachines` on macOS
- **THEN** the check of the daemon's Java passes, naming Java 21 and that JDK's folder

#### Scenario: Requested Java downloadable
- **WHEN** the criteria ask for Java 21, no Java 21 is installed and the file has `toolchainUrl.MAC_OS.AARCH64`
- **THEN** the check passes, saying Gradle downloads Java 21

#### Scenario: Requested Java missing
- **WHEN** the criteria ask for Java 21, no Java 21 is installed and the file has no `toolchainUrl.` entry
- **THEN** the check warns that Java 21 wasn't found, naming the places searched, with the fix to install a Java 21 JDK or add a toolchain download repository and run `./gradlew updateDaemonJvm`

#### Scenario: Downloads turned off
- **WHEN** the criteria ask for Java 21, no Java 21 is installed, the file has `toolchainUrl.` entries and `org.gradle.java.installations.auto-download=false` is set
- **THEN** the check warns that Java 21 wasn't found

#### Scenario: Unreadable criteria
- **WHEN** `gradle/gradle-daemon-jvm.properties` has no `toolchainVersion`
- **THEN** the check warns that the daemon JVM criteria can't be read, with the fix to regenerate them with `./gradlew updateDaemonJvm`

### Requirement: Check the Java set by org.gradle.java.home
When the daemon's Java comes from `org.gradle.java.home`, the check SHALL run `bin/java` (`bin\java.exe` on Windows) under it with `-version` and pass, naming the major version and where it was found. It SHALL warn, naming the property, its value and the file that sets it, when that `java` doesn't exist, and warn with its output when `java -version` fails.

#### Scenario: Property points at a JDK
- **WHEN** the repository's `gradle.properties` sets `org.gradle.java.home=/opt/jdk-17` and `/opt/jdk-17/bin/java -version` prints `openjdk version "17.0.12"`
- **THEN** the check of the daemon's Java passes, naming Java 17 and `/opt/jdk-17/bin/java`

#### Scenario: Property points nowhere
- **WHEN** `org.gradle.java.home` is `/opt/jdk-gone` and `/opt/jdk-gone/bin/java` doesn't exist
- **THEN** the check warns, naming `org.gradle.java.home`, `/opt/jdk-gone` and the `gradle.properties` that sets it, with the fix to correct or remove the property

### Requirement: The daemon's Java suits the Gradle version
The check of the daemon's Java SHALL judge its Java version against the project's Gradle version with the same tables as the Java check for Gradle: warn when Java is older than the Gradle version needs, or when Gradle is older than the first one that runs on that Java. The fix SHALL name the setting that chose that Java.

#### Scenario: Criteria too new for the Gradle version
- **WHEN** a project uses Gradle 8.10 and its criteria ask for Java 25
- **THEN** the check of the daemon's Java warns that Java 25 needs Gradle 9.1, with the fix to update the wrapper or lower `toolchainVersion` in `gradle/gradle-daemon-jvm.properties`

#### Scenario: Property Java too old for Gradle 9
- **WHEN** a project uses Gradle 9.1.0 and `org.gradle.java.home` points at a Java 11
- **THEN** the check warns that Gradle 9.1.0 needs Java 17, with the fix to point `org.gradle.java.home` at Java 17 or newer

### Requirement: Check the build's Java toolchains
When the root build script, a build script of a direct subfolder, or a file under `buildSrc` or `build-logic` declares a Java toolchain (`jvmToolchain(N)` or `JavaLanguageVersion.of(N)`), a check of the Java toolchains SHALL pass, naming the versions, when each is installed where Gradle looks or the settings script applies the `org.gradle.toolchains.foojay-resolver` plugin and downloads aren't turned off. Otherwise it SHALL warn, naming the missing versions and the places searched.

#### Scenario: Toolchain installed
- **WHEN** `app/build.gradle.kts` declares `jvmToolchain(17)` and a Java 17 JDK is in `~/.sdkman/candidates/java`
- **THEN** the Java toolchains check passes, naming Java 17

#### Scenario: Toolchain downloadable
- **WHEN** a build declares `JavaLanguageVersion.of(21)`, no Java 21 is installed and `settings.gradle.kts` applies `org.gradle.toolchains.foojay-resolver-convention`
- **THEN** the Java toolchains check passes, saying Gradle downloads Java 21

#### Scenario: Toolchain missing
- **WHEN** a build declares `jvmToolchain(17)` and `jvmToolchain(21)`, Java 21 is installed, Java 17 isn't and no resolver plugin is applied
- **THEN** the Java toolchains check warns that Java 17 wasn't found, with the fix to install a Java 17 JDK or apply the `foojay-resolver-convention` plugin in the settings script

#### Scenario: No toolchain
- **WHEN** no build script declares a toolchain
- **THEN** no Java toolchains check is reported

### Requirement: Where installed Java is searched
Installed Java SHALL be searched where Gradle detects it: `JAVA_HOME`, the Java that runs Gradle, `org.gradle.java.installations.paths`, `GRADLE_USER_HOME/jdks`, `~/.jdks`, SDKMAN!'s and asdf's Java folders, and the system's JDK folders. A JDK's version SHALL be read from its `release` file. With `org.gradle.java.installations.auto-detect=false`, only `org.gradle.java.installations.paths` and the Java that runs Gradle SHALL count.

#### Scenario: System JDK folders
- **WHEN** the check looks for installed Java
- **THEN** it searches `/Library/Java/JavaVirtualMachines` and `~/Library/Java/JavaVirtualMachines` on macOS, `/usr/lib/jvm` on Linux, and `C:\Program Files\Java` and `C:\Program Files\Eclipse Adoptium` on Windows

#### Scenario: JDK downloaded by Gradle
- **WHEN** the criteria ask for Java 17 and `~/.gradle/jdks/eclipse_adoptium-17-aarch64-os_x/jdk-17.0.12+7/Contents/Home/release` says `JAVA_VERSION="17.0.12"`
- **THEN** Java 17 is found there

#### Scenario: Auto-detection turned off
- **WHEN** `org.gradle.java.installations.auto-detect=false` is set, the only Java 21 is in `/usr/lib/jvm` on Linux and the criteria ask for Java 21
- **THEN** the check warns that Java 21 wasn't found

### Requirement: Check the Java the Android Gradle Plugin needs
When the version catalog (`gradle/libs.versions.toml`) or a build script uses the Android Gradle Plugin with a version, a check SHALL judge the Java the daemon runs (the criteria's version, else `org.gradle.java.home`'s, else the Java for Gradle's) against it: AGP 8 or later needs Java 17, AGP 7 Java 11, older AGP Java 8. It SHALL pass naming both, or warn with the fix to raise that Java where it was chosen. When that Java is unknown, the check SHALL NOT be reported.

#### Scenario: AGP 8 on Java 17
- **WHEN** `gradle/libs.versions.toml` has `agp = "8.5.2"` referenced by the `com.android.application` plugin and Gradle's Java is 17
- **THEN** the Android Gradle Plugin check passes, naming AGP 8.5.2 and Java 17

#### Scenario: AGP 8 on Java 11
- **WHEN** the root `build.gradle` has `classpath 'com.android.tools.build:gradle:8.2.0'`, the daemon has no criteria nor `org.gradle.java.home`, and Gradle's Java is 11
- **THEN** the check warns that the Android Gradle Plugin 8.2.0 needs Java 17, with the fix to point `JAVA_HOME` at Java 17 or newer

#### Scenario: Criteria choose the daemon's Java
- **WHEN** the build uses AGP 8.7.0, `JAVA_HOME` is a Java 11 and the daemon JVM criteria ask for Java 21
- **THEN** the Android Gradle Plugin check passes with Java 21

#### Scenario: No Android Gradle Plugin
- **WHEN** no build script nor the version catalog uses the Android Gradle Plugin
- **THEN** no Android Gradle Plugin check is reported
