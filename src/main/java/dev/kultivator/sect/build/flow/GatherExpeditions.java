package dev.kultivator.sect.build.flow;

import dev.kultivator.data.Rank;
import dev.kultivator.data.Realm;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.disciple.DuelRules;
import dev.kultivator.disciple.path.DisciplePaths;
import dev.kultivator.dunia.kota42.api.KotaGerakId;
import dev.kultivator.fx.Fx;
import dev.kultivator.sect.build.api.Bahan;
import dev.kultivator.sect.build.api.EngineApi;
import dev.kultivator.sect.build.api.SectTemplates;
import dev.kultivator.sect.event.Duties;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.sect.hall.SectRanks;
import dev.kultivator.sekte.SekteIds;
import dev.kultivator.sekte.mandiri.jalan.Jelmaan;
import dev.kultivator.sosok.GerakId;
import dev.kultivator.sosok.PropId;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Ekspedisi Pengumpulan (DESIGN 21.14 item 3): gathering trips for one material category with 1-3 disciples, always
 * available and pure data. The party leaves in the cloud-step look of module B, is taken out of the world with
 * {@code Disciples.depart} and kept as a tag in the flow's own record (so {@code uji sekte misi selesai} can bring it home
 * at once), and comes back after a short (6,000 ticks) or long (24,000 ticks, x4.5 yield) trip beside the stele, or beside
 * the master while the hall sleeps.
 * <ul>
 * <li>Yield {@code base x (1 + 0.4 P)} with P from 20.3.10 ({@code max m + 0.5 x sum of the others}), x1.5 under an
 * Ekspedisi Berkah offer.</li>
 * <li>Success {@code clamp(0.75 + 0.05 (P - difficulty), 0.30, 0.98)}; a failed party brings 30% and returns at half its
 * health. Never death, never a Qi Deviation.</li>
 * <li>Each disciple earns PK 3 (short) or 10 (long) x (1 + difficulty / 4) and a bar share of 3% or 8%.</li>
 * <li>At most {@code 1 + floor((S - 1) / 3)} parties at once; a disciple on a trip holds no duty, builder post or expedition.</li>
 * </ul>
 * Source: <i>Battle Through the Heavens</i>, trips into the Magical Beast Mountain Range; <i>Soul Land</i>, escorted trips
 * into the Star Dou Great Forest and the Hunting Spirit Forest. Numbers are game design.
 */
public final class GatherExpeditions {

	public static final String REASON = "build_gather";
	public static final long SHORT_TICKS = 6000L;
	public static final long LONG_TICKS = 24000L;
	public static final double LONG_FACTOR = 4.5D;
	public static final double POWER_YIELD = 0.4D;
	public static final double FAIL_SHARE = 0.3D;
	public static final float FAIL_HEALTH = 0.5F;
	public static final int MAX_PARTY = 3;
	/** Base yield in units of a short trip, by category (DESIGN 21.14 table). */
	public static final int[] BASE = {48, 48, 32, 24, 16, 12, 16, 8, 3};
	/** Difficulty by category. */
	public static final int[] DIFFICULTY = {0, 0, 0, 1, 1, 1, 0, 2, 4};
	private static final int DEPART_COLOR = 0xBFE8FF;

	// ================================================================== who may go

	/** The lowest sect rank a member needs for {@code cat} (Tercatat 0, Luar 1, Dalam 2). */
	public static int minRank(Bahan cat) {
		return switch (cat) {
			case GENTENG, KAPUR, PERNIS, LOGAM -> SectRanks.LUAR;
			case PERMATA -> SectRanks.DALAM;
			default -> SectRanks.TERCATAT;
		};
	}

	/** Why this disciple cannot go gathering {@code cat}, or null when it can. */
	public static @Nullable Component blocker(DiscipleEntity disciple, Bahan cat) {
		DiscipleData data = disciple.data();
		if (!disciple.isAlive() || disciple.isRemoved() || disciple.isReleasing() || disciple.role() != DiscipleEntity.Role.DISCIPLE
				|| data.breakingThrough() || DuelRules.inDuel(disciple)) {
			return Component.translatable("kultivator.sectbuild.gather.busy", disciple.getDisplayName());
		}
		int activity = disciple.activity();
		UUID owner = disciple.ownerId();
		boolean builder = owner != null && Builders.isBuilder(owner, disciple.soulId())
				&& (activity == DiscipleEntity.ACTIVITY_BUILD || activity == DiscipleEntity.ACTIVITY_SPECTATOR);
		if (activity != DiscipleEntity.ACTIVITY_NONE && !builder || Duties.dutyOf(data) != null) {
			return Component.translatable("kultivator.sectbuild.gather.on_duty", disciple.getDisplayName());
		}
		if (SectRanks.rank(data) < minRank(cat)) {
			return Component.translatable("kultivator.sectbuild.gather.rank", disciple.getDisplayName(), SectRanks.rankName(minRank(cat)));
		}
		if (cat == Bahan.LOGAM && !data.realm().atLeast(Realm.QI_REFINING)) {
			return Component.translatable("kultivator.sectbuild.gather.realm", disciple.getDisplayName());
		}
		return null;
	}

	/** Member power m = realm ordinal + stage/4 + layer/9 + stars/9 (DESIGN 20.3.10). */
	static double memberPower(DiscipleData data) {
		Rank rank = data.rank();
		return rank.realm().ordinal() + rank.stage() / 4.0D + rank.layer() / 9.0D + DisciplePaths.stars(data) / 9.0D;
	}

	/** P = max m + 0.5 x the sum of the others. */
	static double partyPower(List<DiscipleData> team) {
		double best = 0.0D;
		double sum = 0.0D;
		for (DiscipleData data : team) {
			double m = memberPower(data);
			best = Math.max(best, m);
			sum += m;
		}
		return best + 0.5D * (sum - best);
	}

	public static double chance(double power, Bahan cat) {
		return Math.max(0.30D, Math.min(0.98D, 0.75D + 0.05D * (power - DIFFICULTY[cat.ordinal()])));
	}

	/** Expected yield in centi-units. */
	public static long yieldCenti(UUID owner, double power, Bahan cat, boolean longTrip) {
		double units = BASE[cat.ordinal()] * (1.0D + POWER_YIELD * power) * (longTrip ? LONG_FACTOR : 1.0D) * BuildMissions.blessing(owner, cat);
		return Math.round(units * Bahan.CENTI);
	}

	/** Parties allowed at once: {@code 1 + floor((S - 1) / 3)}. */
	public static int maxParties(UUID owner) {
		int template = SectTemplates.rank(EngineApi.get().template(owner));
		return 1 + Math.max(0, template - 1) / 3;
	}

	private static ListTag parties(UUID owner) {
		return FlowData.list(FlowData.of(owner), "parties");
	}

	/** Parties still away or on their way back. */
	public static int active(UUID owner) {
		return parties(owner).size();
	}

	// ================================================================== departure

	/** Sends {@code members} gathering {@code cat}. False (with a chat reason) when they cannot go. */
	public static boolean send(ServerPlayer player, UUID owner, Bahan cat, boolean longTrip, List<DiscipleEntity> members) {
		Halls.HallView hall = Halls.hallOf(owner);
		if (hall == null) {
			Msg.chat(player, Component.translatable("kultivator.sectbuild.no_hall").withStyle(ChatFormatting.YELLOW));
			return false;
		}
		if (members.isEmpty() || members.size() > MAX_PARTY) {
			Msg.chat(player, Component.translatable("kultivator.sectbuild.gather.party_size", MAX_PARTY).withStyle(ChatFormatting.YELLOW));
			return false;
		}
		if (active(owner) >= maxParties(owner)) {
			Msg.chat(player, Component.translatable("kultivator.sectbuild.gather.too_many", maxParties(owner)).withStyle(ChatFormatting.YELLOW));
			return false;
		}
		List<DiscipleData> team = new ArrayList<>();
		for (DiscipleEntity member : members) {
			Component why = blocker(member, cat);
			if (why != null) {
				Msg.chat(player, why.copy().withStyle(ChatFormatting.YELLOW));
				return false;
			}
			team.add(member.data());
		}
		double power = partyPower(team);
		long now = FlowData.gameTime();
		long duration = longTrip ? LONG_TICKS : SHORT_TICKS;
		CompoundTag party = new CompoundTag();
		CompoundTag section = FlowData.of(owner);
		int id = section.getIntOr("party_seq", 0) + 1;
		section.putInt("party_seq", id);
		party.putInt("id", id);
		party.putString("cat", cat.id());
		party.putBoolean("long", longTrip);
		party.putLong("start", now);
		party.putLong("return_at", now + duration);
		party.putDouble("p", power);
		party.putDouble("chance", chance(power, cat));
		party.putLong("yield", yieldCenti(owner, power, cat, longTrip));
		ListTag stored = new ListTag();
		ListTag names = new ListTag();
		MinecraftServer server = player.level().getServer();
		for (DiscipleEntity member : members) {
			Builders.release(owner, member.soulId(), false);
			if (member.level() instanceof ServerLevel level) {
				Vec3 at = member.position();
				Fx.burst(level, at.add(0.0D, 1.0D, 0.0D), DEPART_COLOR, 24, 1.0F);
				Fx.ring(level, at.add(0.0D, 0.1D, 0.0D), DEPART_COLOR, 1.8F);
				level.playSound(null, at.x, at.y, at.z, SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.NEUTRAL, 0.8F, 0.9F);
			}
			CompoundTag nameTag = new CompoundTag();
			nameTag.store("name", ComponentSerialization.CODEC, member.getDisplayName());
			names.add(nameTag);
			UUID soul = member.soulId();
			CompoundTag tag = Disciples.depart(member, REASON);
			if (!tag.isEmpty()) {
				stored.add(tag);
				Disciples.setAwayUntil(server, owner, soul, now + duration);
			}
		}
		if (stored.isEmpty()) {
			return false;
		}
		party.put("members", stored);
		party.put("names", names);
		parties(owner).add(party);
		FlowData.dirty();
		Msg.chat(player, Component.translatable("kultivator.sectbuild.gather.sent", stored.size(), cat.displayName(), Fmt.clock(duration),
				Fmt.percent(chance(power, cat), 1.0D), Fmt.units(yieldCenti(owner, power, cat, longTrip))).withStyle(ChatFormatting.AQUA));
		return true;
	}

	// ================================================================== return

	/** Once a second per owner with parties: resolves due parties and brings their members home. */
	static void tick(MinecraftServer server, UUID owner) {
		ListTag list = parties(owner);
		if (list.isEmpty()) {
			return;
		}
		long now = server.overworld().getGameTime();
		ServerPlayer master = server.getPlayerList().getPlayer(owner);
		tampilkan(server, owner, list, now);
		for (int i = list.size() - 1; i >= 0; i--) {
			if (!(list.get(i) instanceof CompoundTag party) || party.getLongOr("return_at", 0L) > now) {
				continue;
			}
			if (!party.getBooleanOr("resolved", false)) {
				resolve(owner, party, master, server.overworld().getRandom());
			}
			if (master != null && FlowData.flag(owner, "gather_award_due")) {
				FlowData.setFlag(owner, "gather_award_due", false);
				Advancements.grant(master, "first_gathering");
			}
			ListTag members = party.getListOrEmpty("members");
			for (int m = members.size() - 1; m >= 0; m--) {
				if (members.get(m) instanceof CompoundTag stored && arrive(server, owner, stored, master)) {
					members.remove(m);
					FlowData.dirty();
				}
			}
			if (members.isEmpty()) {
				list.remove(i);
				FlowData.dirty();
			}
		}
	}

	/**
	 * M2-ANIM: a party on a trip is away (pure data), so while a player is within 128 blocks of its gathering site (a fixed spot
	 * 140 to 159 blocks from the stele, one per party) each member is shown by a {@code Jelmaan} stage body working the category
	 * with its tool, around the site and facing it. The bodies go when the party is due or the real member is back.
	 */
	private static void tampilkan(MinecraftServer server, UUID owner, ListTag list, long now) {
		Halls.HallView hall = Halls.hallOf(owner);
		ServerLevel level = hall == null || hall.dim() == null ? null : server.getLevel(hall.dim());
		if (level == null || !Jelmaan.dekatPemain(level, hall.pos(), 300.0D)) {
			return;
		}
		for (Tag tag : list) {
			if (!(tag instanceof CompoundTag party) || party.getBooleanOr("resolved", false) || party.getLongOr("return_at", 0L) <= now) {
				continue;
			}
			int id = party.getIntOr("id", 0);
			BlockPos site = Jelmaan.titikLuar(level, hall.pos(), owner.getLeastSignificantBits() * 31L + id, 140);
			if (site == null) {
				continue;
			}
			Jelmaan.Adegan work = adegan(Bahan.byId(party.getStringOr("cat", "")));
			ListTag members = party.getListOrEmpty("members");
			for (int m = 0; m < members.size(); m++) {
				UUID soul = members.get(m) instanceof CompoundTag stored ? stored.read("soul", UUIDUtil.CODEC).orElse(null) : null;
				if (soul != null) {
					Jelmaan.tunjukkan(level, "gather:" + owner + ":" + id + ":" + soul, owner, soul, members.getCompoundOrEmpty(m),
							Jelmaan.sekitar(level, site, m), site, work, 100);
				}
			}
		}
	}

	/** What the party does at its site: the herb picker's sickle, the miner's pick (the mine duty pose), the spade, the saw, the sap blade. */
	private static Jelmaan.Adegan adegan(@Nullable Bahan cat) {
		if (cat == null) {
			return Jelmaan.Adegan.diam(GerakId.BERDIRI_JAGA, 0, PropId.TOMBAK);
		}
		return switch (cat) {
			case FLORA -> Jelmaan.Adegan.diam(GerakId.PETIK_HERBA_TEBING, 0, PropId.SABIT_HERBA);
			case BATU, KAPUR, LOGAM, PERMATA -> Jelmaan.Adegan.kerja(SekteIds.ACT_MINE, GerakId.MENCANGKUL, 0, PropId.CANGKUL);
			case TANAH -> Jelmaan.Adegan.diam(GerakId.MENCANGKUL, 0, PropId.CANGKUL);
			case KAYU -> Jelmaan.Adegan.diam(KotaGerakId.GERGAJI_KAYU, 0, 0);
			case GENTENG -> Jelmaan.Adegan.diam(GerakId.ANGKAT_PAPAN, 0, 0);
			case PERNIS -> Jelmaan.Adegan.diam(GerakId.MEMANEN, GerakId.V_MEMANEN_SABIT, PropId.SABIT_HERBA);
		};
	}

	/** The success roll, the materials and every member's reward (changed in the stored tags). */
	private static void resolve(UUID owner, CompoundTag party, @Nullable ServerPlayer master, RandomSource random) {
		Bahan cat = Bahan.byId(party.getStringOr("cat", ""));
		if (cat == null) {
			cat = Bahan.BATU;
		}
		boolean longTrip = party.getBooleanOr("long", false);
		boolean success = random.nextDouble() < party.getDoubleOr("chance", 0.75D);
		long yield = party.getLongOr("yield", 0L);
		long got = success ? yield : Math.round(yield * FAIL_SHARE);
		Ledger.INSTANCE.add(owner, cat, got);
		int difficulty = DIFFICULTY[cat.ordinal()];
		int pk = (int) Math.round((longTrip ? 10 : 3) * (1.0D + difficulty / 4.0D));
		double share = longTrip ? 0.08D : 0.03D;
		for (Tag tag : party.getListOrEmpty("members")) {
			if (!(tag instanceof CompoundTag stored)) {
				continue;
			}
			DiscipleData data = DiscipleData.load(stored.getCompoundOrEmpty("disciple"));
			SectRanks.addPk(data, success ? pk : Math.max(1, pk / 2), "kultivator.sectbuild.pk.gather");
			DisciplePaths.grantBarShare(data, success ? share : share * 0.5D);
			stored.put("disciple", data.save());
			if (!success) {
				stored.putFloat("health", Math.max(1.0F, stored.getFloatOr("health", 20.0F) * FAIL_HEALTH));
			}
		}
		party.putBoolean("resolved", true);
		party.putBoolean("success", success);
		party.putLong("got", got);
		FlowData.dirty();
		FlowData.setFlag(owner, "gather_award_due", true);
		Halls.log(owner, Component.translatable(success ? "kultivator.sectbuild.log.gather" : "kultivator.sectbuild.log.gather_failed",
				Fmt.units(got), cat.displayName()));
		if (master != null) {
			Msg.chat(master, Component.translatable(success ? "kultivator.sectbuild.gather.back" : "kultivator.sectbuild.gather.back_failed",
					Fmt.units(got), cat.displayName(), success ? pk : Math.max(1, pk / 2)).withStyle(success ? ChatFormatting.GOLD : ChatFormatting.RED));
		}
	}

	/** A member arrives beside the active stele, else beside the online master; false while it cannot come yet. */
	private static boolean arrive(MinecraftServer server, UUID owner, CompoundTag stored, @Nullable ServerPlayer master) {
		Halls.HallView hall = Halls.hallOf(owner);
		ServerLevel level = null;
		Vec3 at = null;
		if (hall != null && hall.active()) {
			ServerLevel hallLevel = server.getLevel(hall.dim());
			if (hallLevel != null && hallLevel.isLoaded(hall.pos())) {
				level = hallLevel;
				BlockPos arrival = EngineApi.get().anchor(owner, "arrival");
				at = arrival != null ? Vec3.atBottomCenterOf(arrival) : Vec3.atBottomCenterOf(hall.pos()).add(2.0D, 0.0D, 0.0D);
			}
		}
		if (level == null && master != null && !master.isSpectator()) {
			level = master.level();
			at = master.position();
		}
		if (level == null) {
			return false;
		}
		DiscipleEntity back = Disciples.arrive(level, at, stored, owner);
		if (back == null) {
			return false;
		}
		Vec3 pos = back.position();
		Fx.burst(level, pos.add(0.0D, 1.0D, 0.0D), DEPART_COLOR, 24, 1.0F);
		Fx.ring(level, pos.add(0.0D, 0.1D, 0.0D), DEPART_COLOR, 1.8F);
		level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.NEUTRAL, 0.7F, 1.2F);
		if (master != null) {
			Msg.chat(master, Component.translatable("kultivator.hall.away.back", back.getDisplayName()).withStyle(ChatFormatting.AQUA));
		}
		return true;
	}

	/** {@code uji sekte misi selesai}: every party is due now; the number of parties. */
	public static int finishAll(MinecraftServer server, UUID owner) {
		ListTag list = parties(owner);
		long now = server.overworld().getGameTime();
		for (Tag tag : list) {
			if (tag instanceof CompoundTag party) {
				party.putLong("return_at", Math.min(party.getLongOr("return_at", now), now));
			}
		}
		FlowData.dirty();
		int count = list.size();
		tick(server, owner);
		return count;
	}

	// ================================================================== for the Misi panel

	static ListTag info(UUID owner) {
		ListTag out = new ListTag();
		long now = FlowData.gameTime();
		for (Tag tag : parties(owner)) {
			if (!(tag instanceof CompoundTag party)) {
				continue;
			}
			CompoundTag entry = new CompoundTag();
			entry.putInt("id", party.getIntOr("id", 0));
			entry.putString("cat", party.getStringOr("cat", "batu"));
			entry.putBoolean("long", party.getBooleanOr("long", false));
			entry.putLong("left", Math.max(0L, party.getLongOr("return_at", now) - now));
			entry.putDouble("chance", party.getDoubleOr("chance", 0.0D));
			entry.putLong("yield", party.getLongOr("yield", 0L));
			entry.putBoolean("resolved", party.getBooleanOr("resolved", false));
			entry.putBoolean("success", party.getBooleanOr("success", false));
			entry.put("names", party.getListOrEmpty("names").copy());
			out.add(entry);
		}
		return out;
	}

	private GatherExpeditions() {
	}
}
