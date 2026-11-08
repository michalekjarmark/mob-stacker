package com.frikinjay.mobstacker.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Finds settings from a few typed letters (1.12.0, asked for after test round 13). The config
 * screen's search box and {@code /mobstacker help <words>} both ask this, so the two cannot rank
 * differently.
 *
 * <p>Seventy-odd settings with long camelCase ids are slow to find by paging, and nobody remembers
 * whether it is {@code sweepingEdgeSingleHit} or {@code singleHitSweeping}. So the search forgives
 * the way an IDE's "go to symbol" does. In the order it trusts them:
 * <ul>
 *   <li>the id, or the start of it: {@code stackh} finds stackHealth;</li>
 *   <li>the starts of its words in order, any of them left out: {@code kwsod} or {@code killwhole}
 *       finds killWholeStackOnDeath, {@code max size} finds maxStackSize;</li>
 *   <li>words in any order, each one found in the id, the description, the page's name or one of
 *       the values: {@code health stack}, {@code wool}, {@code whitelist};</li>
 *   <li>a wrong letter or two in a longer word: {@code seperator}, {@code sweaping};</li>
 *   <li>and last, the letters in order anywhere in the id: {@code swpedg}.</li>
 * </ul>
 * Every way that fits gives a score, the best one counts, and hits come best first, in the
 * registry's order (the order of the pages) between equals. Weak hits are dropped when a much
 * better one exists, so a query that names a setting does not bring forty descriptions with it.
 * Each hit says which letters of its id matched, for the screen to show why it is there.
 */
public final class SettingSearch {
    private static final int EXACT = 1000;
    private static final int PREFIX = 900;
    private static final int WORD_START = 760;
    private static final int WORD_CHAIN = 720;
    private static final int CONTAINS = 620;
    private static final int TYPO = 600;
    private static final int PREFIX_TYPO = 540;
    private static final int TERMS = 300;
    private static final int TERMS_ALL_IN_ID = 100;
    private static final int LETTERS = 250;
    private static final int LETTERS_MAX = 450;
    /** A hit under this share of the best one is noise next to it. */
    private static final double KEEP_SHARE = 0.45;

    /** One setting found, how well, and which letters of its id matched. */
    public record Hit(ConfigOption option, int score, boolean[] matched) {
    }

    /** What a setting can be found by, worked out once per setting. */
    private record Index(String id, int[] wordStarts, String[] idWords, String[] descriptionWords,
                         String description, String[] categoryWords, String[] values) {
    }

    private static final Map<String, Index> INDEX = new ConcurrentHashMap<>();

    private SettingSearch() {
    }

    /** Every setting that matches {@code query}, best first; empty for an empty query. */
    public static List<Hit> search(String query, Collection<ConfigOption> options) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<String> terms = new ArrayList<>();
        for (String term : q.split("[^a-z0-9]+")) {
            if (!term.isEmpty()) {
                terms.add(term);
            }
        }
        if (terms.isEmpty()) {
            return List.of();
        }
        String compact = String.join("", terms);
        List<Hit> hits = new ArrayList<>();
        for (ConfigOption option : options) {
            Hit hit = score(option, terms, compact);
            if (hit != null) {
                hits.add(hit);
            }
        }
        if (hits.isEmpty()) {
            return hits;
        }
        // A stable sort keeps the registry's order between equal scores.
        hits.sort(Comparator.comparingInt((Hit hit) -> -hit.score()));
        int floor = (int) (hits.get(0).score() * KEEP_SHARE);
        hits.removeIf(hit -> hit.score() < floor);
        return hits;
    }

    /** The one best hit, or null - for "did you mean" when a command names no setting. */
    public static ConfigOption best(String query, Collection<ConfigOption> options) {
        List<Hit> hits = search(query, options);
        return hits.isEmpty() ? null : hits.get(0).option();
    }

    private static Hit score(ConfigOption option, List<String> terms, String compact) {
        Index index = INDEX.computeIfAbsent(option.id(), id -> index(option));
        String id = index.id();
        boolean[] marks = new boolean[id.length()];
        int best = 0;
        boolean[] bestMarks = marks;

        if (id.equals(compact)) {
            Arrays.fill(marks, true);
            return new Hit(option, EXACT, marks);
        }
        if (id.startsWith(compact)) {
            best = PREFIX - Math.min(100, id.length() - compact.length());
            bestMarks = range(id.length(), 0, compact.length());
        }
        int at = id.indexOf(compact);
        if (at > 0 && compact.length() >= 2) {
            int score = isWordStart(index, at) ? WORD_START - Math.min(60, at) : CONTAINS - Math.min(60, at);
            if (compact.length() >= 3 || isWordStart(index, at)) {
                if (score > best) {
                    best = score;
                    bestMarks = range(id.length(), at, compact.length());
                }
            }
        }
        if (compact.length() >= 2 && best < WORD_CHAIN) {
            boolean[] chain = new boolean[id.length()];
            int skips = wordChain(index, compact, chain);
            if (skips >= 0) {
                int score = Math.max(CONTAINS - 60, WORD_CHAIN - 25 * skips);
                if (score > best) {
                    best = score;
                    bestMarks = chain;
                }
            }
        }
        int allowed = typosAllowed(compact.length());
        if (best < TYPO && allowed > 0) {
            if (distance(compact, id) <= allowed) {
                best = TYPO;
                bestMarks = range(id.length(), 0, id.length());
            } else if (id.length() > compact.length()
                    && distance(compact, id.substring(0, compact.length())) <= allowed) {
                best = PREFIX_TYPO;
                bestMarks = range(id.length(), 0, compact.length());
            }
        }
        if (best < TERMS + 120 + TERMS_ALL_IN_ID) {
            boolean[] termMarks = new boolean[id.length()];
            int score = termScore(index, terms, termMarks);
            if (score > best) {
                best = score;
                bestMarks = termMarks;
            }
        }
        if (best < LETTERS_MAX && compact.length() >= 3) {
            boolean[] letterMarks = new boolean[id.length()];
            int score = letters(index, compact, letterMarks);
            if (score > best) {
                best = score;
                bestMarks = letterMarks;
            }
        }
        return best > 0 ? new Hit(option, best, bestMarks) : null;
    }

    /**
     * Each word typed must turn up somewhere: in the id, the page's name, a value or the
     * description. Worth less than a hit on the id as a whole, and more when every word was in it.
     */
    private static int termScore(Index index, List<String> terms, boolean[] marks) {
        int sum = 0;
        boolean allInId = true;
        for (String term : terms) {
            int inId = termInId(index, term, marks);
            int elsewhere = 0;
            if (inId == 0) {
                allInId = false;
                elsewhere = termElsewhere(index, term);
                if (elsewhere == 0) {
                    return 0;
                }
            }
            sum += Math.max(inId, elsewhere);
        }
        return TERMS + sum / terms.size() + (allInId ? TERMS_ALL_IN_ID : 0);
    }

    private static int termInId(Index index, String term, boolean[] marks) {
        String[] words = index.idWords();
        for (int i = 0; i < words.length; i++) {
            if (words[i].equals(term)) {
                mark(marks, index.wordStarts()[i], term.length());
                return 120;
            }
        }
        for (int i = 0; i < words.length; i++) {
            if (words[i].startsWith(term)) {
                mark(marks, index.wordStarts()[i], term.length());
                return 100;
            }
        }
        int at = index.id().indexOf(term);
        if (term.length() >= 2 && at >= 0) {
            mark(marks, at, term.length());
            return 75;
        }
        int allowed = typosAllowed(term.length());
        if (allowed > 0) {
            for (int i = 0; i < words.length; i++) {
                String word = words[i];
                boolean whole = distance(term, word) <= allowed;
                boolean start = word.length() > term.length()
                        && distance(term, word.substring(0, term.length())) <= allowed;
                if (whole || start) {
                    mark(marks, index.wordStarts()[i], whole ? word.length() : term.length());
                    return 65;
                }
            }
        }
        return 0;
    }

    private static int termElsewhere(Index index, String term) {
        if (term.length() >= 2) {
            for (String value : index.values()) {
                if (value.equals(term)) {
                    return 60;
                }
                if (value.startsWith(term)) {
                    return 50;
                }
            }
            for (String word : index.categoryWords()) {
                if (word.startsWith(term)) {
                    return 55;
                }
            }
        }
        for (String word : index.descriptionWords()) {
            if (word.equals(term)) {
                return 45;
            }
        }
        if (term.length() >= 3) {
            for (String word : index.descriptionWords()) {
                if (word.startsWith(term)) {
                    return 35;
                }
            }
        }
        if (term.length() >= 4 && index.description().contains(term)) {
            return 25;
        }
        int allowed = typosAllowed(term.length());
        if (allowed > 0) {
            for (String word : index.descriptionWords()) {
                if (word.length() >= 4 && distance(term, word) <= allowed) {
                    return 20;
                }
            }
        }
        return 0;
    }

    /**
     * Whether what was typed is the starts of the id's words in order, some words left out:
     * "kwsod", "killwhole", "maxsize". The answer is how many words were left out between or before
     * the ones used (fewer is better), or -1; the letters used are marked.
     */
    private static int wordChain(Index index, String typed, boolean[] marks) {
        String[] words = index.idWords();
        int n = typed.length();
        // best[w][p]: fewest words left out to match typed[p..] from word w on; INF when impossible.
        int inf = Integer.MAX_VALUE / 2;
        int[][] best = new int[words.length + 1][n + 1];
        int[][] took = new int[words.length + 1][n + 1];
        for (int w = words.length; w >= 0; w--) {
            for (int p = n; p >= 0; p--) {
                if (p == n) {
                    best[w][p] = 0;
                    continue;
                }
                if (w == words.length) {
                    best[w][p] = inf;
                    continue;
                }
                int result = 1 + best[w + 1][p];
                int choice = 0;
                String word = words[w];
                for (int k = Math.min(word.length(), n - p); k >= 1; k--) {
                    if (typed.regionMatches(p, word, 0, k) && best[w + 1][p + k] < result) {
                        result = best[w + 1][p + k];
                        choice = k;
                    }
                }
                best[w][p] = Math.min(result, inf);
                took[w][p] = choice;
            }
        }
        if (best[0][0] >= inf) {
            return -1;
        }
        int p = 0;
        for (int w = 0; w < words.length && p < n; w++) {
            int k = took[w][p];
            if (k > 0) {
                mark(marks, index.wordStarts()[w], k);
                p += k;
            }
        }
        return best[0][0];
    }

    /** The typed letters in order anywhere in the id, best where they start words or run together. */
    private static int letters(Index index, String typed, boolean[] marks) {
        String id = index.id();
        int score = LETTERS;
        int from = 0;
        int previous = -2;
        for (int i = 0; i < typed.length(); i++) {
            int at = id.indexOf(typed.charAt(i), from);
            if (at < 0) {
                return 0;
            }
            marks[at] = true;
            if (isWordStart(index, at)) {
                score += 12;
            }
            if (at == previous + 1) {
                score += 8;
            } else if (previous >= 0) {
                score -= Math.min(10, at - previous - 1);
            }
            previous = at;
            from = at + 1;
        }
        return Math.min(LETTERS_MAX, Math.max(1, score));
    }

    /** None in a short word, where a typo is another word; one from five letters, two from nine. */
    private static int typosAllowed(int length) {
        return length >= 9 ? 2 : length >= 5 ? 1 : 0;
    }

    /** Edits between two words, a swap of neighbours counting as one (optimal string alignment). */
    static int distance(String a, String b) {
        int[][] d = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) {
            d[i][0] = i;
        }
        for (int j = 0; j <= b.length(); j++) {
            d[0][j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                int value = Math.min(Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + cost);
                if (i > 1 && j > 1 && a.charAt(i - 1) == b.charAt(j - 2) && a.charAt(i - 2) == b.charAt(j - 1)) {
                    value = Math.min(value, d[i - 2][j - 2] + 1);
                }
                d[i][j] = value;
            }
        }
        return d[a.length()][b.length()];
    }

    private static boolean isWordStart(Index index, int at) {
        for (int start : index.wordStarts()) {
            if (start == at) {
                return true;
            }
        }
        return false;
    }

    private static boolean[] range(int length, int from, int count) {
        boolean[] marks = new boolean[length];
        mark(marks, from, count);
        return marks;
    }

    private static void mark(boolean[] marks, int from, int count) {
        for (int i = from; i < Math.min(marks.length, from + count); i++) {
            marks[i] = true;
        }
    }

    private static Index index(ConfigOption option) {
        String raw = option.id();
        List<Integer> starts = new ArrayList<>();
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            char before = i == 0 ? 0 : raw.charAt(i - 1);
            boolean upper = Character.isUpperCase(c);
            boolean start = i == 0
                    || (upper && !Character.isUpperCase(before))
                    || (upper && i + 1 < raw.length() && Character.isLowerCase(raw.charAt(i + 1)))
                    || (Character.isDigit(c) && !Character.isDigit(before));
            if (start) {
                starts.add(i);
            }
        }
        String id = raw.toLowerCase(Locale.ROOT);
        int[] wordStarts = starts.stream().mapToInt(Integer::intValue).toArray();
        String[] idWords = new String[wordStarts.length];
        for (int i = 0; i < wordStarts.length; i++) {
            int end = i + 1 < wordStarts.length ? wordStarts[i + 1] : id.length();
            idWords[i] = id.substring(wordStarts[i], end);
        }
        String description = option.description() == null ? "" : option.description().toLowerCase(Locale.ROOT);
        List<String> values = new ArrayList<>();
        if (option.enumValues() != null) {
            for (String value : option.enumValues()) {
                values.add(value.toLowerCase(Locale.ROOT));
            }
        }
        return new Index(id, wordStarts, idWords, words(description), description,
                words(option.category().display().toLowerCase(Locale.ROOT) + " " + option.category().name().toLowerCase(Locale.ROOT)),
                values.toArray(new String[0]));
    }

    private static String[] words(String text) {
        List<String> out = new ArrayList<>();
        for (String word : text.split("[^a-z0-9]+")) {
            if (!word.isEmpty()) {
                out.add(word);
            }
        }
        return out.toArray(new String[0]);
    }
}
