package com.frikinjay.mobstacker;

import net.minecraft.nbt.CompoundTag;
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
                    (a, b) -> a.getVariant() == b.getVariant(),
                    (from, to) -> to.setVariant(from.getVariant())),
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
