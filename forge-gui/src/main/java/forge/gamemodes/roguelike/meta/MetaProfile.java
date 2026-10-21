package forge.gamemodes.roguelike.meta;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/** Account-wide saved progress: every character's progress and the account's experience. */
public class MetaProfile {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public int version = 1;
    public long accountXp;
    public Map<String, CharacterProgress> characters = new LinkedHashMap<>();

    public CharacterProgress character(final String id) {
        return characters.computeIfAbsent(id, k -> new CharacterProgress());
    }

    public void save(final File f) {
        f.getParentFile().mkdirs();
        try (Writer w = Files.newBufferedWriter(f.toPath(), StandardCharsets.UTF_8)) {
            GSON.toJson(this, w);
        } catch (IOException e) {
            throw new IllegalStateException("Could not save progress to " + f, e);
        }
    }

    /** The saved profile, or a new one if there is none. */
    public static MetaProfile load(final File f) {
        if (!f.isFile()) {
            return new MetaProfile();
        }
        try (Reader r = Files.newBufferedReader(f.toPath(), StandardCharsets.UTF_8)) {
            MetaProfile p = GSON.fromJson(r, MetaProfile.class);
            return p == null ? new MetaProfile() : p;
        } catch (IOException e) {
            throw new IllegalStateException("Could not load progress from " + f, e);
        }
    }
}
