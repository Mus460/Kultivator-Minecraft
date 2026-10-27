package dev.kultivator.sekte.paviliun;

import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.DiscipleItems;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.fx.Fx;
import dev.kultivator.sect.build.SectBuild;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.sekte.Ingatan;
import dev.kultivator.sekte.SekteIds;
import dev.kultivator.sekte.SekteLog;
import dev.kultivator.sekte.api.Ikatan;
import dev.kultivator.sekte.api.Kenangan;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.api.SekteBus;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Pemakaman, the sect's cemetery (DESIGN 36.2.5). A disciple who truly falls (a real fight, Hardcore; DESIGN 36.0 rule 3:
 * nothing of this phase kills) is honoured with a grave: its name, its realm, the day, how it fell, and its three
 * strongest good memories ({@code sekte.Ingatan}) engraved as deeds. The grave takes the next free tombstone of the
 * built {@code sx_paviliun_makam} ({@code makam_<n>} anchors: the client floats the name over it), or is kept as data
 * while the sect has no cemetery yet. A full cemetery moves its oldest grave into the Kitab Silsilah ({@link Silsilah}),
 * never losing it.
 * <p>
 * The death is seen through {@code DiscipleHooks.addOnDeath}, which runs <i>before</i> the death and may still be cancelled
 * by a later module (the Undying Blood): A only takes a snapshot there and buries on the next server tick once the body
 * is really gone. A Soul Tablet brought home from a death far away (the forbidden ground's Hardcore loss) is laid on the
 * altar or a grave to bury it the same way; the tablet is never taken, so a revival stays possible, and a revived
 * disciple's grave is lifted again.
 * <p>
 * Sumber: ziarah makam dan papan arwah leluhur (adat Tiongkok); Soul Tablet {@code AwayTeams}.
 */
final class Makam {

	/** A body that is dying (snapshot taken before the death). */
	private record Pending(DiscipleEntity entity, UUID owner, Snapshot snapshot) {
	}

	/** Everything engraved, taken while the body still exists. */
	record Snapshot(UUID soul, Component name, int step, String cause, String causeArg, List<PaviliunData.Deed> deeds, int life,
			@Nullable ResourceKey<net.minecraft.world.level.Level> dimension, @Nullable Vec3 pos) {
	}

	private static final List<Pending> PENDING = new ArrayList<>();
	/** Buried souls per owner (rebuilt when the graves change), for the O(1) revival check. */
	private static final Map<UUID, Set<UUID>> BURIED = new HashMap<>();
	/** Built tombstones per owner (re-counted every 30 s). */
	private static final Map<UUID, Integer> SLOTS = new HashMap<>();
	private static final Map<UUID, Long> SLOTS_AT = new HashMap<>();

	static void clear() {
		PENDING.clear();
		BURIED.clear();
		SLOTS.clear();
		SLOTS_AT.clear();
	}

	// ================================================================== death

	/** {@code DiscipleHooks.addOnDeath}: snapshot only, never cancels the death. */
	static boolean onDeath(DiscipleEntity entity, DamageSource source) {
		if (!Paviliun.active() || entity.role() != DiscipleEntity.Role.DISCIPLE || entity.isReleasing()) {
			return false;
		}
		UUID owner = entity.ownerId();
		if (owner == null || Halls.hallOf(owner) == null) {
			return false;
		}
		String cause = "lain";
		String arg = "";
		Entity killer = source.getEntity();
		if (killer instanceof LivingEntity && killer != entity) {
			cause = "tempur";
			arg = killer.getDisplayName().getString();
		} else if (source.is(DamageTypeTags.IS_FALL)) {
			cause = "jatuh";
		} else if (source.is(DamageTypeTags.IS_FIRE)) {
			cause = "api";
		} else if (source.is(DamageTypeTags.IS_DROWNING)) {
			cause = "tenggelam";
		} else if (source.is(DamageTypeTags.IS_EXPLOSION)) {
			cause = "ledakan";
		} else if (source.is(DamageTypeTags.IS_FREEZING)) {
			cause = "beku";
		} else if (source.is(DamageTypes.FELL_OUT_OF_WORLD)) {
			cause = "hampa";
		} else if (source.is(DamageTypeTags.IS_LIGHTNING) || typeId(source).contains("tribulation") || typeId(source).contains("petir")) {
			cause = "kesengsaraan";
		} else if (source.is(DamageTypes.MAGIC) || source.is(DamageTypes.WITHER) || source.is(DamageTypes.INDIRECT_MAGIC)) {
			cause = "racun";
		}
		DiscipleData data = entity.data();
		Snapshot snapshot = new Snapshot(entity.soulId(), entity.getName().copy(), data.rank().step(), cause, arg, deeds(data), entity.life(),
				entity.level().dimension(), entity.position());
		PENDING.add(new Pending(entity, owner, snapshot));
		return false;
	}

	private static String typeId(DamageSource source) {
		return source.typeHolder().unwrapKey().map(ResourceKey::identifier).map(Object::toString).orElse("");
	}

	/** The three strongest good memories (the deeds on the stone), strongest first; bad ones only if nothing good. */
	static List<PaviliunData.Deed> deeds(DiscipleData data) {
		List<Kenangan> all = new ArrayList<>(Ingatan.of(data));
		all.sort(Comparator.comparingInt((Kenangan k) -> k.weight() > 0 ? 0 : 1).thenComparingInt(k -> -Math.abs(k.weight()))
				.thenComparingLong(k -> -k.day()));
		List<PaviliunData.Deed> deeds = new ArrayList<>(3);
		for (Kenangan k : all) {
			if (deeds.size() >= 3) {
				break;
			}
			if (k.text().isEmpty() || (k.weight() <= 0 && !deeds.isEmpty())) {
				continue;
			}
			deeds.add(new PaviliunData.Deed(k.text(), k.arg(), k.weight()));
		}
		return deeds;
	}

	/** Each server tick: the snapshots of bodies that really died are buried; the saved ones forgotten. */
	static void tick(MinecraftServer server) {
		if (PENDING.isEmpty()) {
			return;
		}
		List<Pending> due = new ArrayList<>(PENDING);
		PENDING.clear();
		for (Pending p : due) {
			DiscipleEntity e = p.entity();
			if (e.departed() || (!e.isDeadOrDying() && e.getRemovalReason() != Entity.RemovalReason.KILLED)) {
				// a later death handler saved it (maybe already unloaded or departed since: not a death)
				continue;
			}
			bury(server, p.owner(), p.snapshot(), false);
		}
	}

	// ================================================================== burial

	/** Buries {@code s} (from a death or a laid tablet). Returns the grave, or null when the module is off. */
	static @Nullable PaviliunData.Grave bury(MinecraftServer server, UUID owner, Snapshot s, boolean fromTablet) {
		if (!Paviliun.active() || Halls.hallOf(owner) == null) {
			return null;
		}
		long today = Ingatan.today(server);
		List<PaviliunData.Grave> graves = new ArrayList<>(PaviliunData.graves(owner));
		boolean enshrined = false;
		for (Iterator<PaviliunData.Grave> it = graves.iterator(); it.hasNext(); ) {
			PaviliunData.Grave old = it.next();
			if (old.soul().equals(s.soul())) {
				enshrined = old.enshrined();
				it.remove();
			}
		}
		int slots = slots(owner, true);
		int limit = slots > 0 ? slots : Paviliun.config().makamTanpaPemakaman;
		// a full cemetery: the oldest grave goes into the Kitab Silsilah
		while (graves.size() >= limit) {
			PaviliunData.Grave oldest = graves.get(graves.size() - 1);
			graves.remove(graves.size() - 1);
			Silsilah.add(owner, oldest.withSlot(-1));
			SekteLog.log(server, owner, SekteIds.A, Component.translatable("kultivator.sekte.paviliun.log.to_lineage", oldest.name()));
		}
		int slot = slots > 0 ? freeSlot(graves, slots) : -1;
		PaviliunData.Grave grave = new PaviliunData.Grave(s.soul(), s.name(), s.step(), today, s.cause(), s.causeArg(), s.deeds(), enshrined, slot,
				s.life());
		graves.add(0, grave);
		PaviliunData.setGraves(owner, graves);
		PaviliunData.bump(owner, PaviliunData.BURIED);
		rebuild(owner);

		// mourners are found before the bus (module R drops its pair table on DISCIPLE_BURIED)
		List<DiscipleEntity> mourners = mourners(owner, s.soul());
		Component where = slot >= 0 ? Component.translatable("kultivator.sekte.paviliun.makam.where", slot + 1)
				: Component.translatable("kultivator.sekte.paviliun.makam.where_none");
		SekteLog.log(server, owner, SekteIds.A, Component.translatable("kultivator.sekte.paviliun.log.buried", s.name(), where));
		ServerPlayer master = server.getPlayerList().getPlayer(owner);
		if (master != null) {
			Msg.chat(master, Component.translatable(fromTablet ? "kultivator.sekte.paviliun.makam.buried_tablet" : "kultivator.sekte.paviliun.makam.buried",
					s.name(), where).withColor(0xC8D2E0));
			Msg.chat(master, Component.translatable("kultivator.sekte.paviliun.makam.revive_hint").withStyle(ChatFormatting.DARK_GRAY));
		}
		BlockPos stone = slot >= 0 ? SectBuild.anchor(owner, PaviliunKomponen.ANCHOR_GRAVE + slot) : null;
		ServerLevel level = hallLevel(server, owner);
		if (level != null && stone != null && level.isLoaded(stone)) {
			Vec3 at = Vec3.atBottomCenterOf(stone);
			Fx.pillar(level, at, 0xD8E4FF, 3.0F);
			level.playSound(null, at.x, at.y + 1.0D, at.z, SoundEvents.SOUL_ESCAPE.value(), SoundSource.NEUTRAL, 1.0F, 0.7F);
		}
		SekteBus.DISCIPLE_BURIED.invoker().onBuried(server, owner, s.soul(), s.name());
		Upacara.funeral(server, owner, grave, stone, mourners);
		return grave;
	}

	/**
	 * Who mourns: the dead's friends, sworn siblings, beloved and Dao companion (module R), and anyone who remembers the
	 * dead kindly; loaded, free and in the territory; at most six.
	 */
	private static List<DiscipleEntity> mourners(UUID owner, UUID dead) {
		Set<UUID> souls = new LinkedHashSet<>();
		for (Ikatan kind : new Ikatan[]{Ikatan.PASANGAN_DAO, Ikatan.KEKASIH, Ikatan.SAUDARA_ANGKAT, Ikatan.SAHABAT}) {
			souls.addAll(SekteApi.hubungan().with(owner, dead, kind));
		}
		List<DiscipleEntity> list = new ArrayList<>();
		List<DiscipleEntity> loaded = Disciples.loaded(owner);
		for (DiscipleEntity d : loaded) {
			if (souls.contains(d.soulId()) && Tempat.free(d, true, DiscipleEntity.ACTIVITY_NONE) && list.size() < 6) {
				list.add(d);
			}
		}
		for (DiscipleEntity d : loaded) {
			if (list.size() >= 6) {
				break;
			}
			if (list.contains(d) || d.soulId().equals(dead) || !Tempat.free(d, true, DiscipleEntity.ACTIVITY_NONE)) {
				continue;
			}
			for (Kenangan k : Ingatan.about(d.data(), dead)) {
				if (k.weight() > 0) {
					list.add(d);
					break;
				}
			}
		}
		return list;
	}

	/** The lowest tombstone slot no grave holds (0..slots-1), or -1. */
	private static int freeSlot(List<PaviliunData.Grave> graves, int slots) {
		Set<Integer> used = new HashSet<>();
		for (PaviliunData.Grave g : graves) {
			used.add(g.slot());
		}
		for (int i = 0; i < slots; i++) {
			if (!used.contains(i)) {
				return i;
			}
		}
		return -1;
	}

	/**
	 * Keeps the graves fitting the cemetery as it is built or shrinks: graves without a tombstone take a free one, graves
	 * beyond the built ones become data again; over the limit the oldest go into the Silsilah. Cheap; every 30 s.
	 */
	static void normalize(MinecraftServer server, UUID owner) {
		List<PaviliunData.Grave> graves = PaviliunData.graves(owner);
		if (graves.isEmpty()) {
			return;
		}
		int slots = slots(owner, true);
		int limit = slots > 0 ? slots : Paviliun.config().makamTanpaPemakaman;
		List<PaviliunData.Grave> fixed = new ArrayList<>(graves);
		boolean changed = false;
		while (fixed.size() > limit) {
			Silsilah.add(owner, fixed.remove(fixed.size() - 1).withSlot(-1));
			changed = true;
		}
		for (int i = 0; i < fixed.size(); i++) {
			PaviliunData.Grave g = fixed.get(i);
			if (slots == 0 && g.slot() >= 0 || g.slot() >= slots) {
				fixed.set(i, g.withSlot(-1));
				changed = true;
			}
		}
		if (slots > 0) {
			for (int i = fixed.size() - 1; i >= 0; i--) {
				PaviliunData.Grave g = fixed.get(i);
				if (g.slot() < 0) {
					int slot = freeSlot(fixed, slots);
					if (slot >= 0) {
						fixed.set(i, g.withSlot(slot));
						changed = true;
					}
				}
			}
		}
		if (changed) {
			PaviliunData.setGraves(owner, fixed);
			rebuild(owner);
			PaviliunSync.dirty(owner);
		}
	}

	/** The built tombstones of the owner's cemetery (0 without one); counted every 30 s unless {@code fresh}. */
	static int slots(UUID owner, boolean fresh) {
		long now = System.currentTimeMillis();
		Long at = SLOTS_AT.get(owner);
		if (!fresh && at != null && now - at < 30_000L) {
			return SLOTS.getOrDefault(owner, 0);
		}
		int n = 0;
		while (n < 64 && SectBuild.anchor(owner, PaviliunKomponen.ANCHOR_GRAVE + n) != null) {
			n++;
		}
		SLOTS.put(owner, n);
		SLOTS_AT.put(owner, now);
		return n;
	}

	// ================================================================== revival, tablets

	/** Once a second per living disciple: a soul back from the dead (revived with its tablet) leaves its grave. */
	static void tick20(MinecraftServer server, UUID owner, DiscipleEntity entity) {
		Set<UUID> buried = BURIED.computeIfAbsent(owner, Makam::collect);
		if (!buried.contains(entity.soulId())) {
			return;
		}
		List<PaviliunData.Grave> graves = new ArrayList<>(PaviliunData.graves(owner));
		boolean removed = graves.removeIf(g -> g.soul().equals(entity.soulId()) && g.life() < entity.life());
		if (!removed) {
			return;
		}
		PaviliunData.setGraves(owner, graves);
		rebuild(owner);
		PaviliunSync.dirty(owner);
		SekteLog.log(server, owner, SekteIds.A, Component.translatable("kultivator.sekte.paviliun.log.revived", entity.getName()));
		ServerPlayer master = server.getPlayerList().getPlayer(owner);
		if (master != null) {
			Msg.chat(master, Component.translatable("kultivator.sekte.paviliun.makam.revived", entity.getName()).withColor(0xB8F0FF));
		}
	}

	private static Set<UUID> collect(UUID owner) {
		Set<UUID> set = new HashSet<>();
		for (PaviliunData.Grave g : PaviliunData.graves(owner)) {
			set.add(g.soul());
		}
		return set;
	}

	static void rebuild(UUID owner) {
		BURIED.put(owner, collect(owner));
	}

	/**
	 * The master lays a Soul Tablet at the altar or a grave: a soul without a grave is buried (a death far from the sect);
	 * one already resting gets a moment of remembrance. The tablet stays in the hand. True when handled.
	 */
	static boolean layTablet(ServerPlayer master, ItemStack stack) {
		CustomData custom = stack.get(DataComponents.CUSTOM_DATA);
		CompoundTag tablet = custom == null ? new CompoundTag() : custom.copyTag().getCompoundOrEmpty("kultivator_tablet");
		UUID soul = tablet.read("soul", UUIDUtil.CODEC).orElse(null);
		UUID tabletOwner = tablet.read("owner", UUIDUtil.CODEC).orElse(null);
		if (soul == null) {
			return false;
		}
		MinecraftServer server = master.level().getServer();
		UUID owner = master.getUUID();
		if (tabletOwner != null && !tabletOwner.equals(owner)) {
			Msg.actionBar(master, Component.translatable("kultivator.sekte.paviliun.makam.not_yours").withStyle(ChatFormatting.YELLOW));
			return true;
		}
		Component name = tablet.read("name", ComponentSerialization.CODEC).orElse(Component.translatable("kultivator.sekte.paviliun.someone"));
		for (PaviliunData.Grave g : PaviliunData.graves(owner)) {
			if (g.soul().equals(soul)) {
				Msg.chat(master, Component.translatable("kultivator.sekte.paviliun.makam.already", name).withColor(0xC8D2E0));
				master.level().playSound(null, master.getX(), master.getY() + 1.0D, master.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS,
						0.8F, 0.7F);
				return true;
			}
		}
		for (DiscipleEntity d : Disciples.loaded(owner)) {
			if (d.soulId().equals(soul)) {
				Msg.actionBar(master, Component.translatable("kultivator.sekte.paviliun.makam.alive", name).withStyle(ChatFormatting.YELLOW));
				return true;
			}
		}
		DiscipleData data = DiscipleData.load(tablet.getCompoundOrEmpty("disciple"));
		Snapshot s = new Snapshot(soul, name, data.rank().step(), "jauh", "", deeds(data), tablet.getIntOr("life", 0), null, null);
		bury(server, owner, s, true);
		return true;
	}

	/** True when {@code stack} is a Soul Tablet. */
	static boolean isTablet(ItemStack stack) {
		return stack.is(DiscipleItems.DISCIPLE_TABLET);
	}

	/** The test command's burial of a loaded disciple's likeness (it stays alive; the grave is data like any other). */
	static @Nullable PaviliunData.Grave buryTest(MinecraftServer server, UUID owner, DiscipleEntity entity) {
		Snapshot s = new Snapshot(UUID.randomUUID(), entity.getName().copy(), entity.data().rank().step(), "uji", "", deeds(entity.data()), 0,
				entity.level().dimension(), entity.position());
		return bury(server, owner, s, false);
	}

	static @Nullable ServerLevel hallLevel(MinecraftServer server, UUID owner) {
		Halls.HallView hall = Halls.hallOf(owner);
		return hall == null ? null : server.getLevel(hall.dim());
	}

	private Makam() {
	}
}
