package dev.kultivator.client.pusakalangit;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.kultivator.client.Keys;
import dev.kultivator.client.dampak.DampakClient;
import dev.kultivator.client.dampak.cinema.TargetMark;
import dev.kultivator.client.dampak.cinema.Vistas;
import dev.kultivator.client.epic.EpicDirector;
import dev.kultivator.client.epic.EpicSettings;
import dev.kultivator.client.epic.Scene;
import dev.kultivator.client.epic.camera.CinematicDirector;
import dev.kultivator.client.epic.camera.ShotGuard;
import dev.kultivator.client.pusakalangit.cinema.PusakaFilm;
import dev.kultivator.data.TechniqueRegistry;
import dev.kultivator.data.TechniqueSpec;
import dev.kultivator.network.Actions;
import dev.kultivator.network.Payloads;
import dev.kultivator.technique.spec.dsl.Dampak;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

/**
 * Client entry of Phase 10b Titah Langit (DESIGN 29, module M2 "client cinema and GUI"): the {@code TitahSync}
 * receiver (contract C10b.5) and its cache, the opener of the jade-scroll menu ({@code OpenScreen.TITAH}), the actions
 * the menu sends ({@code Actions.TITAH}, arg = {@code op << 16 | skill << 8 | field}, text = the value), the world
 * module's start ({@code TitahWorld.init}, M3) and {@code /sinematik pusaka}. The TITAH technique events reach the film
 * through the Phase 10 hub ({@code DampakClient} → {@code SkyRide} → {@link PusakaFilm}). Client thread only; never
 * throws into the game.
 * <p>
 * Tingkat Titah (DESIGN 29.16.10, contracts C10b.8 and C10b.9): each decree also carries its tier {@code t} (1-9), the
 * highest tier allowed {@code tMax} with the reason {@code tWhy}, {@code perm} (this cast would carve permanently) and
 * the readout numbers {@code ro.tier, ro.dmgR, ro.carveR, ro.weapon, ro.permanent}; {@link #FIELD_T} sets the tier. The
 * {@code TitahFar} list of giant monuments goes to the world module's {@code FarRelic}.
 */
public final class TitahClient {

	private static final String K = "kultivator.titah_client.";

	// Action ops (C10b.5).
	public static final int OP_SYNC = 0;
	public static final int OP_SET = 1;
	public static final int OP_SELECT = 2;
	public static final int OP_RESET = 3;
	// Fields of OP_SET, in the order of Titah.Settings (k, d, v, h, m).
	public static final int FIELD_K = 0;
	public static final int FIELD_D = 1;
	public static final int FIELD_V = 2;
	public static final int FIELD_H = 3;
	public static final int FIELD_M = 4;
	/** Tingkat (29.16.3): sent as "1".."9". */
	public static final int FIELD_T = 5;
	/** The tier at which a real carve becomes permanent (29.16.5; the server's {@code Titah.PERMANENT_TIER}). */
	public static final int PERMANENT_TIER = 6;

	/** Kekuatan steps (29.2.2, log steps). */
	public static final float[] K_STEPS = {0.1F, 0.25F, 0.5F, 0.75F, 1.0F, 1.5F, 2.0F, 3.0F, 4.0F, 5.0F, 7.0F, 10.0F};
	/** Kerusakan steps (0-100 % in tens), as fractions. */
	public static final float[] D_STEPS = {0.0F, 0.1F, 0.2F, 0.3F, 0.4F, 0.5F, 0.6F, 0.7F, 0.8F, 0.9F, 1.0F};
	/** Kecepatan steps (incl. the film). */
	public static final float[] V_STEPS = {0.3F, 0.5F, 0.75F, 1.0F, 1.5F, 2.0F, 3.0F};
	/** Tingkat steps (29.16.10). */
	public static final float[] T_STEPS = {1.0F, 2.0F, 3.0F, 4.0F, 5.0F, 6.0F, 7.0F, 8.0F, 9.0F};
	private static final String KT = "kultivator.titah_tingkat_client.";
	/** The decree glyphs: sword, pillar, dipper, mountain. */
	public static final String[] GLYPHS = {"劍", "柱", "斗", "山"};
	/** Base visual radius at ×1 (29.3.2) and base cooldown in seconds (29.2.1), for the client's own readout fallback. */
	private static final float[] BASE_RADIUS = {96.0F, 64.0F, 48.0F, 32.0F};
	private static final int[] BASE_COOLDOWN = {600, 600, 900, 900};

	/** One decree's state from the last sync (the sliders write here first, the next sync confirms the server's values). */
	public static final class Decree {
		public boolean unlocked;
		public int cdLeft;
		public int cdTotal;
		public float k = 1.0F;
		public float d = 1.0F;
		public float v = 1.0F;
		public boolean h = true;
		public boolean m = true;
		public float kMax = 1.0F;
		public String readout = "";
		/** A lang key saying why the decree is locked (empty when open or not sent). */
		public String why = "";
		// the server's readout numbers ({@code ro}); -1 when not sent
		public int rVis = -1;
		public int rDmg = -1;
		public int blocks = -1;
		public int cdSec = -1;
		public int qiPct = -1;
		// Tingkat Titah (29.16.3): the used tier, its limit and why, the permanent flag and the scaled readout numbers
		public int t = 1;
		public int tMax = 9;
		public String tWhy = "";
		public boolean perm;
		/** The sword's or pillar's length in blocks (0 for Bintang and Gunung; -1 when not sent). */
		public int weapon = -1;
		public int dmgR = -1;
		public int carveR = -1;
		/** The tier of the server's readout numbers (-1 when not sent). */
		public int roTier = -1;
		public boolean roPermanent;
	}

	private static final Decree[] DECREES = {new Decree(), new Decree(), new Decree(), new Decree()};
	private static int selected;
	private static boolean kehancuranOn;
	/** Ticks of the shared seal recovery left at the sync (every decree waits for it). */
	private static int recover;
	private static boolean synced;
	private static long syncTick;
	private static long ticks;
	/** Bumped on every sync, so an open screen can refresh its controls. */
	private static int version;

	private TitahClient() {
	}

	/** Called once from {@code KultivatorClient.onInitializeClient}, right after {@code DampakClient.init()}. */
	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(Payloads.TitahSync.TYPE, (payload, context) -> {
			try {
				onSync(payload.data());
			} catch (RuntimeException exception) {
				DampakClient.warn("titah sync", exception);
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> ticks++);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
		try {
			dev.kultivator.client.pusakalangit.world.TitahWorld.init();
		} catch (RuntimeException | LinkageError exception) {
			DampakClient.warn("titah world init", exception);
		}
		try {
			// Tingkat Titah (C10b.9): the live giant monuments of this dimension, for the far relics
			ClientPlayNetworking.registerGlobalReceiver(Payloads.TitahFar.TYPE, (payload, context) -> {
				try {
					dev.kultivator.client.pusakalangit.world.FarRelic.onFar(payload.data());
				} catch (RuntimeException | LinkageError exception) {
					DampakClient.warn("titah far", exception);
				}
			});
		} catch (RuntimeException | LinkageError exception) {
			DampakClient.warn("titah far receiver", exception);
		}
	}

	// ================================================================ the sync (C10b.5)

	/**
	 * Reads one {@code TitahSync}: {@code skills[4]} (unlocked, why, cdLeft, cdMax, k, d (0-100), v, h, m, kMax, readout,
	 * ro {rVis, rDmg, blocks, cdSec, qiPct}), {@code selected}, {@code kehancuranOn}, {@code recover}; Tingkat Titah adds
	 * per skill {@code t, tMax, tWhy, perm} and {@code ro {tier, dmgR, carveR, weapon, permanent}} (missing = tier 1).
	 */
	public static void onSync(CompoundTag data) {
		ListTag skills = data.getListOrEmpty("skills");
		for (int i = 0; i < DECREES.length && i < skills.size(); i++) {
			CompoundTag tag = skills.getCompoundOrEmpty(i);
			Decree decree = DECREES[i];
			decree.unlocked = tag.getBooleanOr("unlocked", false);
			decree.cdLeft = Math.max(0, tag.getIntOr("cdLeft", 0));
			decree.k = tag.getFloatOr("k", 1.0F);
			// Kerusakan travels as whole percents (0-100)
			decree.d = Math.max(0.0F, Math.min(1.0F, tag.getFloatOr("d", 100.0F) / 100.0F));
			decree.v = tag.getFloatOr("v", 1.0F);
			decree.h = tag.getBooleanOr("h", true);
			decree.m = tag.getBooleanOr("m", true);
			decree.kMax = Math.max(K_STEPS[0], tag.getFloatOr("kMax", 1.0F));
			decree.readout = tag.getStringOr("readout", "");
			decree.why = tag.getStringOr("why", "");
			int total = tag.getIntOr("cdMax", tag.getIntOr("cdTotal", 0));
			decree.cdTotal = total > 0 ? total : Math.round(BASE_COOLDOWN[i] * 20.0F * (float) Math.sqrt(Math.max(1.0F, decree.k)));
			CompoundTag ro = tag.getCompoundOrEmpty("ro");
			decree.rVis = ro.getIntOr("rVis", -1);
			decree.rDmg = ro.getIntOr("rDmg", -1);
			decree.blocks = ro.getIntOr("blocks", -1);
			decree.cdSec = ro.getIntOr("cdSec", -1);
			decree.qiPct = ro.getIntOr("qiPct", -1);
			decree.t = Math.max(1, Math.min(9, tag.getIntOr("t", 1)));
			decree.tMax = Math.max(1, Math.min(9, tag.getIntOr("tMax", 9)));
			decree.tWhy = tag.getStringOr("tWhy", "");
			decree.perm = tag.getBooleanOr("perm", false);
			decree.roTier = ro.getIntOr("tier", -1);
			decree.dmgR = ro.getIntOr("dmgR", decree.rDmg);
			decree.carveR = ro.getIntOr("carveR", -1);
			decree.weapon = ro.getIntOr("weapon", -1);
			decree.roPermanent = ro.getBooleanOr("permanent", false);
		}
		selected = Math.max(0, Math.min(3, data.getIntOr("selected", selected)));
		kehancuranOn = data.getBooleanOr("kehancuranOn", false);
		recover = Math.max(0, data.getIntOr("recover", 0));
		synced = true;
		syncTick = ticks;
		version++;
	}

	private static void clear() {
		for (int i = 0; i < DECREES.length; i++) {
			DECREES[i] = new Decree();
		}
		selected = 0;
		kehancuranOn = false;
		recover = 0;
		synced = false;
		version++;
	}

	public static Decree decree(int skill) {
		return DECREES[Math.max(0, Math.min(3, skill))];
	}

	public static int selected() {
		return selected;
	}

	public static boolean kehancuranOn() {
		return kehancuranOn;
	}

	public static boolean synced() {
		return synced;
	}

	public static int version() {
		return version;
	}

	/** Cooldown ticks left now: the decree's own or the shared seal recovery, counted down on the client since the sync. */
	public static int cooldownLeft(int skill) {
		Decree decree = decree(skill);
		return (int) Math.max(0L, Math.max(decree.cdLeft, recover) - (ticks - syncTick));
	}

	/** The index in {@link #K_STEPS} of the realm's Kekuatan maximum (the largest step ≤ kMax). */
	public static int kMaxIndex(int skill) {
		float max = decree(skill).kMax;
		int best = 0;
		for (int i = 0; i < K_STEPS.length; i++) {
			if (K_STEPS[i] <= max + 1.0E-3F) {
				best = i;
			}
		}
		return best;
	}

	/** The nearest step index of {@code value}. */
	public static int nearest(float[] steps, float value) {
		int best = 0;
		for (int i = 1; i < steps.length; i++) {
			if (Math.abs(steps[i] - value) < Math.abs(steps[best] - value)) {
				best = i;
			}
		}
		return best;
	}

	/**
	 * The readout line of a decree (29.2.2): the server's numbers ({@code ro}) in the player's language, else its plain
	 * line, else the client's estimate from 29.3.2 (R_vis = base × Kekuatan clamped to 128, wounds ≤ 40 b, ≤ 8,192
	 * blocks, the cooldown × √Kekuatan, qi 85 %).
	 */
	public static Component readout(int skill) {
		Decree decree = decree(skill);
		if (decree.roTier >= 1 && decree.rVis >= 0 && decree.cdSec >= 0) {
			// Tingkat Titah (29.16.3): "Tingkat 7 · R ≈ 805 b · luka ≤ 671 b · pahat ≤ 201 b · pedang 376 b · PERMANEN · jeda · qi"
			return tierReadout(skill, decree.roTier, decree.rVis, decree.dmgR >= 0 ? decree.dmgR : decree.rDmg, decree.carveR, decree.weapon, decree.roPermanent,
					decree.cdSec, decree.qiPct >= 0 ? decree.qiPct : 85);
		}
		if (decree.t >= 2 && decree.rVis < 0) {
			// the slider moved and the sync is on its way: the client estimate of the tier (29.16.10 fallback)
			return tierEstimate(skill, decree);
		}
		if (decree.rVis >= 0 && decree.rDmg >= 0 && decree.blocks >= 0 && decree.cdSec >= 0) {
			return Component.translatable(K + "screen.readout", decree.rVis, decree.rDmg, grouped(decree.blocks), minutes(decree.cdSec),
					decree.qiPct >= 0 ? decree.qiPct : 85);
		}
		if (!decree.readout.isEmpty()) {
			return Component.literal(decree.readout);
		}
		float radius = Math.max(4.0F, Math.min(128.0F, BASE_RADIUS[skill] * decree.k));
		int wound = Math.round(Math.min(radius, 40.0F));
		int seconds = Math.round(BASE_COOLDOWN[skill] * (float) Math.sqrt(Math.max(1.0F, decree.k)));
		return Component.translatable(K + "screen.readout", Math.round(radius), wound, "8.192", minutes(seconds), 85);
	}

	/** The tier readout line from its numbers (29.16.3), in the player's language. */
	private static Component tierReadout(int skill, int tier, int rVis, int dmgR, int carveR, int weapon, boolean permanent, int cdSec, int qiPct) {
		Component weaponPart = weapon > 0 && skill <= 1 ? Component.translatable(KT + (skill == 0 ? "readout.sword" : "readout.pillar"), grouped(weapon))
				: Component.empty();
		Component permPart = permanent ? Component.translatable(KT + "readout.perm") : Component.empty();
		return Component.translatable(KT + "readout", tier, grouped(Math.max(0, rVis)), grouped(Math.max(0, dmgR)), carveR >= 0 ? grouped(carveR) : "-", weaponPart,
				permPart, minutes(Math.max(0, cdSec)), qiPct);
	}

	/**
	 * The client estimate of a tier (29.16.1) until the server answers: R ≈ base × K × s (≤ 2500), wounds ≤ 40 × s, the
	 * carve ≤ R / 4, the weapon ≈ 1.87 × 12 √K × s or 3 × 10 √K × s, the cooldown × √K × √t.
	 */
	private static Component tierEstimate(int skill, Decree decree) {
		float s = (float) Math.pow(1.6D, decree.t - 1);
		float k = Math.max(0.1F, decree.k);
		float base = BASE_RADIUS[skill] * (skill == 0 ? 0.5F : 1.0F);
		int radius = Math.round(Math.min(2500.0F, Math.max(4.0F, Math.min(256.0F, base * k)) * s));
		int wound = Math.round(Math.min(radius, 40.0F * s));
		int carve = Math.round(Math.min(radius * 0.25F, (skill == 1 ? 16.0F : skill == 0 ? 12.0F : 8.0F) * s));
		int weapon = skill == 0 ? Math.round(1.87F * Math.min(1100.0F, Math.max(6.0F, Math.min(24.0F, 12.0F * (float) Math.sqrt(k))) * s))
				: skill == 1 ? Math.round(3.0F * Math.min(1100.0F, Math.max(5.0F, Math.min(20.0F, 10.0F * (float) Math.sqrt(k))) * s)) : 0;
		int seconds = Math.round(BASE_COOLDOWN[skill] * (float) Math.sqrt(Math.max(1.0F, k)) * (float) Math.sqrt(decree.t));
		return tierReadout(skill, decree.t, radius, wound, carve, weapon, permanent(skill), seconds, 85);
	}

	/** The index in {@link #T_STEPS} of the highest tier allowed now. */
	public static int tMaxIndex(int skill) {
		return Math.max(0, Math.min(T_STEPS.length - 1, decree(skill).tMax - 1));
	}

	/** True when this decree at its tier would carve permanently (tier 6-9 with Mode Kehancuran and Hancurkan tanah). */
	public static boolean permanent(int skill) {
		Decree decree = decree(skill);
		return decree.t >= PERMANENT_TIER && (decree.perm || kehancuranOn && decree.h && decree.roTier < 0);
	}

	/** A whole number with dots between the thousands (8.192). */
	private static String grouped(int value) {
		return String.format(Locale.ROOT, "%,d", value).replace(',', '.');
	}

	/** Seconds as "m:ss". */
	public static String minutes(int seconds) {
		return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
	}

	// ================================================================ actions (C10b.5)

	public static void requestSync() {
		send(OP_SYNC, 0, 0, "");
	}

	/** Selects the decree the seal casts (the local view follows at once). */
	public static void select(int skill) {
		selected = Math.max(0, Math.min(3, skill));
		send(OP_SELECT, selected, 0, "");
	}

	/** Back to the decree's defaults ("Bawaan"); the server answers with a sync. */
	public static void reset(int skill) {
		send(OP_RESET, skill, 0, "");
	}

	/**
	 * Sets a number field (Kekuatan, Kerusakan as a 0..1 fraction, sent as whole percents, Kecepatan); the local copy
	 * follows at once and the server's answer (a sync) confirms the sanitised value.
	 */
	public static void set(int skill, int field, float value) {
		Decree decree = decree(skill);
		switch (field) {
			case FIELD_K -> decree.k = value;
			case FIELD_D -> decree.d = value;
			case FIELD_V -> decree.v = value;
			case FIELD_T -> decree.t = Math.max(1, Math.min(9, Math.round(value)));
			default -> {
			}
		}
		decree.readout = "";
		decree.rVis = -1;
		decree.roTier = -1;
		String text = field == FIELD_D ? String.valueOf(Math.round(value * 100.0F)) : field == FIELD_T ? String.valueOf(decree.t) : trim(value);
		send(OP_SET, skill, field, text);
	}

	/** Sets a switch field (Hancurkan tanah, Tinggalkan pusaka); sent as "1" / "0". */
	public static void set(int skill, int field, boolean on) {
		Decree decree = decree(skill);
		if (field == FIELD_H) {
			decree.h = on;
		} else if (field == FIELD_M) {
			decree.m = on;
		}
		send(OP_SET, skill, field, on ? "1" : "0");
	}

	private static String trim(float value) {
		String text = String.format(Locale.ROOT, "%.2f", value);
		while (text.contains(".") && (text.endsWith("0") || text.endsWith("."))) {
			text = text.substring(0, text.length() - 1);
		}
		return text;
	}

	private static void send(int op, int skill, int field, String text) {
		Keys.send(new Payloads.Action(Actions.TITAH, (op & 0xFF) << 16 | (skill & 0xFF) << 8 | (field & 0xFF), text));
	}

	// ================================================================ the screen

	/** {@code OpenScreen.TITAH}: opens the jade-scroll menu ({@code arg} = the decree to show, else the selected one). */
	public static void openScreen(int arg) {
		Minecraft minecraft = Minecraft.getInstance();
		int skill = arg >= 0 && arg <= 3 ? arg : selected;
		minecraft.gui.setScreen(new TitahScreen(skill));
		requestSync();
	}

	// ================================================================ /sinematik pusaka (29.11)

	/** The {@code pusaka} branch of {@code /sinematik} (added by {@code EpicClientCommands}). */
	public static LiteralArgumentBuilder<FabricClientCommandSource> command() {
		LiteralArgumentBuilder<FabricClientCommandSource> root = ClientCommands.literal("pusaka");
		LiteralArgumentBuilder<FabricClientCommandSource> films = ClientCommands.literal("film");
		LiteralArgumentBuilder<FabricClientCommandSource> atur = ClientCommands.literal("atur");
		for (int i = 0; i < EpicSettings.TITAH_SKILLS.length; i++) {
			int skill = i;
			String name = EpicSettings.TITAH_SKILLS[i];
			films.then(ClientCommands.literal(name).executes(context -> film(context, skill, 160, 1))
					.then(ClientCommands.argument("detik", IntegerArgumentType.integer(2, 12))
							.executes(context -> film(context, skill, IntegerArgumentType.getInteger(context, "detik") * 20, 1))
							// Tingkat Titah (29.16.15): the film framing of that tier, still under every gate
							.then(ClientCommands.argument("tingkat", IntegerArgumentType.integer(1, 9))
									.executes(context -> film(context, skill, IntegerArgumentType.getInteger(context, "detik") * 20,
											IntegerArgumentType.getInteger(context, "tingkat"))))));
			LiteralArgumentBuilder<FabricClientCommandSource> modes = ClientCommands.literal(name);
			for (EpicSettings.TitahFilm mode : EpicSettings.TitahFilm.values()) {
				modes.then(ClientCommands.literal(mode.id()).executes(context -> {
					EpicSettings.setTitahFilm(skill, mode);
					return ok(context, Component.translatable(K + "cmd.set", decreeName(skill), Component.translatable(K + "film." + mode.id())));
				}));
			}
			atur.then(modes);
		}
		root.then(films);
		root.then(atur);
		LiteralArgumentBuilder<FabricClientCommandSource> vista = ClientCommands.literal("vista");
		for (Dampak.Vista value : new Dampak.Vista[]{Dampak.Vista.ISTANA_LANGIT, Dampak.Vista.LAUT_TIMUR, Dampak.Vista.BIDUK_UTARA, Dampak.Vista.KUA_E}) {
			vista.then(ClientCommands.literal(value.name().toLowerCase(Locale.ROOT)).executes(context -> vista(context, value)));
		}
		root.then(vista);
		root.then(ClientCommands.literal("info").executes(TitahClient::info));
		root.then(ClientCommands.literal("jauh").executes(TitahClient::far));
		return root;
	}

	/** {@code /sinematik pusaka jauh}: the far relics with distance, LOD step and scale (29.16.15). */
	private static int far(CommandContext<FabricClientCommandSource> context) {
		FabricClientCommandSource source = context.getSource();
		java.util.List<String> lines;
		try {
			lines = dev.kultivator.client.pusakalangit.world.TitahWorld.farRelics();
		} catch (RuntimeException | LinkageError exception) {
			lines = java.util.List.of();
		}
		source.sendFeedback(Component.translatable(KT + "cmd.far.title", lines.size()).withStyle(ChatFormatting.GOLD));
		if (lines.isEmpty()) {
			source.sendFeedback(Component.translatable(KT + "cmd.far.none").withStyle(ChatFormatting.GRAY));
		}
		for (int i = 0; i < lines.size() && i < 24; i++) {
			source.sendFeedback(Component.literal(lines.get(i)).withStyle(ChatFormatting.GRAY));
		}
		return 1;
	}

	private static int film(CommandContext<FabricClientCommandSource> context, int skill, int ticks, int tier) {
		TechniqueSpec spec = TechniqueRegistry.byId("titah_" + EpicSettings.TITAH_SKILLS[skill]);
		if (spec == null || spec.dampak() == null) {
			context.getSource().sendError(Component.translatable(K + "cmd.unknown", EpicSettings.TITAH_SKILLS[skill]));
			return 0;
		}
		String refused = PusakaFilm.test(spec, skill, ahead(24.0D), ticks, tier);
		if (refused != null) {
			context.getSource().sendError(Component.translatable(K + "cmd.film_refused", refused, ShotGuard.reason().isEmpty() ? "-" : ShotGuard.reason()));
			return 0;
		}
		if (tier >= 2) {
			return ok(context, Component.translatable(KT + "cmd.film", decreeName(skill), Math.round(ticks / 20.0F), tier));
		}
		return ok(context, Component.translatable(K + "cmd.film", decreeName(skill), Math.round(ticks / 20.0F)));
	}

	private static int vista(CommandContext<FabricClientCommandSource> context, Dampak.Vista vista) {
		Minecraft minecraft = Minecraft.getInstance();
		TechniqueSpec spec = null;
		for (String name : EpicSettings.TITAH_SKILLS) {
			TechniqueSpec candidate = TechniqueRegistry.byId("titah_" + name);
			if (candidate != null && candidate.dampak() != null && candidate.dampak().vista() == vista) {
				spec = candidate;
			}
		}
		Scene scene = EpicDirector.testScene();
		if (minecraft.player == null || scene == null || spec == null) {
			context.getSource().sendError(Component.translatable(K + "cmd.unknown", vista.name().toLowerCase(Locale.ROOT)));
			return 0;
		}
		Vistas.test(scene, vista, spec, minecraft.player.getEyePosition(), minecraft.player.getViewVector(1.0F), 100, minecraft.player.getId());
		return ok(context, Component.translatable(K + "cmd.vista", vista.name().toLowerCase(Locale.ROOT)));
	}

	private static int info(CommandContext<FabricClientCommandSource> context) {
		FabricClientCommandSource source = context.getSource();
		source.sendFeedback(Component.translatable(K + "cmd.info.title").withStyle(ChatFormatting.GOLD));
		StringBuilder films = new StringBuilder();
		for (int i = 0; i < EpicSettings.TITAH_SKILLS.length; i++) {
			films.append(i == 0 ? "" : ", ").append(EpicSettings.TITAH_SKILLS[i]).append('=').append(EpicSettings.titahFilm(i).id());
		}
		source.sendFeedback(Component.translatable(K + "cmd.info.gates",
				Component.translatable("kultivator.momen.setting.camera." + CinematicDirector.mode().id()),
				Component.translatable("kultivator.dampak.setting.skyride." + EpicSettings.skyRide().id()),
				films.toString(),
				ShotGuard.reason().isEmpty() ? "-" : ShotGuard.reason(),
				PusakaFilm.lastRefusal().isEmpty() ? "-" : PusakaFilm.lastRefusal()).withStyle(ChatFormatting.GRAY));
		String world;
		try {
			world = dev.kultivator.client.pusakalangit.world.TitahWorld.stats();
		} catch (RuntimeException | LinkageError exception) {
			world = "-";
		}
		source.sendFeedback(Component.translatable(K + "cmd.info.world", world).withStyle(ChatFormatting.GRAY));
		return 1;
	}

	/** The decree's name (the spec's display name, else the fallback key). */
	public static Component decreeName(int skill) {
		TechniqueSpec spec = TechniqueRegistry.byId("titah_" + EpicSettings.TITAH_SKILLS[Math.max(0, Math.min(3, skill))]);
		return spec != null ? spec.displayName() : Component.translatable(K + "decree." + skill);
	}

	/** A point on the ground about {@code distance} blocks along the player's look. */
	private static Vec3 ahead(double distance) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null || minecraft.level == null) {
			return Vec3.ZERO;
		}
		Vec3 eye = minecraft.player.getEyePosition();
		Vec3 end = eye.add(minecraft.player.getViewVector(1.0F).scale(distance));
		BlockHitResult hit = minecraft.level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, minecraft.player));
		return TargetMark.ground(hit.getType() == HitResult.Type.MISS ? end : hit.getLocation());
	}

	private static int ok(CommandContext<FabricClientCommandSource> context, Component message) {
		context.getSource().sendFeedback(message.copy().withStyle(ChatFormatting.GREEN));
		return 1;
	}
}
