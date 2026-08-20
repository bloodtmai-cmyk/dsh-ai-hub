package ai.dsh.hub.catalog;

import ai.dsh.hub.common.ApiException;
import org.springframework.http.HttpStatus;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class SemanticVersion implements Comparable<SemanticVersion> {
    private static final Pattern PATTERN = Pattern.compile(
            "^(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)(?:-([0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*))?(?:\\+[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?$"
    );

    private final BigInteger major;
    private final BigInteger minor;
    private final BigInteger patch;
    private final List<String> prerelease;

    private SemanticVersion(BigInteger major, BigInteger minor, BigInteger patch, List<String> prerelease) {
        this.major = major;
        this.minor = minor;
        this.patch = patch;
        this.prerelease = prerelease;
    }

    static SemanticVersion parse(String value) {
        Matcher matcher = PATTERN.matcher(value == null ? "" : value.trim());
        if (!matcher.matches()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SEMANTIC_VERSION",
                    "版本必须使用 SemVer，例如 1.2.0");
        }
        List<String> prerelease = matcher.group(4) == null
                ? List.of() : List.of(matcher.group(4).split("\\."));
        return new SemanticVersion(new BigInteger(matcher.group(1)), new BigInteger(matcher.group(2)),
                new BigInteger(matcher.group(3)), prerelease);
    }

    @Override
    public int compareTo(SemanticVersion other) {
        int core = compareCore(other);
        if (core != 0) return core;
        if (prerelease.isEmpty() && other.prerelease.isEmpty()) return 0;
        if (prerelease.isEmpty()) return 1;
        if (other.prerelease.isEmpty()) return -1;
        int count = Math.max(prerelease.size(), other.prerelease.size());
        for (int i = 0; i < count; i++) {
            if (i >= prerelease.size()) return -1;
            if (i >= other.prerelease.size()) return 1;
            int compared = compareIdentifier(prerelease.get(i), other.prerelease.get(i));
            if (compared != 0) return compared;
        }
        return 0;
    }

    private int compareCore(SemanticVersion other) {
        List<BigInteger> left = new ArrayList<>(List.of(major, minor, patch));
        List<BigInteger> right = List.of(other.major, other.minor, other.patch);
        for (int i = 0; i < left.size(); i++) {
            int compared = left.get(i).compareTo(right.get(i));
            if (compared != 0) return compared;
        }
        return 0;
    }

    private static int compareIdentifier(String left, String right) {
        boolean leftNumeric = left.matches("[0-9]+");
        boolean rightNumeric = right.matches("[0-9]+");
        if (leftNumeric && rightNumeric) return new BigInteger(left).compareTo(new BigInteger(right));
        if (leftNumeric != rightNumeric) return leftNumeric ? -1 : 1;
        return left.compareTo(right);
    }
}
