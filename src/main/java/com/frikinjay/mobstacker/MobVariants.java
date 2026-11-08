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
import java.util.function.BiPredicate;

/**
 * The per-mob state that keeps two mobs apart: a sheep's wool colour, a horse's coat, a panda's
 * genes, whether a creeper is charged. A stack shows one body, so two mobs that do not look and
 * behave alike must not merge.
 *
 * <p>Up to 1.11 every row also said what to carry onto a mob made out of a stack, because that mob
 * was a new one, put through {@code finalizeSpawn}, which rolls a variant of its own. Since 1.12.0 a
 * mob made out of a stack is a copy of the stack's saved data ({@code MobStacker.memberOf}), so its
 * variant is the stack's without anything here having to know about it - and a new mod's mob keeps
 * its variant too.
 *
 * <p>Matching runs on the stacking scan, so it reads plain getters.
 */
public final class MobVariants {

    /** @param what what differs when {@code matches} fails, for {@code /mobstacker inspect} to say */
    private record Rule(Class<? extends Mob> type, String what, BiPredicate<Mob, Mob> matches) {}

    private static <T extends Mob> Rule rule(Class<T> type, String what, BiPredicate<T, T> matches) {
        return new Rule(type, what, (a, b) -> matches.test(type.cast(a), type.cast(b)));
    }

    private static final List<Rule> RULES = List.of(
            rule(Sheep.class, "wool colour, or one of them is sheared",
                    (a, b) -> a.getColor() == b.getColor() && a.isSheared() == b.isSheared()),
            rule(Villager.class, "villager type, or one of them has a profession",
                    // Only jobless villagers stack: a profession carries a trade list and a
                    // workstation that belong to one villager and could not survive a merge.
                    (a, b) -> a.getVariant() == b.getVariant()
                            && a.getVillagerData().getProfession() == VillagerProfession.NONE
                            && b.getVillagerData().getProfession() == VillagerProfession.NONE),
            rule(ZombieVillager.class, "villager type, or one of them has a profession",
                    (a, b) -> a.getVariant() == b.getVariant()
                            && a.getVillagerData().getProfession() == VillagerProfession.NONE
                            && b.getVillagerData().getProfession() == VillagerProfession.NONE),
            rule(Slime.class, "size",
                    (a, b) -> a.getSize() == b.getSize()),
            rule(Frog.class, "variant",
                    (a, b) -> a.getVariant() == b.getVariant()),
            rule(Axolotl.class, "colour",
                    (a, b) -> a.getVariant() == b.getVariant()),
            rule(Cat.class, "coat",
                    (a, b) -> a.getVariant() == b.getVariant()),
            rule(Fox.class, "coat",
                    (a, b) -> a.getVariant() == b.getVariant()),
            rule(MushroomCow.class, "colour",
                    (a, b) -> a.getVariant() == b.getVariant()),
            rule(Horse.class, "coat colour",
                    // Colour only, which is what a herd looks like from a distance and what the
                    // testers asked to keep stacking. The markings are the stack's, like the rest.
                    (a, b) -> a.getVariant() == b.getVariant()),
            rule(Llama.class, "colour or strength",
                    // Strength is the llama's inventory size, so it is as much a variant as colour.
                    (a, b) -> a.getVariant() == b.getVariant() && a.getStrength() == b.getStrength()),
            rule(Rabbit.class, "coat",
                    (a, b) -> a.getVariant() == b.getVariant()),
            rule(Parrot.class, "colour",
                    (a, b) -> a.getVariant() == b.getVariant()),
            rule(Panda.class, "genes",
                    // The hidden gene decides what the cubs look like, so it matters even though
                    // nothing about the panda in front of you shows it.
                    (a, b) -> a.getMainGene() == b.getMainGene() && a.getHiddenGene() == b.getHiddenGene()),
            rule(Goat.class, "horns, or one of them screams",
                    (a, b) -> a.isScreamingGoat() == b.isScreamingGoat()
                            && a.hasLeftHorn() == b.hasLeftHorn() && a.hasRightHorn() == b.hasRightHorn()),
            rule(TropicalFish.class, "pattern or colours",
                    // Pattern, body colour and pattern colour together make the 22 named fish.
                    (a, b) -> a.getVariant() == b.getVariant()
                            && a.getBaseColor() == b.getBaseColor()
                            && a.getPatternColor() == b.getPatternColor()),
            rule(Creeper.class, "one of them is charged",
                    (a, b) -> a.isPowered() == b.isPowered()),
            rule(Shulker.class, "colour",
                    (a, b) -> Objects.equals(a.getColor(), b.getColor())),
            rule(SnowGolem.class, "one of them has lost its pumpkin",
                    (a, b) -> a.hasPumpkin() == b.hasPumpkin()),
            // An enderman carrying a block never stacks at all (StackRefusal.CARRIES_BLOCK), and one
            // that picks a block up while stacked leaves the stack with it (MobStacker#shedClaims).
            // This rule is the second lock on the same door. The block is also among what a copy of
            // a stack leaves out (MobStacker.TOP_ONLY_KEYS): up to 1.11.0 every mob made out of a
            // carrying stack got the block again, so each kill dropped one more.
            rule(EnderMan.class, "one of them carries a block",
                    (a, b) -> a.getCarriedBlock() == null && b.getCarriedBlock() == null)
    );

    private MobVariants() {}

    /**
     * @return true when nothing about these two mobs would be lost by merging them into one stack.
     *         Types with no rule of their own carry no variant and always match.
     */
    public static boolean sameVariant(Mob self, Mob other) {
        return variantDifference(self, other) == null;
    }

    /** What keeps these two mobs from merging, e.g. "wool colour, or one of them is sheared", or null when nothing does. */
    public static String variantDifference(Mob self, Mob other) {
        for (Rule rule : RULES) {
            if (rule.type().isInstance(self) && rule.type().isInstance(other) && !rule.matches().test(self, other)) {
                return rule.what();
            }
        }
        return null;
    }

    // A horse's speed, jump and health are its whole point, and up to 1.9.0 every horse that came out
    // of a stack got a fresh roll of all three - stacking a horse and taking it out again was a re-roll
    // button. They are not a matching rule: applied to a continuous random roll that would stop wild
    // horses stacking at all. A copy of the stack carries the top horse's numbers, and since 1.9.1 the
    // stack remembers every member's own (MEMBER_ROLLS_KEY), which the member that comes out - or takes
    // over the top when the top one dies - gets back.

    /** Exactly the three {@code AbstractHorse.randomizeAttributes} rolls, and nothing else. */
    //? if >=1.20.5 {
    /*private static final List<net.minecraft.core.Holder<Attribute>> ROLLED_HORSE_ATTRIBUTES =
            List.of(Attributes.MAX_HEALTH, Attributes.MOVEMENT_SPEED, Attributes.JUMP_STRENGTH);
    *///?} else {
    private static final List<Attribute> ROLLED_HORSE_ATTRIBUTES =
            List.of(Attributes.MAX_HEALTH, Attributes.MOVEMENT_SPEED, Attributes.JUMP_STRENGTH);
    //?}

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
        for (var attribute : ROLLED_HORSE_ATTRIBUTES) {
            AttributeInstance instance = mob.getAttribute(attribute);
            if (instance != null) {
                rolls.putDouble(attributeKey(attribute), instance.getBaseValue());
            }
        }
        return rolls;
    }

    /** Gives a horse a member's rolls back, at full health - a mob comes out of a stack whole. */
    private static void applyRolls(Mob mob, CompoundTag rolls) {
        for (var attribute : ROLLED_HORSE_ATTRIBUTES) {
            AttributeInstance instance = mob.getAttribute(attribute);
            String key = attributeKey(attribute);
            if (instance != null && rolls.contains(key)) {
                instance.setBaseValue(rolls.getDouble(key));
            }
        }
        mob.setHealth(mob.getMaxHealth());
    }

    //? if >=1.20.5 {
    /*private static String attributeKey(net.minecraft.core.Holder<Attribute> attribute) {
        return String.valueOf(BuiltInRegistries.ATTRIBUTE.getKey(attribute.value()));
    }
    *///?} else {
    private static String attributeKey(Attribute attribute) {
        return String.valueOf(BuiltInRegistries.ATTRIBUTE.getKey(attribute));
    }
    //?}

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
            return; // nothing recorded: the survivor keeps the top horse's numbers, copied with the rest
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
