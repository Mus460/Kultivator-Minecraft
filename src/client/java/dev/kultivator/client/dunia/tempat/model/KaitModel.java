package dev.kultivator.client.dunia.tempat.model;

import dev.kultivator.dunia.tempat.ekonomi.GerobakEntity;
import dev.kultivator.dunia.tempat.gua.BonekaPenjagaEntity;
import dev.kultivator.dunia.tempat.gua.LempengGlifEntity;
import dev.kultivator.dunia.tempat.gua.PintuTebingEntity;
import dev.kultivator.dunia.tempat.gua.SangkarEntity;
import dev.kultivator.dunia.tempat.jalan.RakitEntity;
import dev.kultivator.dunia.tempat.kotabesar.GerbangKotaEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.util.Mth;

/**
 * The animation hooks of the DESIGN 38 entity models (38.O.2; client contract of module 38MD). The renderers draw and
 * animate; the state that drives a mechanism (a gate's opening, a cage's lock, a glyph plate's turn) lives in the entity
 * and is synced by its owner module. The defaults read the owner's synced entity data directly (the gate's
 * {@link GerbangKotaEntity#bukaLerp}, size, break-in and cracks, the cart's {@link GerobakEntity#muatan()}, the raft's
 * {@link RakitEntity#sedangMendayung()}), so the models move with the real mechanism without any extra wiring; the 38GU
 * client ({@code GuaClient}) sets the cave readers. An owner may still replace a reader from its own client init, for
 * example to drive a cut-scene. Hooks are read on the render thread only.
 */
public final class KaitModel {

	/** A float reading of an entity at a partial tick. */
	@FunctionalInterface
	public interface Baca<E extends Entity> {
		float baca(E entity, float partialTick);
	}

	/** An int reading of an entity. */
	@FunctionalInterface
	public interface BacaInt<E extends Entity> {
		int baca(E entity);
	}

	// ------------------------------------------------------------------ gerbang_kota (38KS)

	/**
	 * How far the two gate leaves are open, 0 (shut) to 1 (open, 92° on their outer hinges), already eased
	 * ({@code easeInOutSine} over {@link GerbangKotaEntity#AYUN_TICK} ticks). Default: the entity's synced swing, which
	 * follows the curfew of DESIGN 38.B.4.1 and a knock at night.
	 */
	public static Baca<GerbangKotaEntity> gerbangBuka = (gate, pt) -> gate.bukaLerp(pt);
	/** The gate opening's width in blocks (both leaves together); default: the anchor data (2-12). */
	public static Baca<GerbangKotaEntity> gerbangLebar = (gate, pt) -> gate.lebar();
	/** The gate opening's height in blocks; default: the anchor data (3-14). */
	public static Baca<GerbangKotaEntity> gerbangTinggi = (gate, pt) -> gate.tinggi();
	/** 0 = whole, 1 = one leaf broken in (Gelombang Binatang): leans 12° and trembles. Default: the entity's reading. */
	public static Baca<GerbangKotaEntity> gerbangRoboh = (gate, pt) -> gate.robohLerp(pt);
	/** Damage share 0 whole .. 1 broken: cracks on the planks (light from 0.3, heavy from 0.65). Default: the entity's. */
	public static Baca<GerbangKotaEntity> gerbangRetak = (gate, pt) -> gate.retak();
	/** True for a Yamen cell door (a one-block barred iron door instead of lacquered leaves). Default: the entity's name. */
	public static BacaInt<GerbangKotaEntity> gerbangSel = gate -> gate.sel() ? 1 : 0;

	// ------------------------------------------------------------------ gerobak (38EK)

	/** The cart's load: 0 chests, 1 sacks, 2 jars. Default: the entity's synced load (38EK {@code setMuatan}). */
	public static BacaInt<GerobakEntity> gerobakMuatan = GerobakEntity::muatan;

	// ------------------------------------------------------------------ rakit (38W)

	/**
	 * Progress of the ferry's poling stroke 0..1 (repeating), negative when nobody poles. Default: one stroke every 20
	 * ticks while the entity's synced rowing flag is on ({@link RakitEntity#DAYUNG_TICKS} = four strokes a crossing).
	 */
	public static Baca<RakitEntity> rakitDayung = (raft, pt) -> raft.sedangMendayung() ? ((raft.tickCount + pt) % 20.0F) / 20.0F : -1.0F;

	// ------------------------------------------------------------------ sangkar, lempeng_glif, pintu_tebing, boneka (38GU)

	/** The cage door: 0 locked; 0-0.3 the padlock drops; 0.3-1 the door swings to 100°. Default 0. */
	public static Baca<SangkarEntity> sangkarBuka = (cage, pt) -> 0.0F;
	/** The glyph plate's turn in quarter turns (fractional while it turns, easeOutBack over 10 ticks). Default: its yaw. */
	public static Baca<LempengGlifEntity> glifPutar = (plate, pt) -> Mth.wrapDegrees(plate.getYRot(pt)) / 90.0F;
	/** How bright the plate's glyph burns, 0..1 (a solved plate fades in over 20 ticks). Default 0. */
	public static Baca<LempengGlifEntity> glifNyala = (plate, pt) -> 0.0F;
	/** Which of the six glyphs the plate shows (0 fire, 1 water, 2 wood, 3 metal, 4 earth, 5 thunder). Default: from the id. */
	public static BacaInt<LempengGlifEntity> glifJenis = plate -> Math.floorMod(plate.getId(), 6);
	/** How far the false-stone cliff door has slid aside, 0..1. Default 0. */
	public static Baca<PintuTebingEntity> pintuGeser = (door, pt) -> 0.0F;
	/** The tomb puppet rising from its cross-legged seat: 0 seated, 1 standing (default). */
	public static Baca<BonekaPenjagaEntity> bonekaBangkit = (puppet, pt) -> 1.0F;

	/** The curfew reading: 1 by day, closing 13000-13024, 0 at night, opening 1000-1024. */
	public static float bukaMenurutJam(Level level, float pt) {
		long day = Math.floorMod(level.getOverworldClockTime(), 24000L);
		float t = day + pt;
		if (t >= 13000.0F && t < 13024.0F) {
			return 1.0F - (t - 13000.0F) / 24.0F;
		}
		if (t >= 1000.0F && t < 1024.0F) {
			return (t - 1000.0F) / 24.0F;
		}
		return t >= 1024.0F && t < 13000.0F ? 1.0F : 0.0F;
	}

	private KaitModel() {
	}
}
