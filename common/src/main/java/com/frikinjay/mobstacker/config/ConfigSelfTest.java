package com.frikinjay.mobstacker.config;

import com.frikinjay.mobstacker.MobStacker;
import com.frikinjay.mobstacker.config.ConfigOption.Result.Status;
import com.frikinjay.mobstacker.config.ConfigOption.Type;

import com.google.gson.Gson;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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
        return run(null);
    }

    /**
     * @param source who ran the test, for the checks that need a world and the command tree; null
     *               leaves those out
     */
    public static Report run(CommandSourceStack source) {
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
            testRegionPrivacy(report);
            if (source != null) {
                testCommandAccess(report, source);
                testSpawnCounting(report, source);
                testMemberCopies(report, source);
            }
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
                // As for a switch: a number that needs a default-off setting (maxDamageMultiplier) is
                // refused until that one is on, which is not what this checks.
                List<Restore> prerequisites = satisfyDependencies(option);
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
                // ...and "default" has to bring it back from there, which is the reset below.
                option.apply(ConfigOption.DEFAULT_KEYWORD);
                for (Restore prerequisite : prerequisites) {
                    prerequisite.undo();
                }
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
                List<Restore> prerequisites = satisfyDependencies(option);
                check(report, option.apply("minecraft:stone").status != Status.ERROR, option.id() + ": rejected a valid item (minecraft:stone)");
                check(report, option.apply("mobstacker:not_a_real_item").status == Status.ERROR, option.id() + ": accepted a bogus item id");
                option.reset();
                for (Restore prerequisite : prerequisites) {
                    prerequisite.undo();
                }
            }
            case STRING -> {
                // Free-form, unless the setting has a validator of its own (ignoredDataKeys).
                if (option.id().equals("ignoredDataKeys")) {
                    List<Restore> prerequisites = satisfyDependencies(option);
                    check(report, option.apply("ForgeCaps.somemod:timer, somemod_age").status != Status.ERROR,
                            option.id() + ": refused a well-formed key list");
                    check(report, option.apply("not a key!").status == Status.ERROR,
                            option.id() + ": accepted a key with spaces in it");
                    check(report, com.frikinjay.mobstacker.MobData.parseIgnored(" A , B.c ,, ").size() == 2,
                            option.id() + ": did not read ' A , B.c ,, ' as two keys");
                    option.reset();
                    for (Restore prerequisite : prerequisites) {
                        prerequisite.undo();
                    }
                }
                if (option.id().equals("stackLabelFormat")) {
                    List<Restore> prerequisites = satisfyDependencies(option);
                    check(report, option.apply("{type} ({count})").status != Status.ERROR,
                            option.id() + ": refused {type} ({count})");
                    check(report, option.apply("{type}").status == Status.ERROR,
                            option.id() + ": accepted a label that does not say how many");
                    check(report, StackLabelFormat.build("[{count}] {type}s", net.minecraft.network.chat.Component.literal("Cow"), 7)
                                    .getString().equals("[7] Cows"),
                            option.id() + ": '[{count}] {type}s' did not read as '[7] Cows'");
                    option.reset();
                    for (Restore prerequisite : prerequisites) {
                        prerequisite.undo();
                    }
                }
            }
        }

        // "default" is reset under another name, for every kind of setting, and is never stored as
        // the word. Checked on the stored value for the same reason as the baseline above. For a
        // number it has just been used to come back from "max"; everything else checks the word is
        // taken at all. A region takes it through canonicalize, which has to agree.
        ConfigOption.Result asDefault = option.apply(" Default ");
        check(report, asDefault.status != Status.ERROR,
                option.id() + ": refused '" + ConfigOption.DEFAULT_KEYWORD + "' (" + asDefault.message + ")");
        check(report, option.storedValue().equals(option.defaultValue()),
                option.id() + ": '" + ConfigOption.DEFAULT_KEYWORD + "' left " + option.storedValue()
                        + " instead of " + option.defaultValue());
        check(report, option.canonicalize(ConfigOption.DEFAULT_KEYWORD).equals(option.defaultValue()),
                option.id() + ": a region's '" + ConfigOption.DEFAULT_KEYWORD + "' reads as "
                        + option.canonicalize(ConfigOption.DEFAULT_KEYWORD) + " instead of " + option.defaultValue());
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
            // restore, not apply: the value being put back may be one a player could not set right
            // now - keepMemberEquipment's own default, with stackEquippedMobs still off.
            option.restore(value);
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
            // Putting the default back is a reset, not a request, and must never be refused - that
            // is what "reset all" does to every setting in turn, in whatever state it finds them.
            check(report, option.reset().status != Status.ERROR,
                    option.id() + " could not be reset while " + requiredId + " is off");

            // The same two answers for a region's own override, judged against that region.
            StackRegion region = new StackRegion("selftest", "minecraft:overworld",
                    StackRegion.Type.ALLOW, 0, 0, 0, 1, 1, 1);
            region.setSetting(requiredId, "false");
            check(report, MobStackerSettings.regionEditProblem(option, region, "true") != null,
                    option.id() + " could be turned on in a region whose " + requiredId + " is off");
            check(report, MobStackerSettings.regionEditProblem(option, region, "false") == null,
                    option.id() + " could not be turned off in a region whose " + requiredId + " is off");

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

        // Tags in the mob lists (1.12.0).
        check(report, "#minecraft:raiders".equals(MobLists.normalise(MobListKind.DENY_ENTITIES, "#Raiders")),
                "'#Raiders' was not stored as #minecraft:raiders");
        check(report, MobLists.entryProblem(MobListKind.DENY_ENTITIES, "#minecraft:raiders") == null,
                "an entity list refused a vanilla tag");
        check(report, MobLists.entryProblem(MobListKind.ALLOW_ENTITIES, "#somemod:whatever") == null,
                "an entity list refused a tag that is not loaded, which a data pack may add later");
        check(report, MobLists.entryProblem(MobListKind.DENY_ENTITIES, "#not a tag") != null,
                "an entity list accepted a malformed tag");
        check(report, MobLists.entityProblem("#minecraft:raiders") != null,
                "a stack ceiling accepted a tag, which only the mob lists understand");
        check(report, MobLists.isLoaded(MobListKind.DENY_ENTITIES, "#minecraft:raiders"),
                "#minecraft:raiders is not reported as loaded");
        check(report, !MobLists.isLoaded(MobListKind.DENY_ENTITIES, "#somemod:whatever"),
                "an absent tag is reported as loaded");
        check(report, MobLists.entryNote(MobListKind.DENY_ENTITIES, "#somemod:whatever") != null,
                "an absent tag was accepted without a word");

        // The mod's own tags have to come out of the jar on both loaders (a Forge jar without a
        // pack.mcmeta loads none of its data).
        check(report, EntityType.BEE.is(MobStacker.UNSTACKABLE_TAG),
                "#mobstacker:unstackable was not loaded from the jar (no bee in it)");
        check(report, EntityType.WITHER.is(MobStacker.BOSSES_TAG) && EntityType.WARDEN.is(MobStacker.BOSSES_TAG),
                "#mobstacker:bosses was not loaded from the jar (no wither or warden in it)");
        check(report, !EntityType.COW.is(MobStacker.UNSTACKABLE_TAG) && !EntityType.COW.is(MobStacker.BOSSES_TAG),
                "a cow is in one of the mod's never-stack tags");
    }

    /**
     * countStackMembers (1.12.0): the spawn-cap count sees a stack of five as five mobs, and a
     * non-mob once; off, everything once. The entities are made for the test and never added to the
     * world.
     */
    private static void testSpawnCounting(Report report, CommandSourceStack source) {
        net.minecraft.world.entity.Mob cow = EntityType.COW.create(source.getLevel());
        ArmorStand stand = new ArmorStand(EntityType.ARMOR_STAND, source.getLevel());
        if (cow == null) {
            check(report, false, "could not create a cow for the spawn-count check");
            return;
        }
        MobStacker.setStackSize(cow, 5);
        testStackLabels(report, cow);
        List<net.minecraft.world.entity.Entity> world = List.of(cow, stand);
        check(report, count(MobStacker.countedForSpawning(world)) == 6,
                "countStackMembers on: a stack of 5 and an armor stand did not count as 6");
        MobStacker.config.setCountStackMembers(false);
        check(report, count(MobStacker.countedForSpawning(world)) == 2,
                "countStackMembers off: a stack of 5 and an armor stand did not count as 2");
        MobStacker.config.setCountStackMembers(true);
        check(report, MobStacker.config.getMonsterMobCap() == 70 && MobStacker.config.getWaterAmbientMobCap() == 20,
                "a new config does not start with vanilla's spawn caps");

        // A whole-dimension deny region refuses stacking anywhere in it, and nothing while it is off.
        String dimension = source.getLevel().dimension().location().toString();
        cow.setPos(1_234_567, 100, -7_654_321);
        StackMode mode = MobStacker.config.getStackMode();
        MobStacker.config.setStackMode(StackMode.EVERYWHERE);
        RegionEdit.applyWholeDimension("selftest_nostack", StackRegion.Type.DENY, dimension);
        check(report, MobStacker.locationRefusal(cow) == com.frikinjay.mobstacker.StackRefusal.DENY_REGION,
                "a mob far out in a whole-dimension deny region may still stack");
        check(report, MobStacker.regionAt(cow) != null, "regionAt does not find a whole-dimension region");
        // Its biome filter, judged on whatever biome is really at that spot in this world.
        net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> here = source.getLevel().getBiome(cow.blockPosition());
        String hereId = here.unwrapKey().map(key -> key.location().toString()).orElse("minecraft:plains");
        String elsewhere = "minecraft:the_void".equals(hereId) ? "minecraft:plains" : "minecraft:the_void";
        RegionEdit.editBiomes("selftest_nostack", List.of(elsewhere), true);
        check(report, MobStacker.locationRefusal(cow) == null,
                "a deny region narrowed to " + elsewhere + " still stops stacking in " + hereId);
        RegionEdit.editBiomes("selftest_nostack", List.of(hereId), true);
        check(report, MobStacker.locationRefusal(cow) == com.frikinjay.mobstacker.StackRefusal.DENY_REGION,
                "a deny region that lists " + hereId + " does not stop stacking there");
        java.util.Optional<String> hereTag = here.tags().findFirst().map(tag -> RegionBiomes.TAG_PREFIX + tag.location());
        if (hereTag.isPresent()) {
            RegionEdit.editBiomes("selftest_nostack", List.of(elsewhere, hereId), false);
            RegionEdit.editBiomes("selftest_nostack", List.of(hereTag.get()), true);
            check(report, MobStacker.locationRefusal(cow) == com.frikinjay.mobstacker.StackRefusal.DENY_REGION,
                    "a deny region that lists " + hereTag.get() + " does not stop stacking in " + hereId);
        }
        RegionEdit.setEnabled("selftest_nostack", false);
        check(report, MobStacker.locationRefusal(cow) == null && MobStacker.regionAt(cow) == null,
                "a deny region switched off still stops stacking");
        RegionEdit.delete("selftest_nostack");
        MobStacker.config.setStackMode(mode);
    }

    /**
     * 1.12.0 (#46/#56): a stack's label is never written onto the mob, an older version's is taken back
     * out of its name, and a game without the mod is sent the label in the name instead.
     */
    @SuppressWarnings("unchecked")
    private static void testStackLabels(Report report, net.minecraft.world.entity.Mob cow) {
        check(report, cow.getCustomName() == null,
                "a stack of 5 got its label written into its own name: " + cow.getCustomName());
        cow.setCustomName(net.minecraft.network.chat.Component.literal("Cow x5"));
        MobStacker.updateStackDisplay(cow);
        check(report, cow.getCustomName() == null, "the label an older version left in a stack's name stayed");
        cow.setCustomName(net.minecraft.network.chat.Component.literal("Bessie x5"));
        MobStacker.updateStackDisplay(cow);
        check(report, cow.getCustomName() != null && cow.getCustomName().getString().equals("Bessie"),
                "an older version's count was not taken off a named stack: " + cow.getCustomName());

        net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket sent =
                com.frikinjay.mobstacker.StackLabels.withLabelInName(cow,
                        new net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket(cow.getId(), List.of()));
        int nameId = com.frikinjay.mobstacker.mixin.EntityAccessor.mobstacker$customNameData().getId();
        String shown = sent.packedItems().stream().filter(value -> value.id() == nameId)
                .map(value -> ((java.util.Optional<net.minecraft.network.chat.Component>) value.value())
                        .map(net.minecraft.network.chat.Component::getString).orElse(""))
                .findFirst().orElse("");
        check(report, shown.equals("Bessie x5"), "a game without the mod is not sent the label in the name: '" + shown + "'");
        check(report, cow.getCustomName().getString().equals("Bessie"), "sending the label changed the mob's own name");
        cow.setCustomName(null);
    }

    /**
     * 1.12.0 (#35): a mob made out of a stack is a copy of the stack's saved data, less what is the top
     * mob's alone. None of these mobs is ever added to the world.
     */
    private static void testMemberCopies(Report report, CommandSourceStack source) {
        net.minecraft.server.level.ServerLevel level = source.getLevel();
        net.minecraft.world.entity.monster.Zombie zombie = EntityType.ZOMBIE.create(level);
        if (zombie == null) {
            check(report, false, "could not create a zombie for the member-copy check");
            return;
        }
        // What a stack has and every member has with it...
        zombie.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR).addPermanentModifier(
                new net.minecraft.world.entity.ai.attributes.AttributeModifier("selftest bonus", 3.0,
                        net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION));
        zombie.addTag("selftest_member");
        zombie.setRemainingFireTicks(100);
        zombie.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 600));
        zombie.setBaby(true);
        zombie.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_HELMET));
        zombie.setCustomName(net.minecraft.network.chat.Component.literal("Zed"));
        // ...and what is the top zombie's own.
        zombie.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).addPermanentModifier(
                new net.minecraft.world.entity.ai.attributes.AttributeModifier("Leader zombie bonus", 2.0,
                        net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.MULTIPLY_TOTAL));
        zombie.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_SWORD));
        zombie.setGuaranteedDrop(net.minecraft.world.entity.EquipmentSlot.MAINHAND);
        MobStacker.setStackSize(zombie, 5);
        zombie.setHealth(3.0F);

        if (!(MobStacker.memberOf(zombie, level, false) instanceof net.minecraft.world.entity.monster.Zombie member)) {
            check(report, false, "memberOf made no zombie out of a stack of zombies");
            return;
        }
        check(report, !member.getUUID().equals(zombie.getUUID()), "a member copied the stack's UUID");
        check(report, MobStacker.getStackSize(member) == 1, "a member came out as a stack of " + MobStacker.getStackSize(member));
        check(report, ((com.frikinjay.mobstacker.ICustomDataHolder) member).mobstacker$getCustomData()
                        != ((com.frikinjay.mobstacker.ICustomDataHolder) zombie).mobstacker$getCustomData(),
                "a member shares the stack's own stack data");
        check(report, member.isBaby(), "a baby zombie stack let out an adult");
        check(report, member.getTags().contains("selftest_member"), "a member lost the stack's scoreboard tag");
        check(report, member.getRemainingFireTicks() > 0, "a member of a burning stack is not burning");
        check(report, member.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN), "a member lost the stack's effect");
        check(report, member.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR)
                        == zombie.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR),
                "a member lost an attribute modifier the stack has");
        check(report, member.getMaxHealth() == 20.0F, "a member kept the top zombie's leader bonus: max health " + member.getMaxHealth());
        check(report, member.getHealth() == member.getMaxHealth(), "a member came out hurt: " + member.getHealth());
        check(report, member.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND).isEmpty(),
                "a member got a copy of the sword the top zombie picked up");
        check(report, member.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).is(net.minecraft.world.item.Items.IRON_HELMET),
                "a member lost the helmet zombies of this stack spawn with");
        check(report, member.getCustomName() == null, "a member taken out of a stack took the stack's name");
        net.minecraft.world.entity.Mob remainder = MobStacker.memberOf(zombie, level, true);
        check(report, remainder != null && remainder.getCustomName() != null && remainder.getCustomName().getString().equals("Zed"),
                "the remainder of a named stack lost its name");

        // An enderman's block, a creeper's fuse (but not its charge), a turtle's egg (but not its home).
        net.minecraft.world.entity.monster.EnderMan enderman = EntityType.ENDERMAN.create(level);
        if (enderman != null) {
            enderman.setCarriedBlock(net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState());
            net.minecraft.world.entity.Mob copy = MobStacker.memberOf(enderman, level, false);
            check(report, copy instanceof net.minecraft.world.entity.monster.EnderMan carrying && carrying.getCarriedBlock() == null,
                    "a member of an enderman stack got the top one's block");
        }
        net.minecraft.world.entity.monster.Creeper creeper = EntityType.CREEPER.create(level);
        if (creeper != null) {
            net.minecraft.nbt.CompoundTag charged = creeper.saveWithoutId(new net.minecraft.nbt.CompoundTag());
            charged.putBoolean("powered", true);
            charged.putBoolean("ignited", true);
            charged.remove(MobStacker.STACK_DATA_KEY);
            creeper.load(charged);
            net.minecraft.world.entity.Mob copy = MobStacker.memberOf(creeper, level, true);
            check(report, copy instanceof net.minecraft.world.entity.monster.Creeper next && next.isPowered() && !next.isIgnited(),
                    "the remainder of an exploding charged creeper stack is not charged, or is lit too");
        }
        net.minecraft.world.entity.animal.Turtle turtle = EntityType.TURTLE.create(level);
        if (turtle != null) {
            turtle.setHomePos(new net.minecraft.core.BlockPos(1234, 64, -987));
            net.minecraft.nbt.CompoundTag egg = turtle.saveWithoutId(new net.minecraft.nbt.CompoundTag());
            egg.putBoolean("HasEgg", true);
            egg.remove(MobStacker.STACK_DATA_KEY);
            turtle.load(egg);
            net.minecraft.world.entity.Mob copy = MobStacker.memberOf(turtle, level, false);
            net.minecraft.nbt.CompoundTag saved = copy == null ? new net.minecraft.nbt.CompoundTag()
                    : copy.saveWithoutId(new net.minecraft.nbt.CompoundTag());
            check(report, saved.getInt("HomePosX") == 1234 && saved.getInt("HomePosZ") == -987,
                    "a member of a turtle stack got a new home: " + saved.getInt("HomePosX") + ", " + saved.getInt("HomePosZ"));
            check(report, !saved.getBoolean("HasEgg"), "a member of a turtle stack got the top one's egg");
        }

        // A villager's job and its claims, a pig's saddle, a cow in love, a sheep's wool.
        net.minecraft.world.entity.npc.Villager villager = EntityType.VILLAGER.create(level);
        if (villager != null) {
            villager.setVillagerData(villager.getVillagerData()
                    .setProfession(net.minecraft.world.entity.npc.VillagerProfession.FARMER).setLevel(2));
            villager.setVillagerXp(30);
            villager.getBrain().setMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.HOME,
                    net.minecraft.core.GlobalPos.of(level.dimension(), new net.minecraft.core.BlockPos(1, 2, 3)));
            net.minecraft.world.entity.Mob copy = MobStacker.memberOf(villager, level, false);
            check(report, copy instanceof net.minecraft.world.entity.npc.Villager jobless
                            && jobless.getVillagerData().getProfession() == net.minecraft.world.entity.npc.VillagerProfession.NONE
                            && jobless.getVillagerXp() == 0,
                    "a member of a villager stack got the top one's job");
            check(report, copy != null && copy.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.HOME).isEmpty(),
                    "a member of a villager stack got the top one's bed");
        }
        net.minecraft.world.entity.animal.Pig pig = EntityType.PIG.create(level);
        if (pig != null) {
            pig.equipSaddle(null);
            net.minecraft.world.entity.Mob copy = MobStacker.memberOf(pig, level, false);
            check(report, copy instanceof net.minecraft.world.entity.animal.Pig bare && !bare.isSaddled(),
                    "a member of a pig stack got a copy of the top one's saddle");
        }
        net.minecraft.world.entity.animal.Sheep sheep = EntityType.SHEEP.create(level);
        if (sheep != null) {
            sheep.setColor(net.minecraft.world.item.DyeColor.LIME);
            sheep.setSheared(true);
            sheep.setInLove(null);
            net.minecraft.world.entity.Mob copy = MobStacker.memberOf(sheep, level, false);
            check(report, copy instanceof net.minecraft.world.entity.animal.Sheep same
                            && same.getColor() == net.minecraft.world.item.DyeColor.LIME && same.isSheared(),
                    "a member of a sheared lime sheep stack came out another colour or with its wool");
            check(report, copy instanceof net.minecraft.world.entity.animal.Animal animal && !animal.isInLove(),
                    "a member of a stack in love came out in love");
        }

        // stackHealth: the stack's max health is every member's together; a member has one mob's.
        net.minecraft.world.entity.animal.Cow cow = EntityType.COW.create(level);
        if (cow != null) {
            boolean wasWhole = MobStacker.config.getKillWholeStackOnDeath();
            boolean wasPooled = MobStacker.config.getStackHealth();
            MobStacker.config.setKillWholeStackOnDeath(true);
            MobStacker.config.setStackHealth(true);
            cow.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(50.0);
            net.minecraft.world.entity.Mob copy = MobStacker.memberOf(cow, level, false);
            check(report, copy != null && copy.getMaxHealth() == 10.0F,
                    "under stackHealth a member came out with the stack's max health: " + (copy == null ? 0 : copy.getMaxHealth()));
            MobStacker.config.setStackHealth(wasPooled);
            MobStacker.config.setKillWholeStackOnDeath(wasWhole);
        }

        // #58: a saddle on a stacked pig hands one pig over, like a horse.
        net.minecraft.world.entity.animal.Pig rideable = EntityType.PIG.create(level);
        if (rideable != null) {
            net.minecraft.world.item.ItemStack saddle = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.SADDLE);
            check(report, MobStacker.isSaddlingInteraction(rideable, saddle), "a saddle on a stacked pig does not hand one pig over");
            rideable.equipSaddle(null);
            check(report, !MobStacker.isSaddlingInteraction(rideable, saddle), "a saddle on a pig that has one still counts as saddling");
        }
        // #57: what a mob merged away picked up is found by its drop chance; what it spawned with stays.
        net.minecraft.world.entity.monster.Zombie picker = EntityType.ZOMBIE.create(level);
        if (picker != null) {
            // Cursed with vanishing, so the check leaves no sword lying in the world.
            net.minecraft.world.item.ItemStack sword = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_SWORD);
            sword.enchant(net.minecraft.world.item.enchantment.Enchantments.VANISHING_CURSE, 1);
            picker.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, sword);
            picker.setGuaranteedDrop(net.minecraft.world.entity.EquipmentSlot.MAINHAND);
            picker.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_HELMET));
            MobStacker.dropPickedEquipment(picker);
            check(report, picker.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND).isEmpty(),
                    "a sword a mob picked up stayed on it as it was merged away");
            check(report, picker.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).is(net.minecraft.world.item.Items.IRON_HELMET),
                    "dropPickedEquipment took the helmet a mob spawned with");
        }
    }

    private static int count(Iterable<?> items) {
        int n = 0;
        for (Object ignored : items) {
            n++;
        }
        return n;
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
                "minecraft:overworld", false, 0, 0, 0, 4, 4, 4, null, true);
        check(report, created.ok(), "a new region was refused: " + created.message());

        RegionEdit.Result again = RegionEdit.apply(name, StackRegion.Type.ALLOW,
                "minecraft:overworld", false, 100, 0, 100, 104, 4, 104, null, true);
        check(report, !again.ok(), "'New region' overwrote a region that already existed");
        StackRegion kept = MobStacker.config.getRegion(name);
        check(report, kept != null && kept.getMinX() == 0,
                "the refused create moved the existing region anyway");

        RegionEdit.Result reshaped = RegionEdit.apply(name, StackRegion.Type.ALLOW,
                "minecraft:overworld", false, 100, 0, 100, 104, 4, 104, null, false);
        check(report, reshaped.ok(), "redrawing an existing region was refused: " + reshaped.message());
        StackRegion moved = MobStacker.config.getRegion(name);
        check(report, moved != null && moved.getMinX() == 100,
                "redrawing an existing region did not move it");

        RegionEdit.delete(name);
        check(report, MobStacker.config.getRegion(name) == null,
                "the self-test's own region survived being deleted");

        testRegionCorners(report);
        testRegionSwitchAndDimension(report);
    }

    /**
     * 1.12.0: a region switched off is not there for anything, and a whole-dimension region covers
     * every block of its dimension and nothing of any other. Both ride {@code StackRegion.contains},
     * so that is what is checked, plus the GUI's wire form and what the config file says.
     */
    private static void testRegionSwitchAndDimension(Report report) {
        String name = "selftest_switch";
        RegionEdit.delete(name);
        RegionEdit.apply(name, StackRegion.Type.ALLOW, "minecraft:overworld", 0, 0, 0, 4, 4, 4);
        StackRegion region = MobStacker.config.getRegion(name);
        if (region == null) {
            check(report, false, "could not create the region for the on/off check");
            return;
        }
        String jsonOn = new com.google.gson.Gson().toJson(region);
        check(report, !jsonOn.contains("disabled") && !jsonOn.contains("wholeDimension"),
                "a region that is on and a box writes new keys to the config: " + jsonOn);
        check(report, RegionEdit.setEnabled(name, false).ok() && !region.isEnabled()
                        && !region.contains("minecraft:overworld", 2, 2, 2),
                "a region switched off still covers its blocks");
        check(report, new com.google.gson.Gson().toJson(region).contains("\"disabled\":true"),
                "a region switched off does not say so in the config");
        check(report, !RegionEdit.setEnabled(name, false).ok(), "switching a region off twice reported a change");
        check(report, RegionEdit.setEnabled(name, true).ok() && region.contains("minecraft:overworld", 2, 2, 2),
                "a region switched back on does not cover its blocks again");

        RegionEdit.Result whole = RegionEdit.applyWholeDimension(name, StackRegion.Type.ALLOW, "minecraft:the_nether");
        check(report, whole.ok() && region.isWholeDimension()
                        && region.contains("minecraft:the_nether", 2_000_000, 200, -2_000_000)
                        && !region.contains("minecraft:overworld", 2, 2, 2),
                "a whole-dimension region does not cover exactly its dimension: " + whole.message());
        check(report, java.util.Arrays.equals(region.getCorners(), new int[]{0, 0, 0, 4, 4, 4}),
                "making a region its whole dimension lost the box it had");
        check(report, region.volume() == Long.MAX_VALUE, "a whole dimension is not bigger than every box");
        RegionEdit.apply(name, StackRegion.Type.ALLOW, "minecraft:overworld", 0, 0, 0, 4, 4, 4);
        check(report, !region.isWholeDimension() && !region.contains("minecraft:overworld", 9, 2, 2),
                "giving a whole-dimension region corners did not make it a box again");
        RegionEdit.delete(name);

        RegionEdit.applyWholeDimension(name, StackRegion.Type.DENY, "minecraft:the_end");
        StackRegion fresh = MobStacker.config.getRegion(name);
        check(report, fresh != null && fresh.isWholeDimension()
                        && java.util.Arrays.equals(fresh.getCorners(), StackRegion.WHOLE_DIMENSION_CORNERS),
                "a new whole-dimension region did not start with the whole-world corners");
        int[] c = StackRegion.WHOLE_DIMENSION_CORNERS;
        // What an older version computes for it, knowing nothing of the flag: has to stay positive.
        long oldVolume = (long) (c[3] - c[0] + 1) * (c[4] - c[1] + 1) * (c[5] - c[2] + 1);
        check(report, oldVolume > 0, "an older version would read the whole-world corners with an overflowing volume");
        RegionEdit.delete(name);

        RegionEdit.Definition sent = new RegionEdit.Definition(StackRegion.Type.DENY, "minecraft:the_nether",
                1, 2, 3, 4, 5, 6, true, List.of("minecraft:plains", "#minecraft:is_forest"));
        RegionEdit.Definition read = RegionEdit.Definition.decode(sent.encode());
        check(report, sent.equals(read), "a whole-dimension region does not survive the GUI's wire form: " + sent.encode());
        RegionEdit.Definition cleared = RegionEdit.Definition.decode("ALLOW;minecraft:overworld;1,2,3;4,5,6;biomes=");
        check(report, cleared != null && cleared.biomes() != null && cleared.biomes().isEmpty(),
                "an empty biome part does not clear the filter");
        RegionEdit.Definition old = RegionEdit.Definition.decode("ALLOW;minecraft:overworld;1,2,3;4,5,6");
        check(report, old != null && !old.wholeDimension() && old.biomes() == null,
                "the GUI's old wire form no longer reads as a box that keeps its biomes");

        // The biome filter, as text.
        check(report, RegionBiomes.parseList("plains,  #is_forest minecraft:plains").equals(
                        List.of("minecraft:plains", "#minecraft:is_forest")),
                "the biome box is not read as normalised entries without repeats: "
                        + RegionBiomes.parseList("plains,  #is_forest minecraft:plains"));
        check(report, RegionBiomes.entryProblem("#Bad Tag!") != null && RegionBiomes.entryProblem("modded:swamp_x") == null,
                "biome entries are not judged by their shape");
        RegionEdit.apply(name, StackRegion.Type.ALLOW, "minecraft:overworld", 0, 0, 0, 4, 4, 4);
        check(report, RegionEdit.editBiomes(name, List.of("plains"), true).ok()
                        && MobStacker.config.getRegion(name).getBiomes().equals(List.of("minecraft:plains")),
                "a biome could not be added to a region");
        check(report, !RegionEdit.editBiomes(name, List.of("minecraft:plains"), true).ok(),
                "adding a biome a region already has reported a change");
        check(report, !RegionEdit.editBiomes(name, List.of("not a biome!"), true).ok(),
                "a malformed biome was added to a region");
        RegionEdit.apply(name, StackRegion.Type.ALLOW, "minecraft:overworld", 1, 1, 1, 5, 5, 5);
        check(report, MobStacker.config.getRegion(name).hasBiomeFilter(), "reshaping a region by command dropped its biomes");
        check(report, RegionEdit.editBiomes(name, List.of("minecraft:plains"), false).ok()
                        && !MobStacker.config.getRegion(name).hasBiomeFilter(),
                "taking a region's last biome off did not make it cover every biome");
        RegionEdit.delete(name);
        check(report, RegionEdit.Definition.decode("ALLOW;minecraft:overworld;1,2,3;4,5,6;nonsense") == null,
                "an unknown trailing part of a region definition was accepted");
    }

    /**
     * A region keeps its two corners the way they were given, and still covers the same blocks.
     *
     * <p>The corners used to be sorted into a minimum and a maximum on the way in, so the editor
     * showed back two blocks nobody had clicked. Min and max are still what decides containment -
     * and what an older version of the mod reads - so both halves are checked, plus a region saved
     * before the corners were kept, which has to fall back rather than come back empty.
     */
    private static void testRegionCorners(Report report) {
        StackRegion region = new StackRegion("selftest", "minecraft:overworld",
                StackRegion.Type.ALLOW, 5, 1, 9, 2, 7, 3);
        check(report, java.util.Arrays.equals(region.getCorners(), new int[]{5, 1, 9, 2, 7, 3}),
                "a region did not keep its corners as given: " + java.util.Arrays.toString(region.getCorners()));
        check(report, region.getMinX() == 2 && region.getMaxX() == 5
                        && region.getMinY() == 1 && region.getMaxY() == 7
                        && region.getMinZ() == 3 && region.getMaxZ() == 9,
                "a region given its corners in reverse got the wrong extent");
        check(report, region.contains("minecraft:overworld", 3, 4, 5)
                        && !region.contains("minecraft:overworld", 6, 4, 5),
                "a region given its corners in reverse does not cover the right blocks");
        check(report, region.describeBounds().equals("[5, 1, 9] -> [2, 7, 3]"),
                "a region describes its corners sorted: " + region.describeBounds());

        StackRegion legacy = new com.google.gson.Gson().fromJson(
                "{\"name\":\"old\",\"dimension\":\"minecraft:overworld\",\"type\":\"ALLOW\","
                        + "\"minX\":0,\"minY\":1,\"minZ\":2,\"maxX\":3,\"maxY\":4,\"maxZ\":5}",
                StackRegion.class);
        check(report, java.util.Arrays.equals(legacy.getCorners(), new int[]{0, 1, 2, 3, 4, 5}),
                "a region saved before corners were kept did not fall back to min and max");
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

    /**
     * Who sees a region while regions are private: admins, its owner, the players it is shared with,
     * and everybody once it - or the whole server - is public. And the owner and the list survive the
     * trip to the config file, or a restart would hand every farm back to nobody.
     */
    private static void testRegionPrivacy(Report report) {
        StackRegion region = new StackRegion("selftest", "minecraft:overworld",
                StackRegion.Type.ALLOW, 0, 0, 0, 4, 4, 4);
        UUID owner = UUID.randomUUID();
        UUID friend = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();
        region.setOwner(owner, "Owner");
        region.share(friend, "Friend");
        MobStacker.config.setPrivateRegions(true);
        check(report, MobStacker.regionVisible(region, owner, false), "a region's owner does not see it");
        check(report, MobStacker.regionVisible(region, friend, false), "a player it was shared with does not see it");
        check(report, !MobStacker.regionVisible(region, stranger, false), "a private region is shown to a stranger");
        check(report, MobStacker.regionVisible(region, stranger, true), "an admin does not see a private region");

        region.setSetting("privateRegions", "false");
        check(report, MobStacker.regionVisible(region, stranger, false), "a region made public is still hidden");
        region.clearSetting("privateRegions");
        MobStacker.config.setPrivateRegions(false);
        check(report, MobStacker.regionVisible(region, stranger, false), "privateRegions off still hides a region");
        MobStacker.config.setPrivateRegions(true);

        StackRegion reloaded = new Gson().fromJson(new Gson().toJson(region), StackRegion.class);
        check(report, reloaded.isOwnedBy(owner) && "Owner".equals(reloaded.getOwnerName()),
                "a region's owner did not survive being saved");
        check(report, reloaded.isSharedWith(friend) && !reloaded.isSharedWith(stranger),
                "a region's shared list did not survive being saved");

        region.unshare(friend);
        check(report, !MobStacker.regionVisible(region, friend, false), "a player taken off the list still sees the region");
        region.setOwner(null, null);
        check(report, !MobStacker.regionVisible(region, owner, false), "a former owner still sees the region");

        // A region marked private keeps the mark while the server agrees with it, so switching the
        // server to public does not take the region along (found by the scripted test: the save
        // pruned the override as redundant).
        StackRegion pinned = new StackRegion("selftest_private", "minecraft:overworld",
                StackRegion.Type.ALLOW, 0, 0, 0, 4, 4, 4);
        pinned.setSetting("privateRegions", "true");
        MobStacker.config.addRegion(pinned);
        try {
            MobStacker.config.setPrivateRegions(true);
            MobStacker.config.setPrivateRegions(false);
            check(report, MobStacker.regionIsPrivate(pinned), "a region made private became public with the server");
        } finally {
            MobStacker.config.removeRegion("selftest_private");
            MobStacker.config.setPrivateRegions(true);
        }
    }

    /**
     * The /mobstacker tree as a player who is no operator sees it: nothing at all, and for the owner of
     * a region {@code region share} and nothing else. Every branch asks for an operator by itself
     * since the root lets owners in, so one branch added without that would be open to every owner.
     */
    private static void testCommandAccess(Report report, CommandSourceStack source) {
        CommandNode<CommandSourceStack> root = source.getServer().getCommands().getDispatcher()
                .getRoot().getChild(MobStacker.MOD_ID);
        check(report, root != null, "the /mobstacker command is not registered");
        if (root == null) {
            return;
        }
        // Stand-ins for two players: what the tree looks at is the entity's UUID.
        ArmorStand stranger = new ArmorStand(EntityType.ARMOR_STAND, source.getLevel());
        ArmorStand owner = new ArmorStand(EntityType.ARMOR_STAND, source.getLevel());
        StackRegion region = new StackRegion("selftest_owned", "minecraft:overworld",
                StackRegion.Type.ALLOW, 0, 0, 0, 4, 4, 4);
        region.setOwner(owner.getUUID(), "Owner");
        MobStacker.config.addRegion(region);
        try {
            CommandSourceStack asStranger = source.withEntity(stranger).withPermission(0);
            CommandSourceStack asOwner = source.withEntity(owner).withPermission(0);
            check(report, !root.canUse(asStranger), "/mobstacker is open to a player who owns no region");
            check(report, root.canUse(asOwner), "/mobstacker is closed to a region owner");
            for (CommandNode<CommandSourceStack> child : root.getChildren()) {
                if (!"region".equals(child.getName())) {
                    check(report, !child.canUse(asOwner), "/mobstacker " + child.getName() + " is open to a region owner");
                    continue;
                }
                for (CommandNode<CommandSourceStack> regionChild : child.getChildren()) {
                    boolean share = "share".equals(regionChild.getName());
                    check(report, regionChild.canUse(asOwner) == share,
                            "/mobstacker region " + regionChild.getName() + (share ? " is closed to" : " is open to")
                                    + " a region owner");
                }
            }
        } finally {
            MobStacker.config.removeRegion("selftest_owned");
        }
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
