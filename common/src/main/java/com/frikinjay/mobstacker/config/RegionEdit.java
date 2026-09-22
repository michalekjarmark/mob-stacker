package com.frikinjay.mobstacker.config;

import com.frikinjay.mobstacker.MobStacker;

import java.util.regex.Pattern;

/**
 * Creating, reshaping and deleting a region, in one place.
 * <p>
 * A region's area used to be fixed the moment {@code /mobstacker region add} ran: changing it meant
 * deleting the region and adding it again, which threw away every setting it carried and its
 * priority. Reshaping is now a first-class edit, and because the command tree, the config GUI in
 * singleplayer and the GUI over the network all have to agree on what is allowed, they all come
 * through here rather than each validating the same thing slightly differently.
 */
public final class RegionEdit {
    /** Vanilla's world border limit; a region outside it could never contain a mob. */
    private static final int MAX_HORIZONTAL = 30_000_000;
    /** Generous vertical range — far beyond any dimension's build limits, but not unbounded. */
    private static final int MAX_VERTICAL = 20_000;
    private static final int MAX_NAME_LENGTH = 32;
    private static final Pattern VALID_NAME = Pattern.compile("[A-Za-z0-9_.-]+");

    /**
     * What an edit did, ready to be shown to whoever asked for it.
     *
     * @param ok      whether the config was actually changed
     * @param message a single line saying what happened, or why it did not
     */
    public record Result(boolean ok, String message) {
        static Result failed(String message) {
            return new Result(false, message);
        }
    }

    /**
     * A region's whole shape as one string, so the config GUI can create or redraw a region over the
     * existing two-string edit packet instead of needing a channel of its own:
     * {@code ALLOW;minecraft:overworld;-10,60,-10;10,90,10}.
     */
    public record Definition(StackRegion.Type type, String dimension,
                             int x1, int y1, int z1, int x2, int y2, int z2) {
        public String encode() {
            return type.name() + ';' + dimension + ';'
                    + x1 + ',' + y1 + ',' + z1 + ';'
                    + x2 + ',' + y2 + ',' + z2;
        }

        /** @return the decoded definition, or null when the text is not one */
        public static Definition decode(String raw) {
            if (raw == null) {
                return null;
            }
            String[] parts = raw.split(";");
            if (parts.length != 4) {
                return null;
            }
            String[] first = parts[2].split(",");
            String[] second = parts[3].split(",");
            if (first.length != 3 || second.length != 3) {
                return null;
            }
            try {
                StackRegion.Type type = StackRegion.Type.valueOf(parts[0].trim().toUpperCase(java.util.Locale.ROOT));
                return new Definition(type, parts[1].trim(),
                        Integer.parseInt(first[0].trim()), Integer.parseInt(first[1].trim()), Integer.parseInt(first[2].trim()),
                        Integer.parseInt(second[0].trim()), Integer.parseInt(second[1].trim()), Integer.parseInt(second[2].trim()));
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }

    private RegionEdit() {
    }

    /**
     * Creates the named region, or moves and reshapes it when it already exists — keeping its
     * settings, its priority and its name either way.
     */
    public static Result apply(String name, StackRegion.Type type, String dimension,
                               int x1, int y1, int z1, int x2, int y2, int z2) {
        return apply(name, type, dimension, x1, y1, z1, x2, y2, z2, false);
    }

    /**
     * The same, told whether the caller believes it is making a <em>new</em> region.
     *
     * <p>Reshaping an existing region and creating one are the same edit to this method but not to
     * the person asking for it. "New region…" filled in with a name that is already taken used to
     * quietly move somebody else's region onto the box around the player — no warning, no undo, and
     * the settings it carried now belonged to an area on the other side of the world.
     * {@code /mobstacker region add} has always refused that and pointed at {@code region bounds};
     * the GUI now goes through the same door.
     *
     * @param mustBeNew refuse rather than reshape when the name is taken
     */
    public static Result apply(String name, StackRegion.Type type, String dimension,
                               int x1, int y1, int z1, int x2, int y2, int z2, boolean mustBeNew) {
        String problem = problem(name, type, dimension, x1, y1, z1, x2, y2, z2);
        if (problem != null) {
            return Result.failed(problem);
        }
        String trimmed = name.trim();

        StackRegion region = MobStacker.config.getRegion(trimmed);
        boolean created = region == null;
        if (mustBeNew && !created) {
            return Result.failed("A region called '" + trimmed
                    + "' already exists. Open it from the list to redraw it, or pick another name.");
        }
        if (created) {
            region = new StackRegion(trimmed, dimension, type, x1, y1, z1, x2, y2, z2);
            MobStacker.config.addRegion(region);
        } else {
            region.setBounds(x1, y1, z1, x2, y2, z2);
            region.setType(type);
            region.setDimension(dimension);
        }

        // Drawing an allow region while stacking is off is almost certainly meant to turn it on —
        // the same courtesy /mobstacker region add has done since 1.5.3. An explicit
        // everywhere/players mode is never overridden.
        boolean switchedMode = false;
        if (type == StackRegion.Type.ALLOW && MobStacker.config.getStackMode() == StackMode.OFF) {
            MobStacker.config.setStackMode(StackMode.REGIONS);
            switchedMode = true;
        }
        MobStacker.config.save();

        String what = created ? "Added " + type + " region '" : "Reshaped region '";
        String message = what + trimmed + "' in " + dimension + " " + region.describeBounds()
                + (created ? "" : " (its settings were kept)")
                + (switchedMode ? "  -  stackMode was OFF, switched to REGIONS so it takes effect." : "");
        return new Result(true, message);
    }

    /** Same as {@link #apply}, from a {@link Definition} the GUI sent over the wire. */
    public static Result apply(String name, Definition definition) {
        return apply(name, definition, false);
    }

    /** Same as {@link #apply(String, Definition)}, refusing a name that is already taken. */
    public static Result apply(String name, Definition definition, boolean mustBeNew) {
        if (definition == null) {
            return Result.failed("Malformed region definition.");
        }
        return apply(name, definition.type(), definition.dimension(),
                definition.x1(), definition.y1(), definition.z1(),
                definition.x2(), definition.y2(), definition.z2(), mustBeNew);
    }

    /**
     * Gives a region a different name, keeping everything else about it. Deleting and re-adding it
     * under the new name would throw away its settings, its priority and its colour — the same trap
     * {@code bounds} was added to get out of.
     */
    public static Result rename(String from, String to) {
        String oldName = from == null ? "" : from.trim();
        String newName = to == null ? "" : to.trim();
        StackRegion region = MobStacker.config.getRegion(oldName);
        if (region == null) {
            return Result.failed("Region '" + oldName + "' does not exist");
        }
        if (oldName.equals(newName)) {
            return new Result(true, "Region '" + oldName + "' is already called that");
        }
        String problem = nameProblem(newName);
        if (problem != null) {
            return Result.failed(problem);
        }
        if (MobStacker.config.getRegion(newName) != null) {
            return Result.failed("A region called '" + newName + "' already exists");
        }
        region.setName(newName);
        MobStacker.config.save();
        return new Result(true, "Renamed '" + oldName + "' to '" + newName + "'");
    }

    /**
     * Everything wrong with a region name on its own, split out so renaming and creating judge one
     * by exactly the same rules.
     *
     * @return the reason it would be refused, or null when it is fine
     */
    public static String nameProblem(String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            return "A region needs a name.";
        }
        if (trimmed.length() > MAX_NAME_LENGTH || !VALID_NAME.matcher(trimmed).matches()) {
            return "'" + trimmed + "' is not a usable region name (letters, digits, _ . - only).";
        }
        return null;
    }

    /** Removes a region and everything it carried. */
    public static Result delete(String name) {
        String trimmed = name == null ? "" : name.trim();
        if (MobStacker.config.getRegion(trimmed) == null) {
            return Result.failed("Region '" + trimmed + "' does not exist");
        }
        MobStacker.config.removeRegion(trimmed);
        MobStacker.config.save();
        return new Result(true, "Removed region '" + trimmed + "'");
    }

    /**
     * Everything that makes a region definition unusable, without touching the config — so the GUI
     * can say no before it sends anything, on a client that has no config of its own to consult.
     *
     * @return the reason it would be refused, or null when it is fine
     */
    public static String problem(String name, StackRegion.Type type, String dimension,
                                 int x1, int y1, int z1, int x2, int y2, int z2) {
        String problem = nameProblem(name);
        if (problem != null) {
            return problem;
        }
        if (dimension == null || dimension.isBlank()) {
            return "A region needs a dimension.";
        }
        if (type == null) {
            return "A region must be allow or deny.";
        }
        return checkRange(x1, y1, z1, x2, y2, z2);
    }

    private static String checkRange(int x1, int y1, int z1, int x2, int y2, int z2) {
        if (Math.abs(x1) > MAX_HORIZONTAL || Math.abs(x2) > MAX_HORIZONTAL
                || Math.abs(z1) > MAX_HORIZONTAL || Math.abs(z2) > MAX_HORIZONTAL) {
            return "X and Z must be within +/-" + MAX_HORIZONTAL + " (the world border).";
        }
        if (Math.abs(y1) > MAX_VERTICAL || Math.abs(y2) > MAX_VERTICAL) {
            return "Y must be within +/-" + MAX_VERTICAL + ".";
        }
        return null;
    }
}
