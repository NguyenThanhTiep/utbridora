package vn.utbridora.auth.modules.cooperation.domain;

import java.text.Normalizer;
import java.util.*;

public final class VietnameseBusinessTextNormalizer {
    private VietnameseBusinessTextNormalizer() {}
    public enum MatchKind { EXACT, NORMALIZED_MATCH, PARTIAL, MISMATCH }
    public record Match(MatchKind kind, int score) { public boolean matches() { return score >= 85; } }
    public static String normalize(String raw) {
        String text = Normalizer.normalize(raw == null ? "" : raw, Normalizer.Form.NFD)
            .replaceAll("\\p{M}+", "").replace('đ', 'd').replace('Đ', 'D').toUpperCase(Locale.ROOT)
            .replaceAll("[^A-Z0-9]+", " ").strip();
        String[][] replacements = {{"TNHH", "TRACH NHIEM HUU HAN"}, {"CP", "CO PHAN"}, {"MTV", "MOT THANH VIEN"},
            {"TM", "THUONG MAI"}, {"DV", "DICH VU"}, {"SX", "SAN XUAT"}, {"XD", "XAY DUNG"},
            {"TPHCM|TP HCM|TP HO CHI MINH|THANH PHO HO CHI MINH", "HO CHI MINH"},
            {"TP HN|TP HA NOI|THANH PHO HA NOI|HN", "HA NOI"}, {"P", "PHUONG"}, {"Q", "QUAN"}, {"TX", "THI XA"}, {"TT", "THI TRAN"}};
        for (var replacement : replacements) text = text.replaceAll("\\b(?:" + replacement[0] + ")\\b", replacement[1]);
        return text.replaceAll("\\s+", " ").strip();
    }
    public static Match companyName(String declared, String official) {
        Match full = compare(declared, official);
        if (full.kind() == MatchKind.EXACT || full.kind() == MatchKind.NORMALIZED_MATCH) return full;
        // Shared legal boilerplate must not turn two different business names into a green match.
        String boilerplate = "\\b(CONG|TY|TRACH|NHIEM|HUU|HAN|MOT|THANH|VIEN|CO|PHAN|THUONG|MAI|DICH|VU|SAN|XUAT|XAY|DUNG)\\b";
        Match identity = compare(normalize(declared).replaceAll(boilerplate, " "), normalize(official).replaceAll(boilerplate, " "));
        int score = Math.min(full.score(), identity.score());
        return new Match(score >= 60 ? MatchKind.PARTIAL : MatchKind.MISMATCH, score);
    }
    public static Match address(String declared, String official) { return compare(declared, official); }
    private static Match compare(String left, String right) {
        String a = normalize(left), b = normalize(right);
        if (a.isEmpty() || b.isEmpty()) return new Match(MatchKind.MISMATCH, 0);
        if (Objects.equals(left, right)) return new Match(MatchKind.EXACT, 100);
        if (a.equals(b)) return new Match(MatchKind.NORMALIZED_MATCH, 100);
        Set<String> x = new HashSet<>(List.of(a.split(" "))), y = new HashSet<>(List.of(b.split(" ")));
        Set<String> common = new HashSet<>(x); common.retainAll(y);
        int score = (int) Math.round(200.0 * common.size() / (x.size() + y.size()));
        // Similar address words cannot disguise a different street/ward/house number.
        Set<String> nx = new HashSet<>(), ny = new HashSet<>();
        x.stream().filter(t -> t.matches(".*[0-9].*")).forEach(nx::add);
        y.stream().filter(t -> t.matches(".*[0-9].*")).forEach(ny::add);
        if (!nx.equals(ny)) score = Math.min(score, 60);
        return new Match(score >= 60 ? MatchKind.PARTIAL : MatchKind.MISMATCH, score);
    }
}
