package com.premierleague.server.util;

import java.text.Normalizer;
import java.util.Locale;

public final class PlayerNameNormalizer {

    private PlayerNameNormalizer() {
    }

    public static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }

        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKD)
                .replaceAll("\\p{M}", "")
                .replace('\u0130', 'I')
                .replace('\u0131', 'i')
                .replace('\u2019', '\'')
                .replace('\u2018', '\'')
                .replace('\u2013', '-')
                .replace('\u2014', '-')
                .replace('Ø', 'O')
                .replace('ø', 'o')
                .replace('Ł', 'L')
                .replace('ł', 'l')
                .replace('Đ', 'D')
                .replace('đ', 'd')
                .replace('Æ', 'A')
                .replace('æ', 'a')
                .replace('Œ', 'O')
                .replace('œ', 'o')
                .replace('ß', 's');

        return normalized.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9' -]", "")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
