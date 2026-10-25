package dev.kultivator.dunia.kota42.tumbuh;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.kultivator.Kultivator;
import dev.kultivator.dunia.tempat.Titik;
import dev.kultivator.dunia.tempat.TitikTemplat;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The building plans of the growth plots (DESIGN 42.9.4): the sidecar {@code data/kultivator/structure/kota_tumbuh/rencana.json}
 * of {@code tools/gen_kota_tumbuh.py} (121 plans, 11 kinds x 11 styles, contract in {@code docs/progres/m2-42W-G.md}) and
 * the five cumulative stage templates {@code kultivator:kota_tumbuh/<gaya>/<jenis>/tahap_<1-5>} (Yingzao Fashi order:
 * pondasi, rangka, dinding, atap, isi). The templates are only read here (gzip NBT, palette + blocks, block entities
 * dropped) and never placed through {@code /place} or a structure: {@code PekerjaanTumbuh} turns their cells into one
 * crew job per stage. Plans face south as authored; {@link #rotasi} turns them to the plot's street side, {@link #asal}
 * puts the turned footprint on the plot's box. Cached until the next data pack reload.
 */
public final class RencanaTumbuh {

	/** Stage names (lang {@code kultivator.kota42.tumbuh.tahap.<nama>}). */
	public static final List<String> NAMA_TAHAP = List.of("pondasi", "rangka", "dinding", "atap", "isi");
	public static final Identifier FILE = Kultivator.id("structure/kota_tumbuh/rencana.json");
	/** Bytes read per template at most. */
	private static final long KUOTA = 16L * 1024L * 1024L;

	/** One stage: number 1-5, name, template, blocks it adds, material units by {@link BahanKota#JENIS} index. */
	public record Tahap(int no, String nama, Identifier template, int blok, int[] bahan) {

		public int satuan() {
			int n = 0;
			for (int b : bahan) {
				n += b;
			}
			return n;
		}
	}

	/**
	 * One plan ({@code id} = {@code gaya/jenis}): class {@code kecil} 7 x 7 or {@code besar} 13 x 13, what it answers
	 * ({@code rumah}, {@code usaha}, {@code sekolah}, {@code jaga}, {@code ibadah}, {@code suasana}), households, beds and
	 * jobs it brings, its five stages.
	 */
	public record Rencana(String id, String gaya, String jenis, String kelas, int lebar, int tinggi, int panjang, List<String> kebutuhan,
			int rumahTangga, int kasur, int lapanganKerja, List<Tahap> tahap) {

		public @Nullable Tahap tahap(int no) {
			return no >= 1 && no <= tahap.size() ? tahap.get(no - 1) : null;
		}

		/** Material units of every stage together, by {@link BahanKota#JENIS} index. */
		public int[] bahanTotal() {
			int[] out = new int[BahanKota.JENIS.size()];
			for (Tahap t : tahap) {
				for (int i = 0; i < out.length; i++) {
					out[i] += t.bahan()[i];
				}
			}
			return out;
		}

		public int satuanTotal() {
			int n = 0;
			for (Tahap t : tahap) {
				n += t.satuan();
			}
			return n;
		}

		public boolean butuh(String kebutuhan) {
			return this.kebutuhan.contains(kebutuhan);
		}
	}

	/** One template cell (local, south-facing). */
	public record Sel(int x, int y, int z, BlockState state) {
	}

	private record Data(Map<String, Rencana> rencana, Map<String, String> paletBahan, int[] satuanBlok, java.util.Set<String> genteng) {
	}

	private static volatile @Nullable Data data;
	private static final Map<Identifier, List<Sel>> SEL = new ConcurrentHashMap<>();
	private static boolean hooked;

	static void init() {
		if (hooked) {
			return;
		}
		hooked = true;
		ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) -> lupakan());
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> lupakan());
	}

	static void lupakan() {
		data = null;
		SEL.clear();
	}

	// ================================================================== plans

	/** Every plan by id (insertion order of the file). */
	public static Map<String, Rencana> semua(MinecraftServer server) {
		return muat(server).rencana();
	}

	/** Plan {@code id} ({@code gaya/jenis}), or null. */
	public static @Nullable Rencana get(MinecraftServer server, String id) {
		return id == null ? null : muat(server).rencana().get(id);
	}

	/** The plans of style {@code gaya} that fit a plot of class {@code kelas}. */
	public static List<Rencana> untuk(MinecraftServer server, String gaya, String kelas) {
		List<Rencana> out = new ArrayList<>();
		for (Rencana r : semua(server).values()) {
			if (r.gaya().equals(gaya) && r.kelas().equals(kelas)) {
				out.add(r);
			}
		}
		return out;
	}

	/**
	 * The material of a block id ({@code minecraft:oak_planks} -> index of {@code kayu}), or -1 (not a building block).
	 * Roof tiles of any style ({@code atap_genteng}, not wood or straw) count as {@code genteng}.
	 */
	public static int bahanBlok(MinecraftServer server, String blockId) {
		Data d = muat(server);
		if (d.genteng().contains(blockId)) {
			return BahanKota.JENIS.indexOf("genteng");
		}
		String b = d.paletBahan().get(blockId);
		return b == null ? -1 : BahanKota.JENIS.indexOf(b);
	}

	/** Blocks of material {@code i} that make one unit (42.9.4: kayu 4, batu 4, genteng 2, kaca_kertas 2, kain 4). */
	public static int satuanBlok(MinecraftServer server, int i) {
		int[] s = muat(server).satuanBlok();
		return i >= 0 && i < s.length ? Math.max(1, s[i]) : 4;
	}

	private static Data muat(MinecraftServer server) {
		Data d = data;
		if (d != null) {
			return d;
		}
		d = baca(server);
		data = d;
		return d;
	}

	private static Data baca(MinecraftServer server) {
		Map<String, Rencana> plans = new LinkedHashMap<>();
		Map<String, String> palette = new HashMap<>();
		java.util.Set<String> tiles = new java.util.HashSet<>();
		int[] unit = {4, 4, 2, 2, 4};
		Optional<Resource> res = server.getResourceManager().getResource(FILE);
		if (res.isEmpty()) {
			Kultivator.LOGGER.warn("Kultivator: {} is missing; towns cannot grow", FILE);
			return new Data(Map.of(), Map.of(), unit, java.util.Set.of());
		}
		try (InputStreamReader reader = new InputStreamReader(res.get().open(), StandardCharsets.UTF_8)) {
			JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
			JsonObject sb = root.has("satuan_blok") ? root.getAsJsonObject("satuan_blok") : new JsonObject();
			for (int i = 0; i < unit.length; i++) {
				String k = BahanKota.JENIS.get(i);
				if (sb.has(k)) {
					unit[i] = Math.max(1, sb.get(k).getAsInt());
				}
			}
			if (root.has("palet_bahan")) {
				for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("palet_bahan").entrySet()) {
					palette.put(e.getKey(), e.getValue().getAsString());
				}
			}
			if (root.has("atap_genteng")) {
				for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("atap_genteng").entrySet()) {
					for (JsonElement id : e.getValue().getAsJsonArray()) {
						String block = id.getAsString();
						if (!"kayu".equals(palette.get(block)) && !block.contains("hay")) {
							tiles.add(block);
						}
					}
				}
			}
			for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("rencana").entrySet()) {
				JsonObject r = e.getValue().getAsJsonObject();
				List<Tahap> stages = new ArrayList<>();
				for (JsonElement te : r.getAsJsonArray("tahap")) {
					JsonObject t = te.getAsJsonObject();
					Identifier template = Identifier.tryParse(t.get("template").getAsString());
					if (template == null) {
						continue;
					}
					int[] bahan = new int[BahanKota.JENIS.size()];
					JsonObject b = t.has("bahan") ? t.getAsJsonObject("bahan") : new JsonObject();
					for (int i = 0; i < bahan.length; i++) {
						String k = BahanKota.JENIS.get(i);
						bahan[i] = b.has(k) ? Math.max(0, b.get(k).getAsInt()) : 0;
					}
					stages.add(new Tahap(t.get("tahap").getAsInt(), t.get("nama").getAsString(), template, t.get("blok_baru").getAsInt(), bahan));
				}
				if (stages.size() != NAMA_TAHAP.size()) {
					continue;
				}
				List<String> needs = new ArrayList<>();
				for (JsonElement n : r.getAsJsonArray("kebutuhan")) {
					needs.add(n.getAsString());
				}
				var size = r.getAsJsonArray("ukuran");
				plans.put(e.getKey(), new Rencana(e.getKey(), r.get("gaya").getAsString(), r.get("jenis").getAsString(), r.get("kelas").getAsString(),
						size.get(0).getAsInt(), size.get(1).getAsInt(), size.get(2).getAsInt(), List.copyOf(needs), angka(r, "rumah_tangga"),
						angka(r, "kasur"), angka(r, "lapangan_kerja"), List.copyOf(stages)));
			}
		} catch (RuntimeException | java.io.IOException exception) {
			Kultivator.LOGGER.error("Kultivator: {} could not be read; towns cannot grow", FILE, exception);
		}
		return new Data(Map.copyOf(plans), Map.copyOf(palette), unit, java.util.Set.copyOf(tiles));
	}

	private static int angka(JsonObject o, String k) {
		return o.has(k) ? o.get(k).getAsInt() : 0;
	}

	// ================================================================== stage templates

	/** The cells of a stage template (local coordinates, structure voids left out), cached; empty when unreadable. */
	public static List<Sel> sel(MinecraftServer server, Identifier template) {
		List<Sel> cached = SEL.get(template);
		if (cached != null) {
			return cached;
		}
		List<Sel> read = bacaSel(server, template);
		SEL.put(template, read);
		return read;
	}

	private static List<Sel> bacaSel(MinecraftServer server, Identifier template) {
		Identifier file = template.withPath(p -> "structure/" + p + ".nbt");
		Optional<Resource> res = server.getResourceManager().getResource(file);
		if (res.isEmpty()) {
			Kultivator.LOGGER.warn("Kultivator: growth stage {} is missing", template);
			return List.of();
		}
		try (InputStream in = res.get().open()) {
			CompoundTag root = NbtIo.readCompressed(in, NbtAccounter.create(KUOTA));
			HolderGetter<Block> blocks = server.registryAccess().lookupOrThrow(Registries.BLOCK);
			ListTag pal = root.getListOrEmpty("palette");
			BlockState[] states = new BlockState[pal.size()];
			for (int i = 0; i < states.length; i++) {
				states[i] = NbtUtils.readBlockState(blocks, pal.getCompoundOrEmpty(i));
			}
			ListTag list = root.getListOrEmpty("blocks");
			List<Sel> out = new ArrayList<>(list.size());
			for (int i = 0; i < list.size(); i++) {
				CompoundTag b = list.getCompoundOrEmpty(i);
				int s = b.getIntOr("state", -1);
				ListTag pos = b.getListOrEmpty("pos");
				if (s < 0 || s >= states.length || pos.size() != 3) {
					continue;
				}
				out.add(new Sel(pos.getIntOr(0, 0), pos.getIntOr(1, 0), pos.getIntOr(2, 0), states[s]));
			}
			return List.copyOf(out);
		} catch (java.io.IOException | RuntimeException exception) {
			Kultivator.LOGGER.warn("Kultivator: growth stage {} could not be read", template, exception);
			return List.of();
		}
	}

	// ================================================================== placement

	/** The turn that brings a south-facing plan to face {@code hadap} (kota_hidup_kit.rotasi_rencana). */
	public static Rotation rotasi(Direction hadap) {
		return switch (hadap) {
			case WEST -> Rotation.CLOCKWISE_90;
			case NORTH -> Rotation.CLOCKWISE_180;
			case EAST -> Rotation.COUNTERCLOCKWISE_90;
			default -> Rotation.NONE;
		};
	}

	/** Local (x, z) turned like {@code TitikTemplat} and vanilla templates (clockwise: (x, z) -> (-z, x)). */
	static int[] putar(Rotation r, int x, int z) {
		return switch (r) {
			case CLOCKWISE_90 -> new int[]{-z, x};
			case CLOCKWISE_180 -> new int[]{-x, -z};
			case COUNTERCLOCKWISE_90 -> new int[]{z, -x};
			default -> new int[]{x, z};
		};
	}

	/** The template origin that puts a {@code lebar x panjang} footprint turned by {@code r} on the box corner {@code min}. */
	public static BlockPos asal(BlockPos min, int lebar, int panjang, Rotation r) {
		int[] b = putar(r, lebar - 1, panjang - 1);
		return min.offset(-Math.min(0, b[0]), 0, -Math.min(0, b[1]));
	}

	/** World position of local cell {@code (x, y, z)}. */
	public static BlockPos dunia(BlockPos asal, Rotation r, int x, int y, int z) {
		int[] p = putar(r, x, z);
		return asal.offset(p[0], y, p[1]);
	}

	/** The stage-5 life points of plan {@code rencana} placed at {@code asal} turned by {@code r} (world coordinates). */
	public static List<Titik> titik(MinecraftServer server, Rencana rencana, BlockPos asal, Rotation r) {
		Tahap last = rencana.tahap(NAMA_TAHAP.size());
		if (last == null) {
			return List.of();
		}
		List<Titik> out = new ArrayList<>();
		for (TitikTemplat.Lokal p : TitikTemplat.of(server, last.template())) {
			out.add(TitikTemplat.transform(p, asal, r));
		}
		return out;
	}

	/**
	 * The turning preview of stage 5 for the plot screen (42.11.13 {@code siluet}): {@code [0]} = width, {@code [1]} =
	 * depth, then width x depth column heights (top block + 1, 0 = empty), then width x depth top colours (map colour
	 * RGB, 0 = empty). Local, south-facing; at most 32 x 32.
	 */
	public static int[] siluet(MinecraftServer server, Rencana rencana) {
		Tahap last = rencana.tahap(NAMA_TAHAP.size());
		int w = Math.min(32, rencana.lebar());
		int d = Math.min(32, rencana.panjang());
		int[] out = new int[2 + 2 * w * d];
		out[0] = w;
		out[1] = d;
		if (last == null) {
			return out;
		}
		for (Sel c : sel(server, last.template())) {
			if (c.x() < 0 || c.z() < 0 || c.x() >= w || c.z() >= d || c.state().isAir()) {
				continue;
			}
			int i = c.z() * w + c.x();
			if (c.y() + 1 >= out[2 + i]) {
				out[2 + i] = c.y() + 1;
				MapColor colour = c.state().getMapColor(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
				out[2 + w * d + i] = colour == null ? 0 : colour.col;
			}
		}
		return out;
	}

	private RencanaTumbuh() {
	}
}
