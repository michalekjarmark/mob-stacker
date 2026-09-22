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
            testInertSettings(report);
            testLists(report);
            testListValidation(report);
            testRegionCreation(report);
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
        // The stored value, not the effective one: a setting held inert by another reads as off
        // whatever its default is, and that is correct rather than a failure.
        check(report, option.storedValue().equals(option.defaultValue()),
                option.id() + ": stored value != default after reset ("
                        + option.storedValue() + " vs " + option.defaultValue() + ")");

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
                // "max" is the top of this setting's own range, whatever that is, and is stored as
                // the number it means - so nothing downstream ever has to know the word.
                ConfigOption.Result asMax = option.apply(ConfigOption.MAX_KEYWORD);
                check(report, asMax.status != Status.ERROR,
                        option.id() + ": refused '" + ConfigOption.MAX_KEYWORD + "' (" + asMax.message + ")");
                check(report, !option.currentValue().equalsIgnoreCase(ConfigOption.MAX_KEYWORD),
                        option.id() + ": stored the word '" + ConfigOption.MAX_KEYWORD + "' rather than a number");
                check(report, numeric(option.currentValue()) == option.max().doubleValue(),
                        option.id() + ": '" + ConfigOption.MAX_KEYWORD + "' gave " + option.currentValue()
                                + " instead of " + option.max());
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

    /**
     * Every switch that another setting can hold inert, checked from both ends.
     *
     * <p>Two things have to be true at once and neither is obvious from the code: while the setting
     * it needs is off, it must <b>read as OFF</b> — a greyed-out switch sitting on ON is exactly the
     * confusion {@code requires} exists to prevent, and reading as the <em>default</em> quietly broke
     * that for {@code keepMemberEquipment}, whose default is on — and it must still be possible to
     * turn it <b>off</b>, since switching something off is always safe and was being refused for the
     * same reason. Generic over the registry on purpose: the next setting with a default of true
     * gets both checks for free.
     */
    private static void testInertSettings(Report report) {
        for (ConfigOption option : MobStackerSettings.all()) {
            String requiredId = option.requires();
            if (requiredId == null || option.type() != Type.BOOL) {
                continue;
            }
            ConfigOption required = MobStackerSettings.byId(requiredId);
            if (required == null || required.type() != Type.BOOL) {
                continue;
            }
            Restore restoreRequired = Restore.of(required);
            Restore restoreOption = Restore.of(option);
            required.apply("false");

            check(report, "false".equals(option.currentValue()),
                    option.id() + " reads as " + option.currentValue() + " while " + requiredId
                            + " is off; a setting that does nothing must read as off");
            check(report, option.apply("true").status == Status.ERROR,
                    option.id() + " could be turned on while " + requiredId + " is off");
            check(report, option.apply("false").status != Status.ERROR,
                    option.id() + " could not be turned off while " + requiredId + " is off");

            restoreOption.undo();
            restoreRequired.undo();
        }
    }

    /**
     * What a mob list will and will not accept.
     *
     * <p>Lists used to take anything at all, so a typo sat in the config looking exactly like an
     * entry that was working. The rule is not "does it exist right now" — a modded id has to
     * survive its mod being away — it is "could it ever have meant anything", which for the
     * {@code minecraft} namespace is a question with a definite answer.
     */
    private static void testListValidation(Report report) {
        check(report, MobLists.entryProblem(MobListKind.DENY_ENTITIES, "minecraft:cow") == null,
                "a real vanilla mob was refused by an entity list");
        check(report, MobLists.entryProblem(MobListKind.DENY_ENTITIES, "minecraft:not_a_mob") != null,
                "an entity list accepted a vanilla id nothing answers to");
        check(report, MobLists.entryProblem(MobListKind.ALLOW_ENTITIES, "somemod:whatever") == null,
                "an entity list refused a modded id, which has to survive its mod being away");
        check(report, MobLists.entryProblem(MobListKind.DENY_ENTITIES, "") != null,
                "an entity list accepted an empty entry");
        check(report, MobLists.entryProblem(MobListKind.DENY_MODS, "somemod") == null,
                "a mod list refused a plain namespace");
        check(report, MobLists.entryProblem(MobListKind.DENY_MODS, "somemod:cow") != null,
                "a mod list accepted a whole entity id");
        check(report, MobLists.isLoaded(MobListKind.DENY_ENTITIES, "minecraft:cow"),
                "minecraft:cow is not reported as loaded");
        check(report, !MobLists.isLoaded(MobListKind.DENY_ENTITIES, "somemod:whatever"),
                "an absent modded id is reported as loaded");
        check(report, MobLists.entryNote(MobListKind.DENY_ENTITIES, "minecraft:cow") == null,
                "a loaded id came with a 'not loaded' note");
        check(report, MobLists.entryNote(MobListKind.DENY_ENTITIES, "somemod:whatever") != null,
                "an absent modded id was accepted without a word");
        check(report, MobLists.entityProblem("minecraft:not_a_mob") != null,
                "a stack ceiling accepted a vanilla id nothing answers to");
    }

    /**
     * Creating a region and redrawing one are the same write and different intentions.
     *
     * <p>"New region…" with a name that was already taken used to move somebody else's region onto
     * the box around the player, silently and with no undo. Both directions are checked here
     * because making one of them strict is exactly how the other one breaks.
     */
    private static void testRegionCreation(Report report) {
        String name = "selftest_region";
        RegionEdit.delete(name);

        RegionEdit.Result created = RegionEdit.apply(name, StackRegion.Type.ALLOW,
                "minecraft:overworld", 0, 0, 0, 4, 4, 4, true);
        check(report, created.ok(), "a new region was refused: " + created.message());

        RegionEdit.Result again = RegionEdit.apply(name, StackRegion.Type.ALLOW,
                "minecraft:overworld", 100, 0, 100, 104, 4, 104, true);
        check(report, !again.ok(), "'New region' overwrote a region that already existed");
        StackRegion kept = MobStacker.config.getRegion(name);
        check(report, kept != null && kept.getMinX() == 0,
                "the refused create moved the existing region anyway");

        RegionEdit.Result reshaped = RegionEdit.apply(name, StackRegion.Type.ALLOW,
                "minecraft:overworld", 100, 0, 100, 104, 4, 104, false);
        check(report, reshaped.ok(), "redrawing an existing region was refused: " + reshaped.message());
        StackRegion moved = MobStacker.config.getRegion(name);
        check(report, moved != null && moved.getMinX() == 100,
                "redrawing an existing region did not move it");

        RegionEdit.delete(name);
        check(report, MobStacker.config.getRegion(name) == null,
                "the self-test's own region survived being deleted");
    }

    /**
     * Round-trips the mob lists and the per-type ceilings, which the option loop above cannot reach
     * because they are not scalar settings.
     *
     * <p>Worth its own pass because the interesting behaviour is not "does a value come back": it is
     * that an entry is normalised on the way in, that a region tells inheriting apart from having an
     * empty list of its own, and that taking a list over keeps what it was inheriting. All three are
     * easy to get subtly wrong and impossible to notice without looking.
     */
    private static void testLists(Report report) {
        for (MobListKind kind : MobListKind.values()) {
            MobStacker.config.clearList(kind);
            String entry = kind.flavour() == MobListKind.Flavour.ENTITY ? "minecraft:cow" : "examplemod";

            check(report, MobStacker.config.addToList(kind, entry),
                    kind.id() + ": adding '" + entry + "' to an empty list reported no change");
            check(report, MobStacker.config.getList(kind).contains(entry),
                    kind.id() + ": '" + entry + "' was added but is not in the list");
            check(report, !MobStacker.config.addToList(kind, entry),
                    kind.id() + ": adding '" + entry + "' twice reported a change");
            check(report, MobStacker.config.getList(kind).size() == 1,
                    kind.id() + ": adding '" + entry + "' twice stored it twice");
            check(report, MobStacker.config.removeFromList(kind, entry),
                    kind.id() + ": removing '" + entry + "' reported no change");
            check(report, !MobStacker.config.removeFromList(kind, entry),
                    kind.id() + ": removing '" + entry + "' twice reported a change");

            if (kind.flavour() == MobListKind.Flavour.ENTITY) {
                // "cow" and "minecraft:cow" have to be one entry, or a list quietly holds both.
                MobStacker.config.addToList(kind, "cow");
                check(report, MobStacker.config.getList(kind).contains("minecraft:cow"),
                        kind.id() + ": 'cow' was not stored as 'minecraft:cow'");
                check(report, !MobStacker.config.addToList(kind, "minecraft:cow"),
                        kind.id() + ": 'cow' and 'minecraft:cow' were stored as two entries");
                MobStacker.config.clearList(kind);
            }
        }

        StackRegion region = new StackRegion("selftest", "minecraft:overworld",
                StackRegion.Type.ALLOW, 0, 0, 0, 1, 1, 1);
        MobListKind kind = MobListKind.DENY_ENTITIES;
        MobStacker.config.clearList(kind);
        MobStacker.config.addToList(kind, "minecraft:cow");

        check(report, !region.hasList(kind),
                "a fresh region claims to override " + kind.id());
        check(report, MobLists.effective(kind, region).contains("minecraft:cow"),
                "a region that overrides nothing did not inherit the global " + kind.id());

        region.setList(kind, MobStacker.config.getList(kind));
        check(report, region.hasList(kind),
                "a region given its own " + kind.id() + " still claims to inherit");
        check(report, region.getList(kind).contains("minecraft:cow"),
                "taking " + kind.id() + " over lost what it was inheriting");

        region.removeFromList(kind, "minecraft:cow");
        check(report, region.hasList(kind),
                "emptying a region's " + kind.id() + " dropped the override instead of meaning 'nothing'");
        check(report, MobLists.effective(kind, region).isEmpty(),
                "an emptied region list fell back to the global one");
        check(report, MobStacker.config.getList(kind).contains("minecraft:cow"),
                "editing a region's " + kind.id() + " changed the global list too");

        region.clearList(kind);
        check(report, !region.hasList(kind),
                "a region told to inherit " + kind.id() + " still claims its own");
        check(report, MobLists.effective(kind, region).contains("minecraft:cow"),
                "a region told to inherit " + kind.id() + " did not get the global list back");
        MobStacker.config.clearList(kind);

        // Ceilings: set, read back, and unset.
        MobStacker.config.setMaxStackSize("minecraft:cow", 64);
        check(report, Integer.valueOf(64).equals(MobStacker.config.getMaxStackSize("minecraft:cow")),
                "a global stack ceiling did not come back");
        check(report, Integer.valueOf(64).equals(MobStacker.config.getMaxStackSize("cow")),
                "a stack ceiling was not found under the un-namespaced id");
        MobStacker.config.setMaxStackSize("minecraft:cow", null);
        check(report, MobStacker.config.getMaxStackSize("minecraft:cow") == null,
                "a global stack ceiling survived being unset");

        region.setMaxStackSize("minecraft:cow", 8);
        check(report, Integer.valueOf(8).equals(region.getMaxStackSize("minecraft:cow")),
                "a region stack ceiling did not come back");
        region.setMaxStackSize("minecraft:cow", null);
        check(report, region.getMaxStackSize("minecraft:cow") == null,
                "a region stack ceiling survived being unset");
    }

    private static void check(Report report, boolean pass, String failureMessage) {
        report.checks++;
        if (!pass) {
            report.failures++;
            report.messages.add(failureMessage);
        }
    }

    /** The value as a number, or NaN - which fails any comparison, which is the right answer here. */
    private static double numeric(String value) {
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return Double.NaN;
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
