package com.frikinjay.mobstacker.config;

import com.frikinjay.mobstacker.MobStacker;
import com.frikinjay.mobstacker.config.ConfigOption.Result.Status;
import com.frikinjay.mobstacker.config.ConfigOption.Type;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Automated round-trip check of every registered {@link ConfigOption}. For each setting it verifies
 * reset-to-default, then type-appropriate behaviour: booleans toggle and flip back; numbers reject
 * non-numbers and out-of-range values but accept a valid one; enums accept every constant and reject
 * garbage; item settings accept a real item and reject a bogus id. It also checks the known
 * stackHealth / killWholeStackOnDeath dependency.
 * <p>
 * The whole run happens against a throwaway sandbox config (a fresh {@link MobStackerConfig} pointed
 * at a temp file), so the live per-world config and its save file are never touched. Runs on the
 * server thread, synchronously, so no mob ticking observes the swapped config.
 */
public final class ConfigSelfTest {

    public static final class Report {
        public int options;
        public int checks;
        public int failures;
        public final List<String> messages = new ArrayList<>();

        public boolean passed() {
            return failures == 0;
        }
    }

    private ConfigSelfTest() {
    }

    public static Report run() {
        Report report = new Report();
        MobStackerConfig realConfig = MobStacker.config;
        File realFile = MobStacker.configFile;
        try {
            File temp = File.createTempFile("mobstacker-selftest", ".json");
            temp.deleteOnExit();
            MobStacker.configFile = temp;
            MobStacker.config = new MobStackerConfig();

            for (ConfigOption option : MobStackerSettings.all()) {
                report.options++;
                testOption(option, report);
            }
            testDependencies(report);
        } catch (Exception e) {
            report.checks++;
            report.failures++;
            report.messages.add("self-test crashed: " + e);
        } finally {
            MobStacker.config = realConfig;
            MobStacker.configFile = realFile;
        }
        return report;
    }

    private static void testOption(ConfigOption option, Report report) {
        // Baseline: reset to default and confirm it took.
        ConfigOption.Result reset = option.reset();
        check(report, reset.status != Status.ERROR, option.id() + ": reset errored (" + reset.message + ")");
        check(report, option.currentValue().equals(option.defaultValue()),
                option.id() + ": value != default after reset (" + option.currentValue() + " vs " + option.defaultValue() + ")");

        switch (option.type()) {
            case BOOL -> {
                // A setting that needs another one is inert until that one is on, and refuses to be
                // switched on - which is the point of it, not a fault. Switch its prerequisites on
                // for the duration so the setting itself is actually exercised.
                List<Restore> prerequisites = satisfyDependencies(option);
                boolean before = Boolean.parseBoolean(option.currentValue());
                ConfigOption.Result first = option.toggle();
                check(report, first.status == Status.CHANGED, option.id() + ": first toggle was not CHANGED (" + first.status + ")");
                check(report, Boolean.parseBoolean(option.currentValue()) != before, option.id() + ": toggle did not flip the value");
                option.toggle(); // restore
                for (Restore prerequisite : prerequisites) {
                    prerequisite.undo();
                }
            }
            case INT, DOUBLE -> {
                check(report, option.apply("notanumber").status == Status.ERROR, option.id() + ": accepted a non-numeric value");
                check(report, option.apply(belowMin(option)).status == Status.ERROR, option.id() + ": accepted a below-minimum value");
                check(report, option.apply(aboveMax(option)).status == Status.ERROR, option.id() + ": accepted an above-maximum value");
                String valid = validDifferent(option);
                if (valid != null) {
                    ConfigOption.Result applied = option.apply(valid);
                    check(report, applied.status == Status.CHANGED,
                            option.id() + ": valid value " + valid + " not applied (" + applied.status + " " + applied.message + ")");
                }
                option.reset();
            }
            case ENUM -> {
                for (String value : option.enumValues()) {
                    ConfigOption.Result applied = option.apply(value);
                    check(report, applied.status != Status.ERROR, option.id() + ": rejected enum value " + value + " (" + applied.message + ")");
                }
                check(report, option.apply("__not_a_value__").status == Status.ERROR, option.id() + ": accepted a bogus enum value");
                option.reset();
            }
            case ITEM -> {
                check(report, option.apply("minecraft:stone").status != Status.ERROR, option.id() + ": rejected a valid item (minecraft:stone)");
                check(report, option.apply("mobstacker:not_a_real_item").status == Status.ERROR, option.id() + ": accepted a bogus item id");
                option.reset();
            }
            case STRING -> {
                // Free-form; nothing to assert beyond the reset baseline above.
            }
        }
    }

    /**
     * Puts the config into the state where {@code option} actually means something: everything it
     * depends on switched on, innermost first, and anything that would make it redundant switched
     * off. Without this the test reports a failure for a setting that is merely inert, which is
     * exactly what it did for every {@code requires} setting until 1.7.0.
     *
     * @return what was changed, for the caller to put back exactly as it was
     */
    private static List<Restore> satisfyDependencies(ConfigOption option) {
        List<ConfigOption> chain = new ArrayList<>();
        ConfigOption current = option;
        // Guarded against a dependency loop a future setting could introduce by mistake.
        for (int depth = 0; depth < 16; depth++) {
            String requiredId = current.requires();
            if (requiredId == null) {
                break;
            }
            ConfigOption required = MobStackerSettings.byId(requiredId);
            if (required == null || required.type() != Type.BOOL) {
                break;
            }
            chain.add(required);
            current = required;
        }

        // Innermost first: a setting can only be switched on once the one it needs already is.
        List<Restore> changed = new ArrayList<>();
        for (int i = chain.size() - 1; i >= 0; i--) {
            ConfigOption required = chain.get(i);
            if (!Boolean.parseBoolean(required.currentValue())) {
                changed.add(Restore.of(required));
                required.apply("true");
            }
        }

        // And the opposite direction: a setting that already does this one's job has to be off, or
        // the option under test would correctly refuse to change and look like a failure.
        String blockerId = option.redundantWhen();
        ConfigOption blocker = blockerId == null ? null : MobStackerSettings.byId(blockerId);
        if (blocker != null && blocker.type() == Type.BOOL && Boolean.parseBoolean(blocker.currentValue())) {
            changed.add(Restore.of(blocker));
            blocker.apply("false");
        }
        return changed;
    }

    /**
     * A setting and the value it had before the test touched it.
     *
     * <p>Putting it back with {@code reset()} would have been wrong: that restores the *default*,
     * not what the player had. Harmless while the test only ever switched something on from its
     * default, but the moment it has to switch something off — a setting that makes another one
     * redundant — resetting would quietly take the player's own choice away.
     */
    private record Restore(ConfigOption option, String value) {
        static Restore of(ConfigOption option) {
            return new Restore(option, option.storedValue());
        }

        void undo() {
            option.apply(value);
        }
    }

    private static void testDependencies(Report report) {
        ConfigOption stackHealth = MobStackerSettings.byId("stackHealth");
        ConfigOption killWhole = MobStackerSettings.byId("killWholeStackOnDeath");
        if (stackHealth == null || killWhole == null) {
            check(report, false, "dependency test: stackHealth/killWholeStackOnDeath option missing");
            return;
        }
        stackHealth.reset();
        killWhole.reset();
        stackHealth.apply("true");
        check(report, MobStacker.getKillWholeStackOnDeath(),
                "stackHealth=true did not force killWholeStackOnDeath on");
        check(report, "true".equals(killWhole.currentValue()),
                "killWholeStackOnDeath did not report itself as forced on");
        ConfigOption.Result blocked = killWhole.apply("false");
        check(report, blocked.status == Status.ERROR,
                "killWholeStackOnDeath=false was allowed while stackHealth is on");
        // The lock must not overwrite what is stored: switching stackHealth off gives it back.
        stackHealth.apply("false");
        check(report, !MobStacker.getKillWholeStackOnDeath(),
                "killWholeStackOnDeath stayed on after stackHealth was switched off");
        stackHealth.reset();
        killWhole.reset();
    }

    private static void check(Report report, boolean pass, String failureMessage) {
        report.checks++;
        if (!pass) {
            report.failures++;
            report.messages.add(failureMessage);
        }
    }

    private static String belowMin(ConfigOption option) {
        if (option.type() == Type.INT) {
            return Long.toString((long) option.min().doubleValue() - 1);
        }
        return Double.toString(option.min() - 1.0);
    }

    private static String aboveMax(ConfigOption option) {
        if (option.type() == Type.INT) {
            return Long.toString((long) option.max().doubleValue() + 1);
        }
        return Double.toString(option.max() + 1.0);
    }

    private static String validDifferent(ConfigOption option) {
        double min = option.min();
        double max = option.max();
        if (option.type() == Type.INT) {
            long def = Long.parseLong(option.defaultValue());
            long candidate = (def + 1 <= max) ? def + 1 : def - 1;
            return (candidate < min) ? null : Long.toString(candidate);
        }
        double def = Double.parseDouble(option.defaultValue());
        double candidate = (def + 1.0 <= max) ? def + 1.0 : def - 1.0;
        return (candidate < min) ? null : Double.toString(candidate);
    }
}
