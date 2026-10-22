package dev.kultivator.sekte.tetua;

import dev.kultivator.alchemy.PillItem;
import dev.kultivator.alchemy.PillKind;
import dev.kultivator.disciple.DiscipleCultivation;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.fx.Fx;
import dev.kultivator.sect.event.Storehouse;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.sekte.Ingatan;
import dev.kultivator.sekte.SekteIds;
import dev.kultivator.sekte.SekteLog;
import dev.kultivator.sekte.api.Ikatan;
import dev.kultivator.sekte.api.Jabatan;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.api.SekteBus;
import dev.kultivator.voice.VoiceDisciples;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Urusan harian, the elders' daily affairs (DESIGN 36.2.6, budget 36.3.9). From {@code jamMulai} to {@code jamSelesai}
 * (06:00-18:00 world time) every office holder that is free sits at the desk of its office ({@code SekteIds.ACT_OFFICE},
 * brush and scroll in hand) by its domain's anchor; outside those hours, or when called away, it is its own self again.
 * One affair per hall every {@code urusanDetik} (120 s), taken in turn by the seated elders:
 * <ul>
 * <li><b>Tetua Agung</b>: mediates one rivalry or grudge between two disciples present (R's {@code adjust}).</li>
 * <li><b>Balai Hukum</b>: counsels disciples whose contentment fell under 30, once each per day (P hears it as an honour
 * of 3 × the office's bonus).</li>
 * <li><b>Paviliun Kitab</b>: guides a disciple reading in the pavilion (A applies the bonus to study itself).</li>
 * <li><b>Perbendaharaan</b>: tidies the Lumbung (merges split stacks; nothing is ever lost or added).</li>
 * <li><b>Urusan Luar</b>: receives an envoy of an NPC sect waiting at the gate (D's standing +2 × bonus).</li>
 * <li><b>Obat</b>: gives a healing pill from the Lumbung to a wounded disciple, through the normal pill rules.</li>
 * </ul>
 * An affair that finds nothing to do leaves only a quiet line in the morning report, never in the hall log.
 * <p>
 * Sumber: the daily offices of the <i>Baizhang Qinggui</i> (the abbot's assistants, the guest master who receives
 * visitors, the library master, the infirmary).
 */
final class UrusanHarian {

	private static final double DESK_REACH = 2.25D;
	private static final double CALL_RANGE = 64.0D;
	/** Disciples with contentment below this get the Hall of Law's counsel. */
	static final int LAW_THRESHOLD = 30;
	static final int LAW_HONOUR = 3;
	static final int LAW_PER_AFFAIR = 3;
	static final int ENVOY_STANDING = 2;
	static final int MEDIATE_AFFINITY = 4;
	/** A disciple below this share of its health is "wounded" for the Medicine elder. */
	static final double WOUNDED = 0.6D;

	/** Next affair per hall (game time) and when each office was last seen seated. */
	private static final Map<UUID, Long> NEXT = new HashMap<>();
	private static final Map<UUID, Map<Jabatan, Long>> SEATED = new HashMap<>();

	// ================================================================== the desk

	/** Once a second for an office holder: sit down during office hours, leave the desk after them, run the affair. */
	static void duty(MinecraftServer server, UUID owner, DiscipleEntity entity, Jabatan office) {
		if (Tetua.busy(entity)) {
			return;
		}
		TetuaConfig config = Tetua.config();
		boolean hours = TetuaData.within(TetuaData.hour(server), config.jamMulai, config.jamSelesai);
		BlockPos desk = hours && Tetua.free(entity, false) ? JabatanTetua.desk(owner, office, entity.level()) : null;
		if (desk == null || entity.distanceToSqr(Vec3.atBottomCenterOf(desk)) > CALL_RANGE * CALL_RANGE) {
			if (entity.activity() == SekteIds.ACT_OFFICE) {
				entity.setActivity(DiscipleEntity.ACTIVITY_NONE, null);
			}
			return;
		}
		if (entity.activity() != SekteIds.ACT_OFFICE || !desk.equals(entity.activitySpot())) {
			entity.setActivity(SekteIds.ACT_OFFICE, desk);
			return;
		}
		boolean seated = entity.getNavigation().isDone() && entity.distanceToSqr(Vec3.atBottomCenterOf(desk)) <= DESK_REACH * DESK_REACH;
		if (!seated) {
			return;
		}
		long now = entity.level().getGameTime();
		SEATED.computeIfAbsent(owner, k -> new EnumMap<>(Jabatan.class)).put(office, now);
		Long next = NEXT.get(owner);
		long interval = config.urusanDetik * 20L;
		if (next == null) {
			NEXT.put(owner, now + interval / 2L);
			return;
		}
		if (now < next) {
			return;
		}
		CompoundTag mine = TetuaData.hall(owner);
		Jabatan last = Jabatan.byId(mine.getStringOr(TetuaData.LAST_OFFICE, ""));
		if (office == last && othersSeated(owner, office, now)) {
			// fairness: another seated elder takes the next affair
			return;
		}
		NEXT.put(owner, now + interval);
		mine.putString(TetuaData.LAST_OFFICE, office.id());
		CompoundTag entry = TetuaData.child(TetuaData.child(mine, TetuaData.OFFICES), office.id());
		entry.putInt("urusan", entry.getIntOr("urusan", 0) + 1);
		TetuaData.dirty();
		run(server, owner, entity, office);
	}

	private static boolean othersSeated(UUID owner, Jabatan office, long now) {
		Map<Jabatan, Long> seen = SEATED.get(owner);
		if (seen == null) {
			return false;
		}
		for (Map.Entry<Jabatan, Long> e : seen.entrySet()) {
			if (e.getKey() != office && now - e.getValue() <= 40L) {
				return true;
			}
		}
		return false;
	}

	/** Runs the office's affair now ({@code uji perguruan tetua urusan} calls it directly). */
	static void run(MinecraftServer server, UUID owner, DiscipleEntity elder, Jabatan office) {
		double factor = JabatanTetua.factor(owner, office);
		boolean did = switch (office) {
			case TETUA_AGUNG -> mediate(server, owner, elder, factor);
			case TETUA_HUKUM -> counsel(server, owner, elder, factor);
			case TETUA_AJAR -> guide(server, owner, elder);
			case TETUA_PUSAKA -> tidy(server, owner, elder);
			case TETUA_LUAR -> receive(server, owner, elder, factor);
			case TETUA_OBAT -> heal(server, owner, elder);
		};
		if (!did) {
			LaporanPagi.note(server, owner, SekteIds.E, Component.translatable("kultivator.sekte.tetua.quiet." + office.id(), elder.getName()));
		}
		if (elder.level() instanceof ServerLevel level) {
			Vec3 pos = elder.position();
			int rgb = JabatanTetua.colour(office);
			Fx.burst(level, pos.add(0.0D, 1.1D, 0.0D), rgb, did ? 14 : 6, 0.6F);
			level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BOOK_PAGE_TURN, SoundSource.NEUTRAL, 0.6F, 0.9F + level.getRandom().nextFloat() * 0.2F);
		}
	}

	// ================================================================== the six affairs

	/** Tetua Agung: settle one rivalry or grudge between two disciples present. */
	private static boolean mediate(MinecraftServer server, UUID owner, DiscipleEntity elder, double factor) {
		List<DiscipleEntity> present = present(owner, elder);
		for (DiscipleEntity a : present) {
			for (Ikatan kind : new Ikatan[]{Ikatan.DENDAM, Ikatan.SAINGAN}) {
				for (UUID other : SekteApi.hubungan().with(owner, a.soulId(), kind)) {
					DiscipleEntity b = find(present, other);
					if (b == null) {
						continue;
					}
					int delta = (int) Math.round(MEDIATE_AFFINITY * factor);
					SekteApi.hubungan().adjust(owner, a.soulId(), b.soulId(), delta, "tetua.damai");
					beam(elder, a, JabatanTetua.colour(Jabatan.TETUA_AGUNG));
					beam(elder, b, JabatanTetua.colour(Jabatan.TETUA_AGUNG));
					SekteLog.log(server, owner, SekteIds.E, Component.translatable("kultivator.sekte.tetua.urusan.agung", elder.getName(), a.getName(),
							b.getName(), Component.translatable(kind.key())));
					return true;
				}
			}
		}
		return false;
	}

	/** Balai Hukum: counsel up to three unhappy disciples (once each per day). */
	private static boolean counsel(MinecraftServer server, UUID owner, DiscipleEntity elder, double factor) {
		CompoundTag law = TetuaData.child(TetuaData.hall(owner), TetuaData.LAW);
		long today = Ingatan.today(server);
		int done = 0;
		List<Component> names = new ArrayList<>();
		for (DiscipleEntity d : present(owner, elder)) {
			if (done >= LAW_PER_AFFAIR) {
				break;
			}
			DiscipleData data = d.data();
			String key = d.soulId().toString();
			if (SekteApi.hati().contentment(data) >= LAW_THRESHOLD || law.getLongOr(key, -1L) == today) {
				continue;
			}
			law.putLong(key, today);
			SekteBus.honored(server, owner, d.soulId(), "tetua.hukum", (int) Math.round(LAW_HONOUR * factor));
			Ingatan.remember(server, data, "tetua.nasihat", elder.soulId(), 2, "kultivator.sekte.tetua.memory.counsel", elder.getName().getString());
			beam(elder, d, JabatanTetua.colour(Jabatan.TETUA_HUKUM));
			names.add(d.getName());
			done++;
		}
		// forget yesterday's marks
		for (String key : List.copyOf(law.keySet())) {
			if (law.getLongOr(key, today) < today - 1L) {
				law.remove(key);
			}
		}
		TetuaData.dirty();
		if (done == 0) {
			return false;
		}
		SekteLog.log(server, owner, SekteIds.E, Component.translatable("kultivator.sekte.tetua.urusan.hukum", elder.getName(), list(names)));
		VoiceDisciples.line(elder, "careful", 1200);
		return true;
	}

	/** Paviliun Kitab: guide one reader (A owns the study and its speed). */
	private static boolean guide(MinecraftServer server, UUID owner, DiscipleEntity elder) {
		for (DiscipleEntity d : present(owner, elder)) {
			String technique = SekteApi.paviliun().studying(owner, d.soulId());
			if (technique == null || technique.isEmpty()) {
				continue;
			}
			SekteBus.honored(server, owner, d.soulId(), "tetua.ajar", 2);
			Ingatan.remember(server, d.data(), "tetua.bimbing", elder.soulId(), 2, "kultivator.sekte.tetua.memory.guided", elder.getName().getString());
			beam(elder, d, JabatanTetua.colour(Jabatan.TETUA_AJAR));
			SekteLog.log(server, owner, SekteIds.E, Component.translatable("kultivator.sekte.tetua.urusan.ajar", elder.getName(), d.getName()));
			VoiceDisciples.line(elder, "cultivate", 1200);
			return true;
		}
		return false;
	}

	/** Perbendaharaan: merge the Lumbung's split stacks (take everything out, put it back: inserts merge first). */
	private static boolean tidy(MinecraftServer server, UUID owner, DiscipleEntity elder) {
		List<ItemStack> all = Storehouse.take(owner, stack -> true, Integer.MAX_VALUE);
		if (all.isEmpty()) {
			return false;
		}
		// how many stacks the same goods need once merged
		List<ItemStack> kinds = new ArrayList<>();
		List<Integer> totals = new ArrayList<>();
		for (ItemStack stack : all) {
			int at = -1;
			for (int i = 0; i < kinds.size(); i++) {
				if (ItemStack.isSameItemSameComponents(kinds.get(i), stack)) {
					at = i;
					break;
				}
			}
			if (at < 0) {
				kinds.add(stack);
				totals.add(stack.getCount());
			} else {
				totals.set(at, totals.get(at) + stack.getCount());
			}
		}
		int needed = 0;
		for (int i = 0; i < kinds.size(); i++) {
			int max = Math.max(1, kinds.get(i).getMaxStackSize());
			needed += (totals.get(i) + max - 1) / max;
		}
		for (ItemStack stack : all) {
			ItemStack rest = Storehouse.insert(owner, stack);
			if (!rest.isEmpty()) {
				// cannot happen (fewer stacks than before); never lose goods: hand them to the master or drop at the elder
				giveBack(server, owner, elder, rest);
			}
		}
		int merged = all.size() - needed;
		if (merged <= 0) {
			return false;
		}
		SekteLog.log(server, owner, SekteIds.E, Component.translatable("kultivator.sekte.tetua.urusan.pusaka", elder.getName(), merged));
		return true;
	}

	private static void giveBack(MinecraftServer server, UUID owner, DiscipleEntity elder, ItemStack rest) {
		ServerPlayer master = server.getPlayerList().getPlayer(owner);
		if (master != null && master.getInventory().add(rest) && rest.isEmpty()) {
			return;
		}
		if (!rest.isEmpty()) {
			elder.spawnAtLocation((ServerLevel) elder.level(), rest);
		}
	}

	/** Urusan Luar: receive one envoy waiting at the gate. */
	private static boolean receive(MinecraftServer server, UUID owner, DiscipleEntity elder, double factor) {
		CompoundTag mine = TetuaData.hall(owner);
		ListTag envoys = mine.getListOrEmpty(TetuaData.ENVOYS);
		if (envoys.isEmpty()) {
			return false;
		}
		Tag first = envoys.get(0);
		ListTag rest = envoys.copy();
		rest.remove(0);
		mine.put(TetuaData.ENVOYS, rest);
		TetuaData.dirty();
		String sect = first.asString().orElse("");
		if (sect.isEmpty()) {
			return false;
		}
		Component name = SekteApi.diplomasi().name(sect);
		SekteApi.diplomasi().adjust(owner, sect, (int) Math.round(ENVOY_STANDING * factor), "tetua.sambut");
		SekteLog.log(server, owner, SekteIds.E, Component.translatable("kultivator.sekte.tetua.urusan.luar", elder.getName(),
				name == null ? Component.literal(sect) : name));
		VoiceDisciples.line(elder, "greet_alt", 600);
		return true;
	}

	/** {@code SekteBus.RIVAL_SECT_ACTION(UTUSAN)}: the envoy waits at the gate for the Outer Affairs elder. */
	static void envoyArrived(UUID owner, String sect) {
		if (owner == null || sect == null || Halls.hallOf(owner) == null) {
			return;
		}
		ListTag envoys = TetuaData.list(TetuaData.hall(owner), TetuaData.ENVOYS);
		envoys.add(net.minecraft.nbt.StringTag.valueOf(sect));
		while (envoys.size() > 6) {
			envoys.remove(0);
		}
		TetuaData.dirty();
	}

	/** Obat: one healing pill from the Lumbung for the most wounded disciple present (the normal pill rules apply). */
	private static boolean heal(MinecraftServer server, UUID owner, DiscipleEntity elder) {
		ServerPlayer master = server.getPlayerList().getPlayer(owner);
		if (master == null) {
			return false;
		}
		DiscipleEntity worst = null;
		float share = (float) WOUNDED;
		for (DiscipleEntity d : present(owner, elder)) {
			float s = d.getHealth() / Math.max(1.0F, d.getMaxHealth());
			if (s < share && d.getTarget() == null) {
				share = s;
				worst = d;
			}
		}
		if (worst == null) {
			return false;
		}
		List<ItemStack> taken = Storehouse.take(owner, UrusanHarian::healing, 1);
		if (taken.isEmpty()) {
			return false;
		}
		ItemStack pill = taken.get(0);
		if (DiscipleCultivation.feedBlocker(worst, pill) != null || !DiscipleCultivation.feed(worst, master, pill)) {
			Storehouse.insert(owner, pill.copyWithCount(1));
			return false;
		}
		if (!pill.isEmpty()) {
			// creative masters keep their items; the Lumbung keeps its pill too
			Storehouse.insert(owner, pill);
		}
		beam(elder, worst, JabatanTetua.colour(Jabatan.TETUA_OBAT));
		SekteLog.log(server, owner, SekteIds.E, Component.translatable("kultivator.sekte.tetua.urusan.obat", elder.getName(), worst.getName()));
		return true;
	}

	private static boolean healing(ItemStack stack) {
		return stack.getItem() instanceof PillItem pill && pill.kind() == PillKind.HEALING;
	}

	// ================================================================== helpers

	/** The master's disciples in the territory near the elder (not itself, not fighting). */
	private static List<DiscipleEntity> present(UUID owner, DiscipleEntity elder) {
		List<DiscipleEntity> list = new ArrayList<>();
		for (DiscipleEntity d : Disciples.loaded(owner)) {
			if (d != elder && d.role() == DiscipleEntity.Role.DISCIPLE && d.level() == elder.level() && !d.isReleasing()
					&& d.distanceToSqr(elder) <= 48.0D * 48.0D && Halls.inTerritory(owner, d.level(), d.position())) {
				list.add(d);
			}
		}
		return list;
	}

	private static @Nullable DiscipleEntity find(List<DiscipleEntity> list, UUID soul) {
		for (DiscipleEntity d : list) {
			if (d.soulId().equals(soul)) {
				return d;
			}
		}
		return null;
	}

	private static void beam(DiscipleEntity from, DiscipleEntity to, int rgb) {
		if (from.level() instanceof ServerLevel level && from.distanceToSqr(to) <= 32.0D * 32.0D) {
			Fx.beam(level, from.position().add(0.0D, 1.2D, 0.0D), to.position().add(0.0D, 1.2D, 0.0D), rgb, 0.06F);
			Fx.burst(level, to.position().add(0.0D, 1.0D, 0.0D), rgb, 8, 0.6F);
		}
	}

	private static Component list(List<Component> names) {
		net.minecraft.network.chat.MutableComponent out = Component.empty();
		for (int i = 0; i < names.size(); i++) {
			if (i > 0) {
				out.append(Component.literal(", "));
			}
			out.append(names.get(i));
		}
		return out;
	}

	static void clear() {
		NEXT.clear();
		SEATED.clear();
	}

	/** {@code uji perguruan tetua urusan}: the next affair of this hall now. */
	static void hurry(UUID owner) {
		NEXT.put(owner, 0L);
		TetuaData.hall(owner).remove(TetuaData.LAST_OFFICE);
	}

	private UrusanHarian() {
	}
}
