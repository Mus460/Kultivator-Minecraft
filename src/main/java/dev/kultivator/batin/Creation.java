package dev.kultivator.batin;

import dev.kultivator.KultivatorConfig;
import dev.kultivator.ascension.Stars;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Dao;
import dev.kultivator.fx.Fx;
import dev.kultivator.fx.FxType;
import dev.kultivator.purba.Purba;
import dev.kultivator.technique.spec.run.Loadout;
import dev.kultivator.technique.spec.run.Mastery;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Jurus Ciptaan Sendiri (DESIGN 26.7.3): the player fuses two comprehended Daos and a form into one of three self-named
 * Dewa ultimates ({@code own_art_1..3}, run by {@link OwnArt}). The Pencerahan Ciptaan is a {@link #CHANNEL_SECONDS} s
 * channel standing still; an interruption refunds everything because the costs are taken only at completion. The
 * composition lives in {@code extra.batin.ciptaan[slot] {a, b, f, name, q, cast}}; names reach the Kitab Teknik through
 * {@code Codex.addNameProvider} (server) and the {@code CH_BATIN} sync (client).
 * <p>
 * Sumber: I Shall Seal the Heavens ch. 1379 "Incredible Power" (Meng Hao calls his last Hex "my own Ninth Hex"); Battle
 * Through the Heavens ch. 262 and ch. 363 (Xiao Yan merges two Heavenly Flames into the Angry Buddha Lotus Flame, a flame
 * not even Medusa knew existed); Coiling Dragon (Linley fuses profound mysteries). Slots, forms, coefficients, quality,
 * costs and naming are game design.
 */
public final class Creation {

	public static final double MIN_COMPREHENSION = 60.0D;
	public static final double PURE_COMPREHENSION = 90.0D;
	public static final int PEARL_COST = 20;
	public static final int MATERIAL_COST = 1;
	public static final double QI_SHARE = 0.10D;
	public static final int REFINE_PEARL_COST = 10;
	public static final double REFINE_QI_SHARE = 0.05D;
	public static final int CHANNEL_SECONDS = 60;
	public static final double CHANNEL_MOVE = 1.5D;
	public static final double Q_MIN = 0.55D;
	public static final double Q_MAX = 1.0D;
	/** A roll lands up to this much below the ceiling of the quality formula (Sempurnakan rolls again). */
	public static final double Q_ROLL = 0.10D;
	public static final double Q_PER_KIND = 0.03D;
	public static final double PURE_BONUS = 1.15D;
	public static final int NAME_MAX = 24;
	private static final Pattern NAME = Pattern.compile("[\\p{L}\\p{N} '\\-]{1," + NAME_MAX + "}");

	/** The six forms with their coefficient at Q = 1 (DESIGN 26.7.3; none above the catalogue's strongest, 18). */
	public enum Form {
		TEBASAN("tebasan", "cut", 18.0D),
		LEDAKAN("ledakan", "burst", 15.0D),
		HUJAN("hujan", "rain", 18.0D),
		GELOMBANG("gelombang", "wave", 16.0D),
		WILAYAH("wilayah", "domain", 14.0D),
		SEGEL("segel", "seal", 16.0D);

		private final String id;
		private final String english;
		private final double coef;

		Form(String id, String english, double coef) {
			this.id = id;
			this.english = english;
			this.coef = coef;
		}

		public String id() {
			return id;
		}

		/** The coefficient at Q = 1 (for HUJAN the total of 18 impacts × 1.0, for WILAYAH 10 pulses × 1.4). */
		public double coef() {
			return coef;
		}

		public Component displayName() {
			return Component.translatable("kultivator.batin.form." + id);
		}

		public static @Nullable Form byOrdinal(int ordinal) {
			return ordinal < 0 || ordinal >= values().length ? null : values()[ordinal];
		}

		public static @Nullable Form byId(String text) {
			String key = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
			for (Form form : values()) {
				if (form.id.equals(key) || form.english.equals(key) || form.name().toLowerCase(Locale.ROOT).equals(key)) {
					return form;
				}
			}
			return null;
		}
	}

	/** One stored creation. */
	public record Art(int slot, Dao a, Dao b, Form form, String name, double q, boolean cast) {
		public boolean pure() {
			return a == b;
		}
	}

	/** A running Pencerahan Ciptaan (memory only). */
	private record Ritual(int slot, Dao a, Dao b, Form form, String name, boolean refine, long start, Vec3 pos) {
	}

	private static final Map<UUID, Ritual> RITUALS = new HashMap<>();
	private static final String[] ALIASES = {"pedang", "api", "es", "petir", "tubuh", "kehidupan", "bayangan", "ruang"};

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(Creation::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> RITUALS.clear());
		ServerPlayerEvents.LEAVE.register(player -> RITUALS.remove(player.getUUID()));
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (entity instanceof ServerPlayer player && taken > 0.0F && RITUALS.containsKey(player.getUUID())) {
				interrupt(player, "kultivator.batin.ciptaan.interrupt.hurt");
			}
		});
	}

	// ================================================================== reads

	/** A Dao by its id ({@code fire}) or Indonesian name ({@code api}). */
	public static @Nullable Dao daoOf(String text) {
		String key = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
		Dao dao = Dao.byId(key);
		if (dao != null) {
			return dao;
		}
		for (int i = 0; i < ALIASES.length; i++) {
			if (ALIASES[i].equals(key)) {
				return Dao.values()[i];
			}
		}
		return "hidup".equals(key) ? Dao.LIFE : null;
	}

	public static String alias(Dao dao) {
		return ALIASES[dao.ordinal()];
	}

	/** The creation in {@code slot} (0-2), or null. */
	public static @Nullable Art art(CultivationData data, int slot) {
		if (slot < 0 || slot >= CiptaanArts.SLOTS) {
			return null;
		}
		ListTag list = InnerWorld.view(data).getListOrEmpty("ciptaan");
		if (slot >= list.size()) {
			return null;
		}
		CompoundTag tag = list.getCompoundOrEmpty(slot);
		Dao a = Dao.byOrdinal(tag.getIntOr("a", -1));
		Dao b = Dao.byOrdinal(tag.getIntOr("b", -1));
		Form form = Form.byOrdinal(tag.getIntOr("f", -1));
		if (a == null || b == null || form == null) {
			return null;
		}
		return new Art(slot, a, b, form, tag.getStringOr("name", "?"), Mth.clamp(tag.getDoubleOr("q", Q_MIN), Q_MIN, Q_MAX),
				tag.getBooleanOr("cast", false));
	}

	public static @Nullable Art art(ServerPlayer player, int slot) {
		return art(Cultivators.get(player), slot);
	}

	/** The ceiling of the quality formula: 0.55 + (compA + compB − 120) / 200 + 0.03 × primordial kinds slain, 0.55-1.0. */
	public static double qualityCeiling(ServerPlayer player, Dao a, Dao b) {
		CultivationData data = Cultivators.get(player);
		double sum = data.comprehension(a) + data.comprehension(b);
		return Mth.clamp(Q_MIN + (sum - 120.0D) / 200.0D + Q_PER_KIND * Purba.distinctKinds(player), Q_MIN, Q_MAX);
	}

	/** Trimmed and checked: 1-24 letters, digits, spaces, apostrophes and hyphens; § and control codes refused (null). */
	public static @Nullable String cleanName(String raw) {
		if (raw == null || raw.indexOf('§') >= 0) {
			return null;
		}
		String name = raw.strip().replaceAll("\\s+", " ");
		return NAME.matcher(name).matches() ? name : null;
	}

	/** Why this creation cannot be made now, or null. */
	static @Nullable Component blocker(ServerPlayer player, int slot, Dao a, Dao b, boolean refine) {
		CultivationData data = Cultivators.get(player);
		if (!InnerWorld.born(data)) {
			return Component.translatable("kultivator.batin.ciptaan.need_batin");
		}
		if (!data.rank().isMax()) {
			return Component.translatable("kultivator.batin.ciptaan.need_peak");
		}
		if (slot < 0 || slot >= CiptaanArts.SLOTS) {
			return Component.translatable("kultivator.batin.ciptaan.bad_slot");
		}
		if (a == b) {
			if (data.comprehension(a) < PURE_COMPREHENSION) {
				return Component.translatable("kultivator.batin.ciptaan.need_comp", a.displayName(), (int) PURE_COMPREHENSION);
			}
		} else {
			if (data.comprehension(a) < MIN_COMPREHENSION) {
				return Component.translatable("kultivator.batin.ciptaan.need_comp", a.displayName(), (int) MIN_COMPREHENSION);
			}
			if (data.comprehension(b) < MIN_COMPREHENSION) {
				return Component.translatable("kultivator.batin.ciptaan.need_comp", b.displayName(), (int) MIN_COMPREHENSION);
			}
		}
		if (InnerWorld.pearlsAvailable(player) < (refine ? REFINE_PEARL_COST : PEARL_COST)) {
			return Component.translatable("kultivator.batin.ciptaan.need_pearls", refine ? REFINE_PEARL_COST : PEARL_COST);
		}
		if (!refine && InnerWorld.countTag(player, InnerWorld.PRIMORDIAL_MATERIAL) < MATERIAL_COST) {
			return Component.translatable("kultivator.batin.ciptaan.need_material");
		}
		double qi = Stars.barSize(player) * (refine ? REFINE_QI_SHARE : QI_SHARE);
		if (data.storedQi() + 1.0E-6D < qi) {
			return Component.translatable("kultivator.batin.ciptaan.need_qi", Msg.number(qi), Msg.number(data.storedQi()));
		}
		return null;
	}

	// ================================================================== the ritual

	public static boolean inRitual(ServerPlayer player) {
		return RITUALS.containsKey(player.getUUID());
	}

	/** Seconds the ritual has run, or -1. */
	static int ritualSeconds(ServerPlayer player) {
		Ritual ritual = RITUALS.get(player.getUUID());
		return ritual == null ? -1 : (int) ((player.level().getGameTime() - ritual.start) / 20L);
	}

	/** Action {@code create:slot:a:b:form:confirm:name}: starts the Pencerahan Ciptaan. */
	static void start(ServerPlayer player, int slot, @Nullable Dao a, @Nullable Dao b, @Nullable Form form, boolean confirm, String rawName) {
		if (a == null || b == null || form == null) {
			return;
		}
		String name = cleanName(rawName);
		if (name == null) {
			Msg.chat(player, Component.translatable("kultivator.batin.ciptaan.bad_name", NAME_MAX).withStyle(ChatFormatting.RED));
			return;
		}
		if (inRitual(player)) {
			Msg.chat(player, Component.translatable("kultivator.batin.ciptaan.already").withStyle(ChatFormatting.YELLOW));
			return;
		}
		if (Batin.busy(player)) {
			Msg.chat(player, Component.translatable("kultivator.spine9.refuse.busy").withStyle(ChatFormatting.YELLOW));
			return;
		}
		Component why = blocker(player, slot, a, b, false);
		if (why != null) {
			Msg.chat(player, why.copy().withStyle(ChatFormatting.RED));
			return;
		}
		if (art(player, slot) != null && !confirm) {
			Msg.chat(player, Component.translatable("kultivator.batin.ciptaan.confirm", slot + 1).withStyle(ChatFormatting.YELLOW));
			return;
		}
		begin(player, new Ritual(slot, a, b, form, name, false, player.level().getGameTime(), player.position()));
	}

	/** Action {@code refine:slot}: Sempurnakan, a new roll of the same composition at half the cost (the higher Q stays). */
	static void startRefine(ServerPlayer player, int slot) {
		Art current = art(player, slot);
		if (current == null || inRitual(player)) {
			return;
		}
		if (Batin.busy(player)) {
			Msg.chat(player, Component.translatable("kultivator.spine9.refuse.busy").withStyle(ChatFormatting.YELLOW));
			return;
		}
		Component why = blocker(player, slot, current.a(), current.b(), true);
		if (why != null) {
			Msg.chat(player, why.copy().withStyle(ChatFormatting.RED));
			return;
		}
		begin(player, new Ritual(slot, current.a(), current.b(), current.form(), current.name(), true, player.level().getGameTime(), player.position()));
	}

	private static void begin(ServerPlayer player, Ritual ritual) {
		RITUALS.put(player.getUUID(), ritual);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8F, 1.3F);
		Msg.chat(player, Component.translatable(ritual.refine() ? "kultivator.batin.ciptaan.refine_start" : "kultivator.batin.ciptaan.start",
				CHANNEL_SECONDS).withStyle(ChatFormatting.LIGHT_PURPLE));
		Batin.dirty(player);
	}

	static void cancel(ServerPlayer player) {
		if (RITUALS.containsKey(player.getUUID())) {
			interrupt(player, "kultivator.batin.ciptaan.interrupt.cancel");
		}
	}

	private static void interrupt(ServerPlayer player, String key) {
		if (RITUALS.remove(player.getUUID()) != null) {
			Msg.chat(player, Component.translatable(key).withStyle(ChatFormatting.YELLOW));
			Msg.chat(player, Component.translatable("kultivator.batin.ciptaan.refund").withStyle(ChatFormatting.GRAY));
			Batin.dirty(player);
		}
	}

	private static void tick(MinecraftServer server) {
		if (RITUALS.isEmpty() || server.getTickCount() % 20 != 9) {
			return;
		}
		for (Map.Entry<UUID, Ritual> entry : List.copyOf(RITUALS.entrySet())) {
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			Ritual ritual = entry.getValue();
			if (player == null) {
				RITUALS.remove(entry.getKey());
				continue;
			}
			if (!player.isAlive() || player.position().distanceToSqr(ritual.pos()) > CHANNEL_MOVE * CHANNEL_MOVE) {
				interrupt(player, "kultivator.batin.ciptaan.interrupt.move");
				continue;
			}
			int seconds = (int) ((player.level().getGameTime() - ritual.start()) / 20L);
			int progress = Math.min(100, seconds * 100 / CHANNEL_SECONDS);
			int packed = progress | (ritual.a().ordinal() + 1) << 8 | (ritual.b().ordinal() + 1) << 12;
			Fx.send(player.level(), player.position(), FxType.BATIN, BatinFxIds.RITUAL, packed, player.getBbHeight());
			Msg.actionBar(player, Component.translatable("kultivator.batin.ciptaan.progress", Math.min(seconds, CHANNEL_SECONDS), CHANNEL_SECONDS)
					.withStyle(ChatFormatting.LIGHT_PURPLE));
			if (seconds >= CHANNEL_SECONDS) {
				RITUALS.remove(entry.getKey());
				complete(player, ritual);
			}
		}
	}

	/** The end of the channel: the costs are taken now, then the art is born (or refined). */
	private static void complete(ServerPlayer player, Ritual ritual) {
		Component why = blocker(player, ritual.slot(), ritual.a(), ritual.b(), ritual.refine());
		if (why != null) {
			Msg.chat(player, why.copy().withStyle(ChatFormatting.RED));
			Batin.dirty(player);
			return;
		}
		CultivationData data = Cultivators.get(player);
		double qi = Stars.barSize(player) * (ritual.refine() ? REFINE_QI_SHARE : QI_SHARE);
		if (!InnerWorld.takePearls(player, ritual.refine() ? REFINE_PEARL_COST : PEARL_COST)) {
			return;
		}
		if (!ritual.refine()) {
			InnerWorld.takeTag(player, InnerWorld.PRIMORDIAL_MATERIAL, MATERIAL_COST);
		}
		data.setStoredQi(Math.max(0.0D, data.storedQi() - qi));
		double roll = roll(player, ritual.a(), ritual.b());
		if (ritual.refine()) {
			Art current = art(player, ritual.slot());
			double before = current == null ? Q_MIN : current.q();
			double kept = Math.max(before, roll);
			write(player, ritual.slot(), ritual.a(), ritual.b(), ritual.form(), ritual.name(), kept, current != null && current.cast());
			Msg.chat(player, Component.translatable("kultivator.batin.ciptaan.refined", Math.round(roll * 100.0D), Math.round(kept * 100.0D))
					.withStyle(ChatFormatting.GOLD));
			if (kept >= Q_MAX - 1.0E-6D) {
				Advancements.grant(player, "own_art_perfect");
			}
		} else {
			born(player, ritual.slot(), ritual.a(), ritual.b(), ritual.form(), ritual.name(), roll);
		}
		Cultivators.dirty(player);
		Batin.dirty(player);
	}

	/** A quality roll: the formula's ceiling minus up to 0.10, at least 0.55. */
	static double roll(ServerPlayer player, Dao a, Dao b) {
		double ceiling = qualityCeiling(player, a, b);
		return Mth.clamp(ceiling - Q_ROLL * player.getRandom().nextDouble(), Q_MIN, Q_MAX);
	}

	/** Writes a new art into {@code slot} (an old one there is destroyed first), learns its spec and tells the player. */
	static void born(ServerPlayer player, int slot, Dao a, Dao b, Form form, String name, double q) {
		if (art(player, slot) != null) {
			destroy(player, slot, false);
		}
		write(player, slot, a, b, form, name, q, false);
		CultivationData data = Cultivators.get(player);
		data.learn(CiptaanArts.IDS[slot]);
		LandSpirit.log(InnerWorld.root(data), InnerWorld.day(player), "art", slot + 1, (int) Math.round(q * 100.0D));
		Advancements.grant(player, "own_art");
		if (q >= Q_MAX - 1.0E-6D) {
			Advancements.grant(player, "own_art_perfect");
		}
		int packed = form.ordinal() | (a.ordinal() + 1) << 4 | (b.ordinal() + 1) << 8;
		Fx.send(player.level(), player.position(), FxType.BATIN, BatinFxIds.ART_BORN, packed, player.getBbHeight());
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.9F, 1.0F);
		Msg.title(player, Component.literal(name).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
				Component.translatable("kultivator.batin.ciptaan.title_sub", Math.round(q * 100.0D)).withStyle(ChatFormatting.YELLOW), 10, 60, 20);
		Msg.chat(player, Component.translatable("kultivator.batin.ciptaan.born", name, a.displayName(), b.displayName(), form.displayName(),
				Math.round(q * 100.0D), slot + 1).withStyle(ChatFormatting.GOLD));
		Cultivators.dirty(player);
		Batin.dirty(player);
	}

	private static void write(ServerPlayer player, int slot, Dao a, Dao b, Form form, String name, double q, boolean cast) {
		CompoundTag batin = InnerWorld.root(Cultivators.get(player));
		ListTag list = InnerWorld.list(batin, "ciptaan");
		while (list.size() < CiptaanArts.SLOTS) {
			list.add(new CompoundTag());
		}
		CompoundTag tag = new CompoundTag();
		tag.putInt("a", a.ordinal());
		tag.putInt("b", b.ordinal());
		tag.putInt("f", form.ordinal());
		tag.putString("name", name);
		tag.putDouble("q", Mth.clamp(q, Q_MIN, Q_MAX));
		tag.putBoolean("cast", cast);
		list.set(slot, tag);
	}

	/** The first cast of an art (EXTRA {@code OWN_ART_BORN}); true when this was the first. */
	static boolean markCast(ServerPlayer player, int slot) {
		ListTag list = InnerWorld.view(Cultivators.get(player)).getListOrEmpty("ciptaan");
		if (slot < 0 || slot >= list.size()) {
			return false;
		}
		CompoundTag tag = list.getCompoundOrEmpty(slot);
		if (tag.getBooleanOr("cast", false) || tag.isEmpty()) {
			return false;
		}
		tag.putBoolean("cast", true);
		Cultivators.dirty(player);
		return true;
	}

	/** Destroys the creation in {@code slot}: un-learns the spec, clears its loadout slots, its selection and its mastery. */
	static boolean destroy(ServerPlayer player, int slot, boolean tell) {
		if (art(player, slot) == null) {
			return false;
		}
		CultivationData data = Cultivators.get(player);
		ListTag list = InnerWorld.list(InnerWorld.root(data), "ciptaan");
		if (slot < list.size()) {
			list.set(slot, new CompoundTag());
		}
		String id = CiptaanArts.IDS[slot];
		String[] slots = Loadout.slots(data);
		for (int i = 0; i < slots.length; i++) {
			if (id.equals(slots[i])) {
				Loadout.set(player, i, "");
			}
		}
		Mastery.setLevel(player, id, 0);
		if (id.equals(data.selectedId())) {
			data.setSelectedId(null);
		}
		data.forget(id);
		Cultivators.dirty(player);
		Batin.dirty(player);
		if (tell) {
			Msg.chat(player, Component.translatable("kultivator.batin.ciptaan.destroyed", slot + 1).withStyle(ChatFormatting.GRAY));
		}
		return true;
	}

	/** {@code uji ciptaan}: creates at once, without the ritual or any cost (the requirements are not checked). */
	static void createNow(ServerPlayer player, int slot, Dao a, Dao b, Form form, double q) {
		String name = capital(alias(a)) + (a == b ? "" : " " + capital(alias(b))) + " " + capital(form.id());
		born(player, slot, a, b, form, name.length() > NAME_MAX ? name.substring(0, NAME_MAX) : name, Mth.clamp(q, Q_MIN, Q_MAX));
	}

	private static String capital(String text) {
		return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
	}

	/** True when created arts may be made (config and an inner world). */
	static boolean enabled() {
		return KultivatorConfig.get().innerWorld;
	}

	private Creation() {
	}
}
