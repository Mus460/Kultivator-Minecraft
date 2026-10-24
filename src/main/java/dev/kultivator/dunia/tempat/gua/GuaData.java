package dev.kultivator.dunia.tempat.gua;

import com.mojang.serialization.Codec;
import dev.kultivator.Kultivator;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * SavedData {@code kultivator:tempat_gua} in the overworld (DESIGN 38.C.4, 38.C.5, 38.D.8): what the caves and natural
 * sites remember beyond their anchors' own state. New worlds only: no migration of older layouts.
 *
 * <h2>Site compound (per site id of the structure's {@code situs} anchor)</h2>
 * <pre>
 * m      C  uuid -> int  tomb progress bits {@link #HATI} {@link #GIOK} {@link #KUAT} {@link #PAHAM} {@link #UJIAN} {@link #HADIAH}
 * lahir  C  {dim S, x y z I, jenis S}  a treasure-birth place (spirit-vein treasure point, star stage centre), 38.A #23
 * </pre>
 * <h2>Player compound</h2>
 * <pre>
 * cermin  L  world day of the last Heart Mirror trial
 * air     L  world day the player last drank from a Spirit Spring
 * bebas   I  captives freed (advancement tempat/pembebas at 5)
 * </pre>
 */
public final class GuaData extends SavedData {

	/** The tomb's Heart trial was passed (quest stage moved past {@code hati}). */
	public static final int HATI = 1;
	/** A Giok Warisan was held while the Heart trial was passed: +10 points on the inheritance roll at this tomb. */
	public static final int GIOK = 2;
	/** The Strength trial: every puppet was defeated with the player in the room. */
	public static final int KUAT = 4;
	/** The Comprehension trial: the five glyph plates were set in the generating cycle. */
	public static final int PAHAM = 8;
	/** The quest event {@code ujian makam:<dao>:<tier>} was fired. */
	public static final int UJIAN = 16;
	/** The tomb's inheritance was given (once per player per tomb). */
	public static final int HADIAH = 32;

	/** A known treasure-birth place. */
	public record Lahir(long situs, ResourceKey<Level> dim, BlockPos pos, String jenis) {
	}

	public static final Codec<GuaData> CODEC = CompoundTag.CODEC.xmap(GuaData::fromTag, GuaData::toTag);
	public static final SavedDataType<GuaData> TYPE = new SavedDataType<>(Kultivator.id("tempat_gua"), GuaData::new, CODEC,
			DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

	private final Map<Long, CompoundTag> sites = new HashMap<>();
	private final Map<UUID, CompoundTag> players = new HashMap<>();

	public GuaData() {
	}

	public static GuaData get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	// ================================================================== sites

	public CompoundTag site(long id) {
		return sites.computeIfAbsent(id, key -> new CompoundTag());
	}

	/** The tomb progress bits of {@code player} at tomb {@code situs}. */
	public int makam(long situs, UUID player) {
		CompoundTag site = sites.get(situs);
		return site == null ? 0 : site.getCompoundOrEmpty("m").getIntOr(player.toString(), 0);
	}

	/** Adds {@code bits}; returns the bits after. */
	public int addMakam(long situs, UUID player, int bits) {
		CompoundTag site = site(situs);
		CompoundTag m = site.getCompoundOrEmpty("m");
		int before = m.getIntOr(player.toString(), 0);
		int after = before | bits;
		if (after != before) {
			m.putInt(player.toString(), after);
			site.put("m", m);
			setDirty();
		}
		return after;
	}

	/** Forgets a player's progress at a tomb ({@code uji tempat_gua reset}). */
	public void resetMakam(long situs, @Nullable UUID player) {
		CompoundTag site = sites.get(situs);
		if (site == null) {
			return;
		}
		if (player == null) {
			site.remove("m");
		} else {
			CompoundTag m = site.getCompoundOrEmpty("m");
			m.remove(player.toString());
			site.put("m", m);
		}
		setDirty();
	}

	/** Remembers a treasure-birth place (idempotent). */
	public void setLahir(long situs, ResourceKey<Level> dim, BlockPos pos, String jenis) {
		CompoundTag site = site(situs);
		CompoundTag old = site.getCompoundOrEmpty("lahir");
		if (old.getIntOr("x", Integer.MIN_VALUE) == pos.getX() && old.getIntOr("y", 0) == pos.getY() && old.getIntOr("z", 0) == pos.getZ()
				&& old.getStringOr("dim", "").equals(dim.identifier().toString())) {
			return;
		}
		CompoundTag tag = new CompoundTag();
		tag.putString("dim", dim.identifier().toString());
		tag.putInt("x", pos.getX());
		tag.putInt("y", pos.getY());
		tag.putInt("z", pos.getZ());
		tag.putString("jenis", jenis);
		site.put("lahir", tag);
		setDirty();
	}

	/** Every known treasure-birth place. */
	public List<Lahir> lahir() {
		List<Lahir> out = new ArrayList<>();
		for (Map.Entry<Long, CompoundTag> entry : sites.entrySet()) {
			CompoundTag tag = entry.getValue().getCompoundOrEmpty("lahir");
			if (!tag.contains("x")) {
				continue;
			}
			Identifier dim = Identifier.tryParse(tag.getStringOr("dim", ""));
			if (dim == null) {
				continue;
			}
			out.add(new Lahir(entry.getKey(), ResourceKey.create(Registries.DIMENSION, dim),
					new BlockPos(tag.getIntOr("x", 0), tag.getIntOr("y", 0), tag.getIntOr("z", 0)), tag.getStringOr("jenis", "")));
		}
		return out;
	}

	// ================================================================== players

	public CompoundTag player(UUID id) {
		return players.computeIfAbsent(id, key -> new CompoundTag());
	}

	public long playerLong(UUID id, String key) {
		CompoundTag tag = players.get(id);
		return tag == null ? -1L : tag.getLongOr(key, -1L);
	}

	public void setPlayerLong(UUID id, String key, long value) {
		player(id).putLong(key, value);
		setDirty();
	}

	/** Adds one to the player's counter {@code key}; returns the new value. */
	public int count(UUID id, String key) {
		CompoundTag tag = player(id);
		int n = tag.getIntOr(key, 0) + 1;
		tag.putInt(key, n);
		setDirty();
		return n;
	}

	public void touch() {
		setDirty();
	}

	// ================================================================== codec

	private static GuaData fromTag(CompoundTag tag) {
		GuaData data = new GuaData();
		CompoundTag siteTag = tag.getCompoundOrEmpty("tempat");
		for (String key : siteTag.keySet()) {
			try {
				data.sites.put(Long.parseLong(key), siteTag.getCompoundOrEmpty(key).copy());
			} catch (NumberFormatException ignored) {
				// dropped
			}
		}
		CompoundTag playerTag = tag.getCompoundOrEmpty("pemain");
		for (String key : playerTag.keySet()) {
			try {
				data.players.put(UUID.fromString(key), playerTag.getCompoundOrEmpty(key).copy());
			} catch (IllegalArgumentException ignored) {
				// dropped
			}
		}
		return data;
	}

	private CompoundTag toTag() {
		CompoundTag tag = new CompoundTag();
		CompoundTag siteTag = new CompoundTag();
		for (Map.Entry<Long, CompoundTag> entry : sites.entrySet()) {
			siteTag.put(Long.toString(entry.getKey()), entry.getValue().copy());
		}
		tag.put("tempat", siteTag);
		CompoundTag playerTag = new CompoundTag();
		for (Map.Entry<UUID, CompoundTag> entry : players.entrySet()) {
			playerTag.put(entry.getKey().toString(), entry.getValue().copy());
		}
		tag.put("pemain", playerTag);
		return tag;
	}

	/** Reads a list of string tags (helper for anchor states). */
	static List<String> strings(CompoundTag tag, String key) {
		List<String> out = new ArrayList<>();
		for (Tag t : tag.getListOrEmpty(key)) {
			t.asString().ifPresent(out::add);
		}
		return out;
	}

	/** Appends {@code value} to the string list {@code key} of {@code tag}, keeping at most {@code max}. */
	static void addString(CompoundTag tag, String key, String value, int max) {
		ListTag list = tag.getListOrEmpty(key).copy();
		list.add(net.minecraft.nbt.StringTag.valueOf(value));
		while (list.size() > max) {
			list.remove(0);
		}
		tag.put(key, list);
	}
}
