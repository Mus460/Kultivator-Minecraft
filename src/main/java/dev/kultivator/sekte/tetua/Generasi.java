package dev.kultivator.sekte.tetua;

import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Realm;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.disciple.path.DisciplePaths;
import dev.kultivator.sect.hall.HallData;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.sekte.Ingatan;
import dev.kultivator.sekte.SekteIds;
import dev.kultivator.sekte.SekteLog;
import dev.kultivator.sekte.api.Jabatan;
import dev.kultivator.sekte.api.Perlakuan;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.api.SekteBus;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Pewaris and the second generation (DESIGN 36.2.6, butir 50). The heir itself stays the existing
 * {@code disciple.path.Heir} (its ritual, its Nine Hexes, its numbers); {@code Heir} fires {@code SekteBus.HEIR_NAMED}
 * once its ritual completes and E remembers the heir in its hall section (older worlds: the first heir tag seen on a
 * disciple). The Succession Ceremony ({@link PenyerahanTakhta}) then makes the heir <b>Ketua Generasi Kedua</b>: a gold
 * name, every filled office +0.05, the hall led in the master's absence; it stays the player's disciple. The player
 * becomes the <b>Leluhur Pendiri</b>: a title, a tenth more renown on every renown the hall earns (watched every 30 s
 * and paid through {@code Halls.addRenown}), and +10 standing with every NPC sect the hall meets. A later heir can take
 * the throne again (generation three and on); the lineage keeps every generation.
 * <p>
 * Sumber: the Fifth Patriarch Hongren handing his robe and bowl to Huineng, the Sixth (<i>Platform Sutra</i>); the heir
 * of the Demon Sealers passed down one per generation (<i>I Shall Seal the Heavens</i>).
 */
final class Generasi {

	/** Unpaid founder renown is paid once it reaches this much (fewer log lines). */
	private static final double RENOWN_BATCH = 5.0D;
	static final double FOUNDER_RENOWN = 0.10D;
	static final int FOUNDER_STANDING = 10;
	/** False: module D's SUCCESSION listener owns the founder's +10 with every NPC sect (finish pass, no double bonus). */
	static final boolean GREET_SELF = false;
	static final int GOLD = 0xFFD54A;
	/** Requirements of the ceremony (DESIGN 36.2.6). */
	static final Realm HEIR_REALM = Realm.NASCENT_SOUL;
	static final int HEIR_STARS = 9;
	static final int HALL_LEVEL = 5;

	// ================================================================== reads

	static @Nullable UUID heir(UUID owner) {
		return TetuaData.uuid(TetuaData.hall(owner).getStringOr(TetuaData.HEIR, ""));
	}

	/** 0 while the founder still rules; 2 for the first Ketua Generasi Kedua, then 3, 4... */
	static int generation(UUID owner) {
		return Math.max(0, TetuaData.hall(owner).getIntOr(TetuaData.GENERATION, 0));
	}

	static @Nullable UUID master(UUID owner) {
		return TetuaData.uuid(TetuaData.hall(owner).getStringOr(TetuaData.MASTER, ""));
	}

	static boolean isMaster(UUID owner, UUID soul) {
		return soul != null && soul.equals(master(owner));
	}

	static boolean founder(UUID owner) {
		return TetuaData.hall(owner).getBooleanOr(TetuaData.FOUNDER, false);
	}

	/** Days the sect has stood (from the hall's founding day, else the first day E saw it). */
	static long age(MinecraftServer server, UUID owner) {
		long today = Ingatan.today(server);
		long founded = Halls.section(owner, HallData.CORE).getLongOr("founded_day", -1L);
		if (founded < 0L) {
			CompoundTag mine = TetuaData.hall(owner);
			founded = mine.getLongOr(TetuaData.FIRST_DAY, -1L);
			if (founded < 0L) {
				mine.putLong(TetuaData.FIRST_DAY, today);
				TetuaData.dirty();
				founded = today;
			}
		}
		return Math.max(0L, today - founded);
	}

	// ================================================================== the heir

	/** {@code SekteBus.HEIR_NAMED} (fired by {@code Heir}): remember it, honour it, and the passed-over seniors. */
	static void onHeirNamed(MinecraftServer server, UUID owner, UUID soul, @Nullable UUID previous) {
		CompoundTag mine = TetuaData.hall(owner);
		DiscipleEntity heir = JabatanTetua.loaded(owner, soul);
		mine.putString(TetuaData.HEIR, soul.toString());
		if (heir != null) {
			TetuaData.putName(mine, TetuaData.HEIR_NAME, heir.getName());
		}
		TetuaData.dirty();
		if (heir == null) {
			return;
		}
		DiscipleData data = heir.data();
		Ingatan.remember(server, data, "tetua.pewaris", owner, 8, "kultivator.sekte.tetua.memory.heir", "");
		SekteLog.log(server, owner, SekteIds.E, Component.translatable("kultivator.sekte.tetua.log.heir", heir.getName()));
		// Another disciple with a stronger bond than the heir's feels passed over (once each).
		int heirBond = DisciplePaths.bond(data);
		int told = 0;
		for (DiscipleEntity other : Disciples.loaded(owner)) {
			if (other == heir || other.role() != DiscipleEntity.Role.DISCIPLE || told >= 3) {
				continue;
			}
			String key = other.soulId().toString();
			if (DisciplePaths.bond(other.data()) > heirBond && !TetuaData.listHas(mine, TetuaData.UNFAIR, key)) {
				TetuaData.listAdd(mine, TetuaData.UNFAIR, key, 64);
				SekteBus.mistreated(server, owner, other.soulId(), Perlakuan.TIDAK_ADIL, "tetua.pewaris");
				Ingatan.remember(server, other.data(), "tetua.dilangkahi", soul, -3, "kultivator.sekte.tetua.memory.passed_over", heir.getName().getString());
				told++;
			}
		}
	}

	/** Once a second per disciple: older worlds learn their heir; the gold name of the Ketua stays (or goes). */
	static void tick20(MinecraftServer server, UUID owner, DiscipleEntity entity) {
		DiscipleData data = entity.data();
		CompoundTag mine = TetuaData.hall(owner);
		if (mine.getStringOr(TetuaData.HEIR, "").isEmpty() && DisciplePaths.isHeir(data)) {
			mine.putString(TetuaData.HEIR, entity.soulId().toString());
			TetuaData.putName(mine, TetuaData.HEIR_NAME, entity.getName());
			TetuaData.dirty();
		}
		boolean ketua = isMaster(owner, entity.soulId());
		boolean gold = TetuaData.disciple(data).getBooleanOr(TetuaData.D_GOLD, false);
		if (ketua) {
			Component name = entity.getCustomName();
			TextColor colour = name == null ? null : name.getStyle().getColor();
			if (name != null && (colour == null || colour.getValue() != GOLD)) {
				entity.setCustomName(name.copy().withStyle(style -> style.withColor(GOLD)));
			}
			if (!gold) {
				TetuaData.discipleSection(data).putBoolean(TetuaData.D_GOLD, true);
			}
		} else if (gold) {
			Component name = entity.getCustomName();
			if (name != null) {
				entity.setCustomName(Component.literal(name.getString()));
			}
			TetuaData.discipleSection(data).remove(TetuaData.D_GOLD);
		}
	}

	// ================================================================== the ceremony's checklist

	/** One line of the ceremony checklist. */
	record Requirement(boolean ok, Component text) {
	}

	/** Every requirement (DESIGN 36.2.6): an heir loaded, Nascent Soul+, 9 stars, hall level 5, the sect's age. */
	static List<Requirement> requirements(MinecraftServer server, UUID owner) {
		List<Requirement> list = new ArrayList<>();
		UUID heirSoul = heir(owner);
		DiscipleEntity heir = heirSoul == null ? null : JabatanTetua.loaded(owner, heirSoul);
		boolean current = heir != null && DisciplePaths.isHeir(heir.data());
		list.add(new Requirement(current, current ? Component.translatable("kultivator.sekte.tetua.req.heir", heir.getName())
				: Component.translatable(heirSoul == null ? "kultivator.sekte.tetua.req.heir_none" : "kultivator.sekte.tetua.req.heir_away")));
		DiscipleData data = heir == null ? null : heir.data();
		list.add(new Requirement(data != null && data.realm().atLeast(HEIR_REALM),
				Component.translatable("kultivator.sekte.tetua.req.realm", HEIR_REALM.displayName())));
		int stars = data == null ? 0 : DisciplePaths.stars(data);
		list.add(new Requirement(stars >= HEIR_STARS, Component.translatable("kultivator.sekte.tetua.req.stars", stars, HEIR_STARS)));
		int level = Halls.level(owner);
		list.add(new Requirement(level >= HALL_LEVEL, Component.translatable("kultivator.sekte.tetua.req.hall", level, HALL_LEVEL)));
		long age = age(server, owner);
		int need = Tetua.config().takhtaHari;
		list.add(new Requirement(age >= need, Component.translatable("kultivator.sekte.tetua.req.age", age, need)));
		if (heirSoul != null && isMaster(owner, heirSoul)) {
			list.add(new Requirement(false, Component.translatable("kultivator.sekte.tetua.req.already")));
		}
		return list;
	}

	static boolean ready(MinecraftServer server, UUID owner) {
		for (Requirement requirement : requirements(server, owner)) {
			if (!requirement.ok()) {
				return false;
			}
		}
		return true;
	}

	// ================================================================== succession (the ceremony's climax)

	/** The permanent result: the heir rules as the next generation, the master becomes the Founding Patriarch. */
	static int succeed(MinecraftServer server, ServerPlayer master, DiscipleEntity heir) {
		UUID owner = master.getUUID();
		CompoundTag mine = TetuaData.hall(owner);
		int generation = Math.max(2, generation(owner) + 1);
		UUID before = master(owner);
		if (before != null && !before.equals(heir.soulId())) {
			DiscipleEntity old = JabatanTetua.loaded(owner, before);
			if (old != null) {
				Ingatan.remember(server, old.data(), "tetua.purna", owner, 5, "kultivator.sekte.tetua.memory.emeritus", Integer.toString(generation));
			}
		}
		// the new master leaves any office it held
		Jabatan held = TetuaData.officeOf(owner, heir.soulId());
		if (held != null) {
			JabatanTetua.release(server, owner, heir.soulId(), held, null, 0, null);
		}
		mine.putString(TetuaData.MASTER, heir.soulId().toString());
		TetuaData.putName(mine, TetuaData.MASTER_NAME, Component.literal(heir.getName().getString()));
		mine.putInt(TetuaData.GENERATION, generation);
		long today = Ingatan.today(server);
		ListTag lineage = TetuaData.list(mine, TetuaData.LINEAGE);
		if (lineage.isEmpty()) {
			// the first line of the lineage is the founder
			CompoundTag first = new CompoundTag();
			first.putInt("gen", 1);
			TetuaData.putName(first, "name", Component.literal(master.getName().getString()));
			first.putLong("day", Math.max(0L, today - age(server, owner)));
			first.putBoolean("founder", true);
			lineage.add(first);
		}
		CompoundTag line = new CompoundTag();
		line.putInt("gen", generation);
		line.putString("soul", heir.soulId().toString());
		TetuaData.putName(line, "name", Component.literal(heir.getName().getString()));
		line.putLong("day", today);
		lineage.add(line);
		while (lineage.size() > 16) {
			lineage.remove(1);
		}
		boolean firstSuccession = !mine.getBooleanOr(TetuaData.FOUNDER, false);
		mine.putBoolean(TetuaData.FOUNDER, true);
		Halls.HallView hall = Halls.hallOf(owner);
		if (firstSuccession) {
			mine.putLong(TetuaData.RENOWN_SEEN, hall == null ? 0L : hall.renown());
			mine.putDouble(TetuaData.RENOWN_REST, 0.0D);
		}
		TetuaData.dirty();
		Ingatan.remember(server, heir.data(), "tetua.takhta", owner, 10, "kultivator.sekte.tetua.memory.throne", Integer.toString(generation));
		SekteBus.honored(server, owner, heir.soulId(), "tetua.takhta", 10);
		SekteBus.SUCCESSION.invoker().onSuccession(server, owner, heir.soulId());
		greetAll(server, owner);
		SekteLog.log(server, owner, SekteIds.E, Component.translatable("kultivator.sekte.tetua.log.succession", heir.getName(), generation,
				master.getName()));
		Msg.chat(master, Component.translatable("kultivator.sekte.tetua.takhta.done", heir.getName(), generation).withColor(GOLD));
		Msg.chat(master, Component.translatable("kultivator.sekte.tetua.takhta.founder").withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC));
		Advancements.grant(master, "sekte/tetua_penerus");
		return generation;
	}

	// ================================================================== the founder's renown and the NPC sects

	/** Every 30 s for an online master: the founder's tenth of new renown, +10 with newly met NPC sects, living offices. */
	static void tickOwner(MinecraftServer server, ServerPlayer master) {
		UUID owner = master.getUUID();
		Halls.HallView hall = Halls.hallOf(owner);
		if (hall == null) {
			return;
		}
		CompoundTag mine = TetuaData.hall(owner);
		if (mine.getBooleanOr(TetuaData.FOUNDER, false)) {
			long seen = mine.getLongOr(TetuaData.RENOWN_SEEN, hall.renown());
			long now = hall.renown();
			if (now > seen) {
				double rest = mine.getDoubleOr(TetuaData.RENOWN_REST, 0.0D) + (now - seen) * FOUNDER_RENOWN;
				mine.putLong(TetuaData.RENOWN_SEEN, now);
				if (rest >= RENOWN_BATCH) {
					int pay = (int) Math.floor(rest);
					rest -= pay;
					mine.putDouble(TetuaData.RENOWN_REST, rest);
					Halls.addRenown(owner, pay, "kultivator.sekte.tetua.renown.founder");
					// our own payment is not a gain to share again
					Halls.HallView after = Halls.hallOf(owner);
					mine.putLong(TetuaData.RENOWN_SEEN, after == null ? now + pay : after.renown());
				} else {
					mine.putDouble(TetuaData.RENOWN_REST, rest);
				}
				TetuaData.dirty();
			} else if (now < seen) {
				mine.putLong(TetuaData.RENOWN_SEEN, now);
				TetuaData.dirty();
			}
			greetAll(server, owner);
		}
		checkLiving(server, master);
	}

	/**
	 * The founder's name opens doors: +10 standing once with every NPC sect known (DESIGN 36.2.6). Module D grants this
	 * itself from {@code SUCCESSION} (its "leluhur" hall flag: +10 on every existing relation and on every relation made
	 * later), so this pass is off by default to avoid paying the bonus twice ({@link #GREET_SELF}).
	 */
	static void greetAll(MinecraftServer server, UUID owner) {
		if (!GREET_SELF || !founder(owner)) {
			return;
		}
		CompoundTag mine = TetuaData.hall(owner);
		for (String sect : SekteApi.diplomasi().sects()) {
			if (sect != null && !TetuaData.listHas(mine, TetuaData.GREETED, sect)) {
				TetuaData.listAdd(mine, TetuaData.GREETED, sect, 256);
				SekteApi.diplomasi().adjust(owner, sect, FOUNDER_STANDING, "tetua.leluhur");
			}
		}
	}

	/** Offices of disciples that died or were forgotten are vacated (the master's list is the truth). */
	private static void checkLiving(MinecraftServer server, ServerPlayer master) {
		UUID owner = master.getUUID();
		Set<String> living = new HashSet<>();
		ListTag list = Cultivators.get(master).extra().getCompoundOrEmpty(Disciples.EXTRA).getListOrEmpty("list");
		for (Tag tag : list) {
			if (tag instanceof CompoundTag entry && !entry.getBooleanOr("dead", false)) {
				living.add(entry.getStringOr("id", ""));
			}
		}
		for (var entry : TetuaData.offices(owner).entrySet()) {
			if (!living.contains(entry.getValue().toString())) {
				Component name = TetuaData.name(TetuaData.office(owner, entry.getKey()), "name");
				JabatanTetua.release(server, owner, entry.getValue(), entry.getKey(), null, 0, null);
				SekteLog.log(server, owner, SekteIds.E, Component.translatable("kultivator.sekte.tetua.log.vacant", Component.translatable(entry.getKey().key()), name));
			}
		}
		UUID ketua = master(owner);
		if (ketua != null && !living.contains(ketua.toString())) {
			// the Ketua fell: the founder rules again until the next succession (the lineage keeps its line)
			CompoundTag mine = TetuaData.hall(owner);
			mine.remove(TetuaData.MASTER);
			mine.remove(TetuaData.MASTER_NAME);
			TetuaData.dirty();
			SekteLog.log(server, owner, SekteIds.E, Component.translatable("kultivator.sekte.tetua.log.master_fallen"));
		}
	}

	/** "Leluhur Pendiri <name>" or null for a master who never passed the throne. */
	static @Nullable MutableComponent founderTitle(UUID owner, Component name) {
		return founder(owner) ? Component.translatable("kultivator.sekte.tetua.title.founder", name).withColor(GOLD) : null;
	}

	private Generasi() {
	}
}
