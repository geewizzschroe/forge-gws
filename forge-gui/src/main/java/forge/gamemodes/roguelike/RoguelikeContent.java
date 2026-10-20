package forge.gamemodes.roguelike;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import forge.StaticData;
import forge.card.CardRules;
import forge.localinstance.properties.ForgeConstants;

/**
 * Everything the run mode reads, from one content source:
 *
 * <pre>
 * config.json        RunConfig
 * upgrades.json      list of RunUpgrade (the pool upgrades are drawn from)
 * starters/*.json    StarterDef
 * encounters/*.json  EncounterDef
 * relics/*.json      RelicDef
 * pools/*.json       CardPoolDef
 * bases/*.txt        Base card scripts
 * overrides/*.txt    replacement card scripts (landless ramp) used in every match of the mode
 * </pre>
 *
 * The content ships inside the app as classpath resources under {@code roguelike/}, listed in
 * {@code roguelike/index.txt} (bundled files can't be listed otherwise). This keeps it on Android, where
 * the {@code res} folder is replaced by downloaded assets. If the folder
 * {@code <user data>/roguelike/content/} exists, it is used instead, so content can be edited on a device.
 */
public class RoguelikeContent {
    public static final String BUNDLE_ROOT = "roguelike/";

    private static final Gson GSON = new Gson();

    /** Where content files come from: a folder on disk, or the bundle inside the app. */
    interface Source {
        /** Relative paths of the files in a subfolder with the given extension, sorted. */
        List<String> list(String sub, String ext);
        /** The file's text, or null if it doesn't exist. */
        String read(String path);
    }

    private RunConfig config = new RunConfig();
    private final List<RunUpgrade> upgrades = new ArrayList<>();
    private final Map<String, StarterDef> starters = new LinkedHashMap<>();
    private final Map<String, EncounterDef> encounters = new LinkedHashMap<>();
    private final Map<String, RelicDef> relics = new LinkedHashMap<>();
    private final Map<String, CardPoolDef> pools = new LinkedHashMap<>();
    private final Map<String, List<String>> bases = new LinkedHashMap<>();
    private final List<CardRules> overrides = new ArrayList<>();

    /** The user's content folder if there is one, else the bundled content. */
    public static RoguelikeContent load() {
        File userDir = new File(ForgeConstants.USER_DIR + "roguelike" + File.separator + "content");
        return userDir.isDirectory() ? load(userDir) : loadBundled();
    }

    public static RoguelikeContent loadBundled() {
        return load(new BundleSource());
    }

    public static RoguelikeContent load(final File dir) {
        if (!dir.isDirectory()) {
            throw new IllegalArgumentException("Run-mode content folder not found: " + dir.getAbsolutePath());
        }
        return load(new FolderSource(dir));
    }

    static RoguelikeContent load(final Source src) {
        RoguelikeContent c = new RoguelikeContent();
        String config = src.read("config.json");
        if (config != null) {
            c.config = parse(config, RunConfig.class, "config.json");
        }
        String upgrades = src.read("upgrades.json");
        if (upgrades != null) {
            c.upgrades.addAll(parse(upgrades, new TypeToken<List<RunUpgrade>>() { }.getType(), "upgrades.json"));
        }
        for (String f : src.list("starters", ".json")) {
            StarterDef d = parse(src.read(f), StarterDef.class, f);
            c.starters.put(d.id, d);
        }
        for (String f : src.list("encounters", ".json")) {
            EncounterDef d = parse(src.read(f), EncounterDef.class, f);
            c.encounters.put(d.id, d);
        }
        for (String f : src.list("relics", ".json")) {
            RelicDef d = parse(src.read(f), RelicDef.class, f);
            c.relics.put(d.id, d);
        }
        for (String f : src.list("pools", ".json")) {
            CardPoolDef d = parse(src.read(f), CardPoolDef.class, f);
            c.pools.put(d.id, d);
        }
        for (String f : src.list("bases", ".txt")) {
            c.bases.put(f.substring(f.lastIndexOf('/') + 1), lines(src.read(f)));
        }
        for (String f : src.list("overrides", ".txt")) {
            c.overrides.add(CardRules.fromScript(lines(src.read(f))));
        }
        return c;
    }

    private static <T> T parse(final String json, final java.lang.reflect.Type type, final String what) {
        try {
            T value = GSON.fromJson(json, type);
            if (value == null) {
                throw new IllegalStateException("Empty file: " + what);
            }
            return value;
        } catch (RuntimeException e) {
            throw new IllegalStateException("Could not read " + what + ": " + e.getMessage(), e);
        }
    }

    private static List<String> lines(final String text) {
        List<String> out = new ArrayList<>();
        for (String line : text.split("\\r?\\n")) {
            if (!line.isBlank()) {
                out.add(line);
            }
        }
        return out;
    }

    static final class FolderSource implements Source {
        private final File dir;

        FolderSource(final File dir) {
            this.dir = dir;
        }

        @Override
        public List<String> list(final String sub, final String ext) {
            String[] found = new File(dir, sub).list((d, name) -> name.endsWith(ext));
            if (found == null) {
                return Collections.emptyList();
            }
            Arrays.sort(found);
            List<String> out = new ArrayList<>();
            for (String name : found) {
                out.add(sub + "/" + name);
            }
            return out;
        }

        @Override
        public String read(final String path) {
            File f = new File(dir, path);
            if (!f.isFile()) {
                return null;
            }
            try {
                return Files.readString(f.toPath(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalStateException("Could not read " + f, e);
            }
        }
    }

    static final class BundleSource implements Source {
        private final List<String> index = new ArrayList<>();

        BundleSource() {
            String text = read("index.txt");
            if (text == null) {
                throw new IllegalStateException("The bundled run-mode content is missing (" + BUNDLE_ROOT + "index.txt)");
            }
            index.addAll(lines(text));
        }

        @Override
        public List<String> list(final String sub, final String ext) {
            List<String> out = new ArrayList<>();
            for (String path : index) {
                if (path.startsWith(sub + "/") && path.endsWith(ext)) {
                    out.add(path);
                }
            }
            Collections.sort(out);
            return out;
        }

        @Override
        public String read(final String path) {
            try (InputStream in = RoguelikeContent.class.getClassLoader().getResourceAsStream(BUNDLE_ROOT + path)) {
                return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalStateException("Could not read bundled " + path, e);
            }
        }
    }

    public RunConfig getConfig() { return config; }    public List<RunUpgrade> getUpgrades() { return upgrades; }
    public Map<String, StarterDef> getStarters() { return starters; }
    public Map<String, EncounterDef> getEncounters() { return encounters; }
    public Map<String, RelicDef> getRelics() { return relics; }
    public Map<String, CardPoolDef> getPools() { return pools; }
    public List<CardRules> getOverrides() { return overrides; }

    /** The Base script with that file name; throws if it is missing. */
    public List<String> getBase(final String fileName) {
        List<String> lines = bases.get(fileName);
        if (lines == null) {
            throw new IllegalArgumentException("No Base named " + fileName);
        }
        return lines;
    }

    public StarterDef getStarter(final String id) { return require(starters, id, "starter"); }
    public EncounterDef getEncounter(final String id) { return require(encounters, id, "encounter"); }
    public RelicDef getRelic(final String id) { return require(relics, id, "relic"); }
    public CardPoolDef getPool(final String id) { return require(pools, id, "card pool"); }

    private static <T> T require(final Map<String, T> map, final String id, final String what) {
        T v = map.get(id);
        if (v == null) {
            throw new IllegalArgumentException("No " + what + " with id " + id);
        }
        return v;
    }

    /** Card names used by the content that the card database does not know (empty when all is well). */
    public Collection<String> findUnknownCards() {
        TreeSet<String> unknown = new TreeSet<>();
        List<String> names = new ArrayList<>();
        for (StarterDef s : starters.values()) {
            names.add(s.commander);
            names.addAll(s.deck.keySet());
        }
        for (EncounterDef e : encounters.values()) {
            if (e.commander != null) {
                names.add(e.commander);
            }
            names.addAll(e.deck.keySet());
        }
        for (CardPoolDef p : pools.values()) {
            names.addAll(p.cards);
        }
        for (CardRules o : overrides) {
            names.add(o.getName());
        }
        for (String n : names) {
            if (n != null && StaticData.instance().getCommonCards().getCard(n) == null) {
                unknown.add(n);
            }
        }
        return unknown;
    }
}
