package com.gr4v1ty.supplylines.util;

import net.minecraftforge.fml.ModList;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Provides version information for the SupplyLines mod at runtime.
 *
 * <p>
 * Version format follows Semantic Versioning 2.0.0:
 * {@code MAJOR.MINOR.PATCH[-PRERELEASE][+BUILD]}
 *
 * <p>
 * Examples:
 * <ul>
 * <li>{@code 1.0.0} - stable release</li>
 * <li>{@code 1.0.0-alpha.1} - alpha pre-release</li>
 * <li>{@code 1.0.0-alpha.1+abc1234} - dev build with commit hash</li>
 * </ul>
 */
public final class ModVersion {

    private static final String MOD_ID = "supplylines";

    // Semantic version pattern: MAJOR.MINOR.PATCH[-PRERELEASE][+BUILD]
    private static final Pattern VERSION_PATTERN = Pattern
            .compile("^(\\d+)\\.(\\d+)\\.(\\d+)(?:-([a-zA-Z0-9.]+))?(?:\\+([a-zA-Z0-9.]+))?$");

    private static ModVersion instance;

    private final String fullVersion;
    private final int major;
    private final int minor;
    private final int patch;
    private final String preRelease;
    private final String buildMetadata;

    private ModVersion(String version) {
        this.fullVersion = version;

        Matcher matcher = VERSION_PATTERN.matcher(version);
        if (matcher.matches()) {
            this.major = Integer.parseInt(matcher.group(1));
            this.minor = Integer.parseInt(matcher.group(2));
            this.patch = Integer.parseInt(matcher.group(3));
            this.preRelease = matcher.group(4);
            this.buildMetadata = matcher.group(5);
        } else {
            this.major = 0;
            this.minor = 0;
            this.patch = 0;
            this.preRelease = null;
            this.buildMetadata = null;
        }
    }

    /**
     * Gets the singleton ModVersion instance. Must be called after mod loading is
     * complete.
     */
    public static ModVersion get() {
        if (instance == null) {
            String version = ModList.get().getModContainerById(MOD_ID)
                    .map(container -> container.getModInfo().getVersion().toString()).orElse("0.0.0-unknown");
            instance = new ModVersion(version);
        }
        return instance;
    }

    /**
     * Returns the full version string as specified in mods.toml. Examples: "1.0.0",
     * "1.0.0-alpha.1", "1.0.0-alpha.1+abc1234"
     */
    public String getFullVersion() {
        return fullVersion;
    }

    /**
     * Returns the base version without build metadata. Examples: "1.0.0",
     * "1.0.0-alpha.1"
     */
    public String getBaseVersion() {
        if (preRelease != null) {
            return String.format("%d.%d.%d-%s", major, minor, patch, preRelease);
        }
        return String.format("%d.%d.%d", major, minor, patch);
    }

    /**
     * Returns just the numeric version: MAJOR.MINOR.PATCH
     */
    public String getNumericVersion() {
        return String.format("%d.%d.%d", major, minor, patch);
    }

    public int getMajor() {
        return major;
    }

    public int getMinor() {
        return minor;
    }

    public int getPatch() {
        return patch;
    }

    /**
     * Returns the pre-release identifier (alpha.1, beta.2, rc.1), or empty for
     * stable.
     */
    public Optional<String> getPreRelease() {
        return Optional.ofNullable(preRelease);
    }

    /**
     * Returns the build metadata (git hash), or empty for release builds.
     */
    public Optional<String> getBuildMetadata() {
        return Optional.ofNullable(buildMetadata);
    }

    /**
     * Returns true if this is a development build (has build metadata).
     */
    public boolean isDevBuild() {
        return buildMetadata != null;
    }

    /**
     * Returns true if this is a pre-release version (alpha, beta, rc).
     */
    public boolean isPreRelease() {
        return preRelease != null;
    }

    /**
     * Returns true if this is a stable release (no pre-release, no build metadata).
     */
    public boolean isStableRelease() {
        return preRelease == null && buildMetadata == null;
    }

    /**
     * Returns the pre-release type (ALPHA, BETA, RC) or STABLE.
     */
    public PreReleaseType getPreReleaseType() {
        if (preRelease == null) {
            return PreReleaseType.STABLE;
        }
        String lower = preRelease.toLowerCase();
        if (lower.startsWith("alpha")) {
            return PreReleaseType.ALPHA;
        }
        if (lower.startsWith("beta")) {
            return PreReleaseType.BETA;
        }
        if (lower.startsWith("rc")) {
            return PreReleaseType.RC;
        }
        return PreReleaseType.OTHER;
    }

    /**
     * Returns a display-friendly version string. For dev builds: "1.0.0-alpha.1
     * (dev: abc1234)" For releases: "1.0.0-alpha.1"
     */
    public String getDisplayVersion() {
        if (buildMetadata != null) {
            return getBaseVersion() + " (dev: " + buildMetadata + ")";
        }
        return getBaseVersion();
    }

    @Override
    public String toString() {
        return fullVersion;
    }

    public enum PreReleaseType {
        STABLE, ALPHA, BETA, RC, OTHER
    }
}
