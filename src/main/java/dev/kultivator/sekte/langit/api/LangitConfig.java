package dev.kultivator.sekte.langit.api;

import dev.kultivator.sekte.SekteConfig;

/**
 * The {@code sekteLangit} section of {@code config/kultivator-sekte.json} (DESIGN 37.7.9; named {@code sekteLangit}, not
 * {@code langit}, because {@code langit} is already a section of {@code kultivator-dunia.json} and a test literal, audit
 * 4.1). Public fields carry the defaults; {@link #clamp} keeps them in range. Switches only stop behaviour: items,
 * entities, payloads and template components are always registered (K1).
 */
public final class LangitConfig {

	/** Master switch of the sky systems (sites stay loaded and saved; building pauses, the base net stays). */
	public boolean aktif = true;
	/** The realm gate of DESIGN 37.3.3. */
	public boolean gerbangRanah = true;
	/** Highest sky template (1-9 sibling number) for path B (founding straight in the sky). */
	public int awanAwalMaks = 3;
	/** Multiplier of the Ascension offering (Sesaji Angkat Gunung). */
	public double sesajiSkala = 1.0D;
	/** Default plaza level yP of a new sky design. */
	public int tinggiAsal = 232;
	/** Highest horizontal shift of the throne from the ground stele. */
	public int offsetMaks = 64;
	/** World days the hall level is guaranteed after the Ascension. */
	public int jaminanHari = 30;
	/** The warded air volume (37.4.10). */
	public boolean lindungiUdara = true;
	/** The base fall catcher (LangitJatuh, 37M). Keep on for public servers. */
	public boolean jaringDasar = true;
	/** Blocks fallen below the island before the catch. */
	public int tangkapJarak = 24;
	public boolean terjunAwan = true;
	public boolean feri = true;
	/** A team sent on an outside mission leaves on the Cloud Ferry as a show (FeriAwanEntity#misi; needs {@link #feri}). */
	public boolean feriMisi = true;
	public boolean patroliPedang = true;
	public int patroliMaks = 2;
	public boolean faktorQi = true;
	public double faktorQiMaks = 1.25D;
	public boolean bajakAwan = true;
	public int bajakHari = 8;
	public boolean longbo = true;
	public boolean utusanLangit = true;
	public boolean sengsaraSaksi = true;
	public boolean sengsaraLebihKeras = false;
	public boolean kebalSalju = true;
	public boolean strukturNpc = true;
	public int sekteNpcMaks = 2;

	private static LangitConfig instance = new LangitConfig();
	private static boolean registered;

	/** From {@code LangitContent.init()}: reads (and writes back) the section. Safe to call twice. */
	public static synchronized void register() {
		if (registered) {
			return;
		}
		registered = true;
		instance = SekteConfig.section("sekteLangit", LangitConfig.class, LangitConfig::new, LangitConfig::clamp);
	}

	/** The live config (the defaults before {@link #register}). */
	public static LangitConfig get() {
		return instance;
	}

	/** True when the sky systems run ({@link #aktif}). */
	public static boolean on() {
		return instance.aktif;
	}

	public static LangitConfig clamp(LangitConfig c) {
		c.awanAwalMaks = clamp(c.awanAwalMaks, 0, 9);
		c.sesajiSkala = Double.isFinite(c.sesajiSkala) ? Math.max(0.0D, Math.min(4.0D, c.sesajiSkala)) : 1.0D;
		c.tinggiAsal = clamp(c.tinggiAsal, 208, 300);
		c.offsetMaks = clamp(c.offsetMaks, 0, 96);
		c.jaminanHari = clamp(c.jaminanHari, 0, 120);
		c.tangkapJarak = clamp(c.tangkapJarak, 8, 64);
		c.patroliMaks = clamp(c.patroliMaks, 0, 4);
		c.faktorQiMaks = Double.isFinite(c.faktorQiMaks) ? Math.max(1.0D, Math.min(1.5D, c.faktorQiMaks)) : 1.25D;
		c.bajakHari = clamp(c.bajakHari, 2, 60);
		c.sekteNpcMaks = clamp(c.sekteNpcMaks, 0, 2);
		return c;
	}

	private static int clamp(int value, int lo, int hi) {
		return Math.max(lo, Math.min(hi, value));
	}
}
