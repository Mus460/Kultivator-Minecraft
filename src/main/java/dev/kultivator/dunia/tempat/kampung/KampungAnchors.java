package dev.kultivator.dunia.tempat.kampung;

import dev.kultivator.Kultivator;
import dev.kultivator.dunia.AnchorHandler;
import dev.kultivator.dunia.DuniaAnchorEntity;
import dev.kultivator.dunia.DuniaSites;
import dev.kultivator.dunia.tempat.TempatZones;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import java.util.List;

/**
 * The anchors of the mortal villages and clan villages (module {@code kampung}, DESIGN 38.C.2; templates of
 * {@code tools/gen_tempat_kampung.py}): {@code kampung/situs} (the village square: publishes the site
 * {@code kampung/<ukuran>} and registers the village box with {@link TempatZones} as safe, protected ground, so 38NL gives
 * it residents and Mode Kehancuran leaves it alone), {@code kampung/lonceng} (the bell: Harvest Festival, talented
 * children), {@code kampung/pasar} (the market of a {@code desa} or {@code desa_besar}), {@code kampung/tetua},
 * {@code kampung/tabib}, {@code kampung/pandai}, {@code kampung/balai} (stand points of 38NL residents; 38EK serves the
 * healer), and in a clan village {@code kampung/patriark}, {@code kampung/tuan_muda} and {@code kampung/tetua_klan}.
 * <p>
 * The size and style come from the structure id ({@code kultivator:kampung/desa_besar_salju},
 * {@code kultivator:kampung/klan_gunung}); a {@code situs} data field ({@code kampung/klan}) is the fallback.
 */
final class KampungAnchors implements AnchorHandler {

	/** Sizes in matching order (the longest first). */
	private static final List<String> UKURAN = List.of("desa_besar", "dusun", "desa");

	/** A village's size and style. */
	record Identitas(String ukuran, String gaya) {

		String jenis() {
			return "kampung/" + ukuran;
		}
	}

	/** The size and style of the village that holds {@code anchor}. */
	static Identitas identitas(DuniaAnchorEntity anchor) {
		String id = anchor.structure();
		int colon = id.indexOf(':');
		String path = colon < 0 ? id : id.substring(colon + 1);
		if (path.startsWith("kampung/")) {
			String rest = path.substring("kampung/".length());
			if (rest.startsWith("klan_")) {
				return new Identitas("klan", rest.substring("klan_".length()));
			}
			for (String ukuran : UKURAN) {
				if (rest.startsWith(ukuran + "_")) {
					return new Identitas(ukuran, rest.substring(ukuran.length() + 1));
				}
			}
		}
		String situs = anchor.data().getStringOr("situs", "");
		if (situs.startsWith("kampung/")) {
			return new Identitas(situs.substring("kampung/".length()), "sedang");
		}
		return new Identitas("desa", "sedang");
	}

	/** The site id of the village of {@code anchor}. */
	static long situs(DuniaAnchorEntity anchor) {
		return anchor.siteOf(identitas(anchor).jenis());
	}

	@Override
	public void loaded(ServerLevel level, DuniaAnchorEntity anchor) {
		if (level.dimension() != Level.OVERWORLD) {
			return;
		}
		Identitas id = identitas(anchor);
		long site = anchor.siteOf(id.jenis());
		KampungData data = KampungData.get(level.getServer());
		switch (anchor.what()) {
			case "situs" -> {
				BlockPos at = anchor.origin() != null ? anchor.origin() : anchor.blockPosition();
				DuniaSites.publish(level, id.jenis(), at, id.ukuran().equals("klan") ? 2 : 1);
				data.kenal(site, id.ukuran(), id.gaya(), anchor.blockPosition());
				TempatZones.daftar(site, level.dimension(), kotak(level, anchor), true, id.jenis(), KampungApi.kekuatan(site));
			}
			case "lonceng" -> data.lonceng(site, anchor.blockPosition());
			default -> {
			}
		}
	}

	/** The village box: its structure start's box grown by 4 blocks, else the anchor's protection radius. */
	private static BoundingBox kotak(ServerLevel level, DuniaAnchorEntity anchor) {
		BlockPos c = anchor.blockPosition();
		try {
			StructureStart start = level.structureManager().getStructureWithPieceAt(c,
					holder -> holder.unwrapKey().map(key -> Kultivator.MOD_ID.equals(key.identifier().getNamespace())
							&& key.identifier().getPath().startsWith("kampung/")).orElse(false));
			if (start.isValid()) {
				BoundingBox b = start.getBoundingBox();
				return new BoundingBox(b.minX() - 4, b.minY() - 8, b.minZ() - 4, b.maxX() + 4, b.maxY() + 16, b.maxZ() + 4);
			}
		} catch (RuntimeException ignored) {
			// no structure data around the anchor: fall back to its radius
		}
		int r = Math.max(24, anchor.protectRadius());
		return new BoundingBox(c.getX() - r, c.getY() - 16, c.getZ() - r, c.getX() + r, c.getY() + 32, c.getZ() + r);
	}

	@Override
	public void nearby(ServerLevel level, DuniaAnchorEntity anchor, List<ServerPlayer> within) {
		if (!KampungConfig.hidup() || level.dimension() != Level.OVERWORLD) {
			return;
		}
		long site = situs(anchor);
		switch (anchor.what()) {
			case "lonceng" -> {
				FestivalPanen.dekat(level, anchor, site, within);
				AnakBerbakat.dekat(level, anchor, site, within);
			}
			case "pasar" -> PasarDesa.dekat(level, anchor, site, within);
			case "tuan_muda" -> Klan.tuanMuda(level, anchor, site, within);
			case "patriark" -> Klan.patriark(level, anchor, site, within);
			default -> {
			}
		}
	}

	@Override
	public void unloaded(ServerLevel level, DuniaAnchorEntity anchor) {
		if (anchor.what().equals("lonceng")) {
			FestivalPanen.lepas(situs(anchor));
		}
	}

	@Override
	public int radius() {
		return 48;
	}
}
