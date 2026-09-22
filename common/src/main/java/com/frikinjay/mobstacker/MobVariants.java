package com.frikinjay.mobstacker;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.animal.Fox;
import net.minecraft.world.entity.animal.MushroomCow;
import net.minecraft.world.entity.animal.Panda;
import net.minecraft.world.entity.animal.Parrot;
import net.minecraft.world.entity.animal.Rabbit;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.animal.SnowGolem;
import net.minecraft.world.entity.animal.TropicalFish;
import net.minecraft.world.entity.animal.axolotl.Axolotl;
import net.minecraft.world.entity.animal.frog.Frog;
import net.minecraft.world.entity.animal.goat.Goat;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.animal.horse.Llama;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.monster.ZombieVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;

/**
 * The per-mob state a stack is not able to represent: a sheep's wool colour, a horse's markings,
 * a panda's genes, whether a creeper is charged.
 *
 * <p>Every entry answers two questions about the same piece of state, side by side on purpose:
 * <ul>
 *   <li><b>matches</b> — may these two mobs merge at all? A stack shows one body, so two mobs that
 *       do not look and behave alike must stay apart.</li>
 *   <li><b>copy</b> — what has to be carried onto a freshly created entity, either the remainder
 *       the stack respawns after a kill or a mob separated out of it.</li>
 * </ul>
 * When those two halves lived in different methods they drifted: horses were in neither, so a herd
 * merged regardless of colour and then respawned in a colour rolled at random by {@code
 * finalizeSpawn}. Keeping them in one row makes adding a type a single edit that cannot be half-done.
 *
 * <p>Matching runs on the stacking scan, so it reads plain getters. Copying runs only when an entity
 * is created, which is rare, so the few types vanilla gives no public setter for fall back to
 * {@link #copyNbtKeys} instead of needing an accessor mixin each.
 */
public final class MobVariants {

    private record Rule(Class<? extends Mob> type, BiPredicate<Mob, Mob> matches, BiConsumer<Mob, Mob> copy) {}

    private static <T extends Mob> Rule rule(Class<T> type, BiPredicate<T, T> matches, BiConsumer<T, T> copy) {
        return new Rule(type,
                (a, b) -> matches.test(type.cast(a), type.cast(b)),
                (from, to) -> copy.accept(type.cast(from), type.cast(to)));
    }

    private static final List<Rule> RULES = List.of(
            rule(Sheep.class,
                    (a, b) -> a.getColor() == b.getColor() && a.isSheared() == b.isSheared(),
                    (from, to) -> {
                        to.setColor(from.getColor());
                        to.setSheared(from.isSheared());
                    }),
            rule(Villager.class,
                    // Only jobless villagers stack: a profession carries a trade list and a
                    // workstation that belong to one villager and could not survive a merge.
                    (a, b) -> a.getVariant() == b.getVariant()
                            && a.getVillagerData().getProfession() == VillagerProfession.NONE
                            && b.getVillagerData().getProfession() == VillagerProfession.NONE,
                    (from, to) -> {
                        to.setVillagerData(from.getVillagerData());
                        to.setVariant(from.getVariant());
                    }),
            rule(ZombieVillager.class,
                    (a, b) -> a.getVariant() == b.getVariant()
                            && a.getVillagerData().getProfession() == VillagerProfession.NONE
                            && b.getVillagerData().getProfession() == VillagerProfession.NONE,
                    (from, to) -> {
                        to.setVillagerData(from.getVillagerData());
                        to.setVariant(from.getVariant());
                    }),
            rule(Slime.class,
                    (a, b) -> a.getSize() == b.getSize(),
                    (from, to) -> to.setSize(from.getSize(), true)),
            rule(Frog.class,
                    (a, b) -> a.getVariant() == b.getVariant(),
                    (from, to) -> to.setVariant(from.getVariant())),
            rule(Axolotl.class,
                    (a, b) -> a.getVariant() == b.getVariant(),
                    (from, to) -> to.setVariant(from.getVariant())),
            rule(Cat.class,
                    (a, b) -> a.getVariant() == b.getVariant(),
                    (from, to) -> {
                        to.setVariant(from.getVariant());
                        to.setCollarColor(from.getCollarColor());
                    }),
            rule(Fox.class,
                    (a, b) -> a.getVariant() == b.getVariant(),
                    (from, to) -> to.setVariant(from.getVariant())),
            rule(MushroomCow.class,
                    (a, b) -> a.getVariant() == b.getVariant(),
                    // The stew effect a mooshroom was fed has no getter, so it travels as NBT.
                    (from, to) -> {
                        to.setVariant(from.getVariant());
                        copyNbtKeys(from, to, "EffectId", "EffectDuration");
                    }),
            rule(Horse.class,
                    // Colour only, which is what a herd looks like from a distance and what the
                    // testers asked to keep stacking. Markings are copied rather than matched:
                    // setVariant writes the low byte only, so the markings on anything created out
                    // of a stack used to be whatever finalizeSpawn had just rolled. The packed
                    // Variant tag carries both, so the herd's markings are the ones that come out.
                    (a, b) -> a.getVariant() == b.getVariant(),
                    (from, to) -> copyNbtKeys(from, to, "Variant")),
            rule(Llama.class,
                    // Strength is the llama's inventory size, so it is as much a variant as colour.
                    (a, b) -> a.getVariant() == b.getVariant() && a.getStrength() == b.getStrength(),
                    (from, to) -> {
                        to.setVariant(from.getVariant());
                        copyNbtKeys(from, to, "Strength");
                    }),
            rule(Rabbit.class,
                    (a, b) -> a.getVariant() == b.getVariant(),
                    (from, to) -> to.setVariant(from.getVariant())),
            rule(Parrot.class,
                    (a, b) -> a.getVariant() == b.getVariant(),
                    (from, to) -> to.setVariant(from.getVariant())),
            rule(Panda.class,
                    // The hidden gene decides what the cubs look like, so it matters even though
                    // nothing about the panda in front of you shows it.
                    (a, b) -> a.getMainGene() == b.getMainGene() && a.getHiddenGene() == b.getHiddenGene(),
                    (from, to) -> {
                        to.setMainGene(from.getMainGene());
                        to.setHiddenGene(from.getHiddenGene());
                    }),
            rule(Goat.class,
                    (a, b) -> a.isScreamingGoat() == b.isScreamingGoat()
                            && a.hasLeftHorn() == b.hasLeftHorn() && a.hasRightHorn() == b.hasRightHorn(),
                    (from, to) -> {
                        to.setScreamingGoat(from.isScreamingGoat());
                        copyNbtKeys(from, to, "HasLeftHorn", "HasRightHorn");
                    }),
            rule(TropicalFish.class,
                    // Pattern, body colour and pattern colour together make the 22 named fish;
                    // only the pattern has a public setter, so the whole lot travels as NBT.
                    (a, b) -> a.getVariant() == b.getVariant()
                            && a.getBaseColor() == b.getBaseColor()
                            && a.getPatternColor() == b.getPatternColor(),
                    (from, to) -> copyNbtKeys(from, to, "Variant")),
            rule(Creeper.class,
                    (a, b) -> a.isPowered() == b.isPowered(),
                    (from, to) -> copyNbtKeys(from, to, "powered")),
            rule(Shulker.class,
                    (a, b) -> Objects.equals(a.getColor(), b.getColor()),
                    (from, to) -> copyNbtKeys(from, to, "Color")),
            rule(SnowGolem.class,
                    (a, b) -> a.hasPumpkin() == b.hasPumpkin(),
                    (from, to) -> copyNbtKeys(from, to, "Pumpkin")),
            rule(EnderMan.class,
                    (a, b) -> Objects.equals(a.getCarriedBlock(), b.getCarriedBlock()),
                    (from, to) -> copyNbtKeys(from, to, "carriedBlockState"))
    );

    private MobVariants() {}

    /**
     * @return true when nothing about these two mobs would be lost by merging them into one stack.
     *         Types with no rule of their own carry no variant and always match.
     */
    public static boolean sameVariant(Mob self, Mob other) {
        for (Rule rule : RULES) {
            if (rule.type().isInstance(self) && rule.type().isInstance(other) && !rule.matches().test(self, other)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Carries the source's variant onto a mob that was just created by {@code EntityType.create} and
     * {@code finalizeSpawn} — which rolls a variant of its own, so this has to run afterwards.
     */
    public static void copyVariant(Mob source, Mob target) {
        for (Rule rule : RULES) {
            if (rule.type().isInstance(source) && rule.type().isInstance(target)) {
                rule.copy().accept(source, target);
            }
        }
        copyRolledAttributes(source, target);
    }

    /**
     * Attributes {@code finalizeSpawn} rolls at random, carried over rather than rolled again.
     *
     * <p>A horse's speed, jump and health are its whole point, and every horse that came out of a
     * stack was getting a fresh roll of all three — so stacking a horse and taking it out again was
     * a re-roll button, and a good horse could be turned into a better one by trying often enough.
     * Copying them makes the answer the same every time: whatever the stack has is what comes out
     * of it.
     *
     * <p>This is deliberately <em>not</em> a {@link #RULES} row. Those say "two mobs that differ
     * here must not merge", and applied to a continuous random roll that would stop wild horses
     * stacking with each other at all.
     *
     * <p>What this copies is only the fallback, since 1.9.1: the numbers of the mob on top. A stack
     * of horses now remembers every member's own numbers ({@link #MEMBER_ROLLS_KEY}), and the
     * member that comes out - or takes over the top when the top one dies - gets its own back.
     */
    private static void copyRolledAttributes(Mob source, Mob target) {
        if (!(source instanceof AbstractHorse) || !(target instanceof AbstractHorse)) {
            return;
        }
        for (Attribute attribute : ROLLED_HORSE_ATTRIBUTES) {
            AttributeInstance from = source.getAttribute(attribute);
            AttributeInstance to = target.getAttribute(attribute);
            if (from != null && to != null) {
                to.setBaseValue(from.getBaseValue());
            }
        }
        // Max health was just moved under it, and a mob that comes out of a stack comes out whole -
        // leaving the number where it was would hand back a horse that is wounded or bursting,
        // depending on which way the roll happened to go.
        target.setHealth(target.getMaxHealth());
    }

    /** Exactly the three {@code AbstractHorse.randomizeAttributes} rolls, and nothing else. */
    private static final List<Attribute> ROLLED_HORSE_ATTRIBUTES =
            List.of(Attributes.MAX_HEALTH, Attributes.MOVEMENT_SPEED, Attributes.JUMP_STRENGTH);

    // --- Each member's own rolls ----------------------------------------------------------------
    // Until 1.9.1 a stack of horses had one set of numbers, the top horse's, and every horse that
    // came out of it got those. That lost a good horse merged into a herd of poor ones, and - the
    // worse half - turned one good horse on top of a herd into a herd of good horses, one separation
    // at a time. Each member's numbers now travel with the stack, the way each member's equipment
    // does, in the same order: the member next in line first.

    /** Key in a stack's custom data: the rolls of the members under the top horse, next first. */
    public static final String MEMBER_ROLLS_KEY = "MemberRolls";

    /** A horse's three rolls, keyed by attribute id. Null for anything that is not a horse. */
    private static CompoundTag captureRolls(Mob mob) {
        if (!(mob instanceof AbstractHorse)) {
            return null;
        }
        CompoundTag rolls = new CompoundTag();
        for (Attribute attribute : ROLLED_HORSE_ATTRIBUTES) {
            AttributeInstance instance = mob.getAttribute(attribute);
            if (instance != null) {
                rolls.putDouble(attributeKey(attribute), instance.getBaseValue());
            }
        }
        return rolls;
    }

    /** Gives a horse a member's rolls back, at full health - a mob comes out of a stack whole. */
    private static void applyRolls(Mob mob, CompoundTag rolls) {
        for (Attribute attribute : ROLLED_HORSE_ATTRIBUTES) {
            AttributeInstance instance = mob.getAttribute(attribute);
            String key = attributeKey(attribute);
            if (instance != null && rolls.contains(key)) {
                instance.setBaseValue(rolls.getDouble(key));
            }
        }
        mob.setHealth(mob.getMaxHealth());
    }

    private static String attributeKey(Attribute attribute) {
        return String.valueOf(BuiltInRegistries.ATTRIBUTE.getKey(attribute));
    }

    /** The rolls stored on a stack, exactly as stored. A copy; never null. */
    private static ListTag storedRolls(Mob mob) {
        if (!(mob instanceof ICustomDataHolder holder)) {
            return new ListTag();
        }
        CompoundTag data = holder.mobstacker$getCustomData();
        return data.contains(MEMBER_ROLLS_KEY, Tag.TAG_LIST)
                ? data.getList(MEMBER_ROLLS_KEY, Tag.TAG_COMPOUND).copy() : new ListTag();
    }

    private static void storeRolls(Mob mob, ListTag rolls, int stackSize) {
        if (!(mob instanceof ICustomDataHolder holder)) {
            return;
        }
        while (rolls.size() > Math.max(0, stackSize - 1)) {
            rolls.remove(rolls.size() - 1);
        }
        CompoundTag data = holder.mobstacker$getCustomData();
        if (rolls.isEmpty()) {
            data.remove(MEMBER_ROLLS_KEY);
        } else {
            data.put(MEMBER_ROLLS_KEY, rolls);
        }
    }

    /**
     * The rolls of every member under the top, one per member. A stack that formed before 1.9.1
     * recorded none, and its members are filled in with the top horse's numbers - which is exactly
     * what every one of them would have come out with before, so nothing changes for it.
     */
    private static ListTag memberRolls(Mob mob) {
        ListTag rolls = storedRolls(mob);
        int members = Math.max(0, MobStacker.getStackSize(mob) - 1);
        CompoundTag own = captureRolls(mob);
        while (rolls.size() < members && own != null) {
            rolls.add(own.copy());
        }
        while (rolls.size() > members) {
            rolls.remove(rolls.size() - 1);
        }
        return rolls;
    }

    /**
     * Before a merge: the list the merged stack should carry - the target's members, then the
     * source's top horse, then the source's members. Taken before the target is reloaded from its
     * snapshot, which puts its own custom data back. Null when these are not horses.
     */
    public static ListTag mergedMemberRolls(Mob target, Mob source) {
        if (!(target instanceof AbstractHorse) || !(source instanceof AbstractHorse)) {
            return null;
        }
        ListTag merged = memberRolls(target);
        merged.add(captureRolls(source));
        merged.addAll(memberRolls(source));
        return merged;
    }

    /** After a merge: see {@link #mergedMemberRolls}. */
    public static void setMergedMemberRolls(Mob target, ListTag merged, int newStackSize) {
        if (merged != null) {
            storeRolls(target, merged, newStackSize);
        }
    }

    /**
     * The horse taken out of a stack is the member next in line, with that member's own numbers.
     * A stack that recorded none (formed before 1.9.1) hands out the top horse's, as it always did.
     * Called once the stack is already one smaller.
     */
    public static void takeMemberRollsFor(Mob stack, Mob separated) {
        if (!(stack instanceof AbstractHorse) || !(separated instanceof AbstractHorse)) {
            return;
        }
        ListTag rolls = storedRolls(stack);
        if (rolls.isEmpty()) {
            return;
        }
        applyRolls(separated, rolls.getCompound(0));
        rolls.remove(0);
        storeRolls(stack, rolls, MobStacker.getStackSize(stack));
    }

    /**
     * After the top of a stack was killed: the new top horse is the member next in line after
     * every one that died, with its own numbers, and the rest keep theirs. The dead are the front of
     * the list (the top first, then the next in line), so with {@code n} rolls recorded the new top
     * is the one at {@code n - newStackSize}.
     */
    public static void handOverMemberRolls(Mob dead, Mob survivor, int newStackSize) {
        if (!(dead instanceof AbstractHorse) || !(survivor instanceof AbstractHorse)) {
            return;
        }
        ListTag rolls = storedRolls(dead);
        if (rolls.isEmpty()) {
            return; // nothing recorded: the survivor keeps the numbers it was copied, as before
        }
        int top = rolls.size() - newStackSize;
        ListTag rest = new ListTag();
        if (top >= 0) {
            applyRolls(survivor, rolls.getCompound(top));
            for (int i = top + 1; i < rolls.size(); i++) {
                rest.add(rolls.getCompound(i).copy());
            }
        } else {
            // Fewer recorded than survived (the stack size was set by hand): keep what there is.
            rest.addAll(rolls);
        }
        storeRolls(survivor, rest, newStackSize);
    }

    /**
     * Moves a handful of save keys from one entity to another, for state vanilla exposes no setter
     * for. The target is round-tripped through its own save tag, so every key not named here is left
     * exactly as it was. A key missing on the source is removed from the target rather than ignored:
     * an uncharged creeper must be able to overwrite a charged one, not only the other way round.
     */
    private static void copyNbtKeys(Mob source, Mob target, String... keys) {
        CompoundTag sourceNbt = new CompoundTag();
        source.saveWithoutId(sourceNbt);
        CompoundTag targetNbt = new CompoundTag();
        target.saveWithoutId(targetNbt);

        boolean changed = false;
        for (String key : keys) {
            Tag value = sourceNbt.get(key);
            if (value != null) {
                targetNbt.put(key, value.copy());
                changed = true;
            } else if (targetNbt.contains(key)) {
                targetNbt.remove(key);
                changed = true;
            }
        }
        if (changed) {
            target.load(targetNbt);
        }
    }

    /**
     * Every mob type that carries a variant rule, for the docs and for tests that want to know what
     * is covered without reaching into the table.
     */
    public static List<Class<? extends Mob>> coveredTypes() {
        List<Class<? extends Mob>> types = new ArrayList<>(RULES.size());
        for (Rule rule : RULES) {
            types.add(rule.type());
        }
        return types;
    }
}
