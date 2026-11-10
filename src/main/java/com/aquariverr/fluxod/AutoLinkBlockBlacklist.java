package com.aquariverr.fluxod;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

final class AutoLinkBlockBlacklist {
    static final List<String> DEFAULT_ENTRIES = List.of(
            "ae2:pattern_provider",
            "advanced_ae:small_adv_pattern_provider",
            "extendedae:ex_pattern_provider_part",
            "appflux:flux_accessor",
            "extendedae:ex_pattern_provider",
            "ae2:cable_pattern_provider",
            "advanced_ae:small_adv_pattern_provider_part",
            "appflux:flux_accessor_part",
            "gtceu:.+_(single|double|quadruple|octal|hex)_(wire|cable)",
            "mekanism:(basic|advanced|elite|ultimate)_universal_cable",
            "sfm:(?:(?:tough|tunnelled)_)?(?:fancy_)?cable(?:_facade)?"
    );

    private final Set<String> blockIds;
    private final List<String> blockTags;
    private final List<Pattern> blockIdPatterns;

    private AutoLinkBlockBlacklist(Set<String> blockIds, List<String> blockTags,
                                   List<Pattern> blockIdPatterns) {
        this.blockIds = Set.copyOf(blockIds);
        this.blockTags = List.copyOf(blockTags);
        this.blockIdPatterns = List.copyOf(blockIdPatterns);
    }

    static AutoLinkBlockBlacklist parse(List<? extends String> entries,
                                        Function<String, String> resourceLocationNormalizer) {
        Set<String> blockIds = new LinkedHashSet<>();
        Set<String> blockTags = new LinkedHashSet<>();
        List<Pattern> blockIdPatterns = new ArrayList<>();

        for (String entry : entries) {
            if (entry == null || entry.isBlank()) {
                throw new IllegalArgumentException("Auto-link block blacklist entries cannot be blank");
            }
            if (entry.startsWith("#")) {
                String tagId = resourceLocationNormalizer.apply(entry.substring(1));
                if (tagId == null) {
                    throw new IllegalArgumentException("Invalid auto-link block tag: " + entry);
                }
                blockTags.add(tagId);
                continue;
            }

            String blockId = resourceLocationNormalizer.apply(entry);
            if (blockId != null) {
                blockIds.add(blockId);
                continue;
            }

            try {
                blockIdPatterns.add(Pattern.compile(entry));
            } catch (PatternSyntaxException exception) {
                throw new IllegalArgumentException("Invalid auto-link block ID regular expression: " + entry,
                        exception);
            }
        }

        return new AutoLinkBlockBlacklist(blockIds, List.copyOf(blockTags), blockIdPatterns);
    }

    static boolean isValidEntry(Object value, Function<String, String> resourceLocationNormalizer) {
        if (!(value instanceof String entry) || entry.isBlank()) return false;
        if (entry.startsWith("#")) {
            return resourceLocationNormalizer.apply(entry.substring(1)) != null;
        }
        if (resourceLocationNormalizer.apply(entry) != null) return true;
        try {
            Pattern.compile(entry);
            return true;
        } catch (PatternSyntaxException ignored) {
            return false;
        }
    }

    List<String> blockTags() {
        return blockTags;
    }

    boolean matches(String blockId, Predicate<String> tagMembership) {
        if (blockIds.contains(blockId)) return true;
        for (String blockTag : blockTags) {
            if (tagMembership.test(blockTag)) return true;
        }

        for (Pattern pattern : blockIdPatterns) {
            if (pattern.matcher(blockId).matches()) return true;
        }
        return false;
    }
}
