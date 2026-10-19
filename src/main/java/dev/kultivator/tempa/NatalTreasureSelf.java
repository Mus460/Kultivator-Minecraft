package dev.kultivator.tempa;

import dev.kultivator.Kultivator;
import dev.kultivator.cultivation.Deviation;
import dev.kultivator.cultivation.Meditation;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Element;
import dev.kultivator.data.Realm;
import dev.kultivator.epic.EpicHooks;
import dev.kultivator.epic.EpicIds;
import dev.kultivator.fx.Fx;
import dev.kultivator.fx.FxType;
import dev.kultivator.harta.SpiritStones;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The player's own natal treasure, a virtual record in {@code extra.dunia.natal {art, tempered}} (DESIGN 25.5.6, 25.15,
 * owned by P8-tempa). Binding takes Golden Core+, an artifact of grade Misterius+ not bound to anyone else, 4 Mid stones
 * and 30 s of meditation holding it; the item then vanishes into the body (RMJ ch. 415) and only Lepas turns it back into
 * an item, so nothing can duplicate it. Up to 2 more artifacts can be linked ({@code extra.dunia.linked}), for 3 at once
 * (RMJ: Immortal Realm ch. 345). The synced {@link #LOOK} lets every client draw it.
 */
public final class NatalTreasureSelf {

	public static final String DUNIA = "dunia";
	public static final String NATAL = "natal";
	public static final String LINKED = "linked";
	public static final String BIND_CD = "bind_cd";
	public static final String TEMPA = "tempa";

	public static final Realm BIND_REALM = Realm.GOLDEN_CORE;
	public static final int MIN_GRADE = 1;
	public static final int BIND_STONES = 4;
	public static final int BIND_SECONDS = 30;
	public static final int MAX_LINKED = 2;
	public static final long UNBIND_COOLDOWN = 24000L;
	public static final double UNBIND_YEARS_KEEP = 0.9D;
	public static final int DEVIATION_TICKS = 6000;

	/**
	 * What other clients need to draw the natal treasure, 0 for none: {@code 1 + form | grade << 4 | tint << 8 |
	 * stage << 12 | (element + 1) << 16}, tint 0 plain, 1 silver, 2 gold, 3 azure, 4 white.
	 */
	public static final AttachmentType<Integer> LOOK = AttachmentRegistry.<Integer>builder()
			.initializer(() -> 0)
			.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all())
			.buildAndRegister(Kultivator.id("natal_look"));

	private record Ritual(UUID artifact, long start) {
	}

	private static final Map<UUID, Ritual> RITUALS = new HashMap<>();

	// ================================================================== contract

	/** A copy of the bound natal treasure's stack, or null when none is bound. */
	public static @Nullable ItemStack of(ServerPlayer player) {
		CompoundTag art = record(player);
		return art == null ? null : ArtifactItem.fromData(art);
	}

	/** The natal treasure's grade 0-5 (Kuning … Dewa), -1 when none is bound. */
	public static int grade(ServerPlayer player) {
		CompoundTag art = record(player);
		return art == null ? -1 : ArtifactItem.grade(art);
	}

	// ================================================================== record

	/** The player's {@code extra.dunia} section (created when missing). */
	static CompoundTag dunia(CultivationData data) {
		CompoundTag extra = data.extra();
		CompoundTag dunia = extra.getCompound(DUNIA).orElse(null);
		if (dunia == null) {
			dunia = new CompoundTag();
			extra.put(DUNIA, dunia);
		}
		return dunia;
	}

	/** The player's {@code extra.tempa} section (created when missing). */
	static CompoundTag tempa(CultivationData data) {
		CompoundTag extra = data.extra();
		CompoundTag tempa = extra.getCompound(TEMPA).orElse(null);
		if (tempa == null) {
			tempa = new CompoundTag();
			extra.put(TEMPA, tempa);
		}
		return tempa;
	}

	/** The live natal record (the artifact data), or null. Read only; change it through {@link #update}. */
	static @Nullable CompoundTag record(ServerPlayer player) {
		CompoundTag natal = Cultivators.get(player).extra().getCompoundOrEmpty(DUNIA).getCompoundOrEmpty(NATAL);
		CompoundTag art = natal.getCompound("art").orElse(null);
		return art == null || art.isEmpty() ? null : art;
	}

	static boolean bound(ServerPlayer player) {
		return record(player) != null;
	}

	/** Replaces the natal record (null removes it), syncs the look and the Pusaka tablet. */
	static void update(ServerPlayer player, @Nullable CompoundTag art) {
		CultivationData data = Cultivators.get(player);
		CompoundTag dunia = dunia(data);
		if (art == null) {
			dunia.remove(NATAL);
		} else {
			CompoundTag natal = dunia.getCompound(NATAL).orElse(null);
			if (natal == null) {
				natal = new CompoundTag();
				dunia.put(NATAL, natal);
			}
			natal.put("art", art.copy());
		}
		Cultivators.dirty(player);
		refreshLook(player);
	}

	static boolean tempered(ServerPlayer player) {
		return Cultivators.get(player).extra().getCompoundOrEmpty(DUNIA).getCompoundOrEmpty(NATAL).getBooleanOr("tempered", false);
	}

	static void setTempered(ServerPlayer player, boolean tempered) {
		CompoundTag natal = dunia(Cultivators.get(player)).getCompound(NATAL).orElse(null);
		if (natal != null) {
			natal.putBoolean("tempered", tempered);
			Cultivators.dirty(player);
		}
	}

	// ================================================================== look

	static int lookOf(@Nullable CompoundTag art) {
		if (art == null) {
			return 0;
		}
		int refine = ArtifactItem.refine(art);
		int tint = refine >= 12 ? 4 : refine >= 9 ? 3 : refine >= 6 ? 2 : refine >= 3 ? 1 : 0;
		Element element = ArtifactItem.element(art);
		return (1 + ArtifactItem.formIndex(ArtifactItem.form(art))) | ArtifactItem.grade(art) << 4 | tint << 8
				| ArtifactItem.spiritStage(art) << 12 | (element == null ? 0 : element.ordinal() + 1) << 16;
	}

	/** Pushes the synced look when it changed. */
	public static void refreshLook(ServerPlayer player) {
		int look = lookOf(record(player));
		Integer old = player.getAttached(LOOK);
		if (old == null || old != look) {
			player.setAttached(LOOK, look);
		}
	}

	// ================================================================== binding

	/** Why {@code stack} cannot become the natal treasure now, or null. */
	static @Nullable Component bindProblem(ServerPlayer player, ItemStack stack, boolean free) {
		if (bound(player)) {
			return Component.translatable("kultivator.tempa.natal.already");
		}
		if (!ArtifactItem.is(stack)) {
			return Component.translatable("kultivator.tempa.natal.hold");
		}
		CompoundTag art = ArtifactItem.data(stack);
		UUID owner = ArtifactItem.bound(art);
		if (owner != null && !owner.equals(player.getUUID())) {
			return Component.translatable("kultivator.tempa.foreign");
		}
		if (free) {
			return null;
		}
		CultivationData data = Cultivators.get(player);
		if (!data.realm().atLeast(BIND_REALM)) {
			return Component.translatable("kultivator.tempa.natal.realm", BIND_REALM.displayName());
		}
		if (ArtifactItem.grade(art) < MIN_GRADE) {
			return Component.translatable("kultivator.tempa.natal.grade", ArtifactItem.gradeName(MIN_GRADE));
		}
		long cd = dunia(data).getLongOr(BIND_CD, 0L);
		if (player.level().getGameTime() < cd) {
			return Component.translatable("kultivator.tempa.natal.cooldown", (cd - player.level().getGameTime()) / 20 / 60 + 1);
		}
		if (Inv.count(player, s -> SpiritStones.isStone(s) && SpiritStones.grade(s) == SpiritStones.MID) < BIND_STONES) {
			return Component.translatable("kultivator.tempa.natal.stones", BIND_STONES, SpiritStones.name(SpiritStones.MID));
		}
		return null;
	}

	/** "Ikat": starts the 30 s binding meditation with the artifact held in the main hand. */
	public static void beginBind(ServerPlayer player) {
		if (dev.kultivator.alam.Alam.blocked(player)) {
			Msg.chat(player, dev.kultivator.alam.Alam.refusal());
			return;
		}
		ItemStack held = player.getMainHandItem();
		Component problem = bindProblem(player, held, false);
		if (problem != null) {
			Msg.chat(player, problem.copy().withStyle(ChatFormatting.RED));
			return;
		}
		UUID id = ArtifactItem.id(ArtifactItem.data(held));
		if (id == null) {
			Msg.chat(player, Component.translatable("kultivator.tempa.natal.hold").withStyle(ChatFormatting.RED));
			return;
		}
		if (!Meditation.isMeditating(player)) {
			Meditation.start(player);
		}
		RITUALS.put(player.getUUID(), new Ritual(id, player.level().getGameTime()));
		Msg.chat(player, Component.translatable("kultivator.tempa.natal.begin", BIND_SECONDS).withStyle(ChatFormatting.GOLD));
	}

	/** Every 20 ticks: advances the binding ritual. */
	static void tickRitual(ServerPlayer player) {
		Ritual ritual = RITUALS.get(player.getUUID());
		if (ritual == null) {
			return;
		}
		ItemStack held = player.getMainHandItem();
		UUID id = ArtifactItem.is(held) ? ArtifactItem.id(ArtifactItem.data(held)) : null;
		if (!ritual.artifact.equals(id) || !Meditation.isMeditating(player)) {
			RITUALS.remove(player.getUUID());
			Msg.chat(player, Component.translatable("kultivator.tempa.natal.broken").withStyle(ChatFormatting.RED));
			return;
		}
		long elapsed = player.level().getGameTime() - ritual.start;
		if (elapsed < BIND_SECONDS * 20L) {
			Msg.actionBar(player, Component.translatable("kultivator.tempa.natal.progress", elapsed / 20, BIND_SECONDS).withStyle(ChatFormatting.GOLD));
			return;
		}
		RITUALS.remove(player.getUUID());
		Component problem = bindProblem(player, held, false);
		if (problem != null) {
			Msg.chat(player, problem.copy().withStyle(ChatFormatting.RED));
			return;
		}
		Inv.take(player, s -> SpiritStones.isStone(s) && SpiritStones.grade(s) == SpiritStones.MID, BIND_STONES);
		finishBind(player, held);
	}

	/** {@code uji pusaka natal}: binds the held artifact at once. */
	public static boolean bindNow(ServerPlayer player) {
		ItemStack held = player.getMainHandItem();
		Component problem = bindProblem(player, held, true);
		if (problem != null) {
			Msg.chat(player, problem.copy().withStyle(ChatFormatting.RED));
			return false;
		}
		finishBind(player, held);
		return true;
	}

	private static void finishBind(ServerPlayer player, ItemStack held) {
		CompoundTag art = ArtifactItem.data(held);
		art.putString("bound", player.getUUID().toString());
		art.putString("bound_name", player.getName().getString());
		held.shrink(held.getCount());
		unlinkId(player, ArtifactItem.id(art));
		update(player, art);
		setTempered(player, false);
		ServerPlayer p = player;
		p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.0F, 1.2F);
		Fx.send(p.level(), p.position().add(0.0D, 1.0D, 0.0D), FxType.TEMPA, TempaFxIds.NATAL_BOND, ArtifactItem.formIndex(ArtifactItem.form(art)),
				(float) ArtifactItem.tint(art));
		EpicHooks.extra(p.level(), EpicIds.EXTRA_NATAL_BOND, p, p, p.position(), ArtifactItem.grade(art), ArtifactItem.formIndex(ArtifactItem.form(art)));
		Msg.chat(p, Component.translatable("kultivator.tempa.natal.bound", ArtifactItem.displayName(art)).withStyle(ChatFormatting.GOLD));
		Advancements.grant(p, "natal_bond");
		Tempa.sync(p);
	}

	/** "Lepas": the treasure returns as an item; 5 min of Qi Deviation, spirit years -10%, 1 day before the next bond. */
	public static void unbind(ServerPlayer player, boolean free) {
		CompoundTag art = record(player);
		if (art == null) {
			Msg.chat(player, Component.translatable("kultivator.tempa.natal.none").withStyle(ChatFormatting.RED));
			return;
		}
		CompoundTag copy = art.copy();
		CompoundTag spirit = copy.getCompoundOrEmpty("spirit").copy();
		spirit.putDouble("years", ArtifactItem.spiritYears(copy) * UNBIND_YEARS_KEEP);
		copy.put("spirit", spirit);
		update(player, null);
		Inv.give(player, ArtifactItem.fromData(copy));
		CultivationData data = Cultivators.get(player);
		if (!free) {
			dunia(data).putLong(BIND_CD, player.level().getGameTime() + UNBIND_COOLDOWN);
			Deviation.apply(player, DEVIATION_TICKS / (2400.0D + 600.0D * data.realm().ordinal()));
		}
		Cultivators.dirty(player);
		Msg.chat(player, Component.translatable("kultivator.tempa.natal.released", ArtifactItem.displayName(copy)).withStyle(ChatFormatting.YELLOW));
		Tempa.sync(player);
	}

	static void forget(ServerPlayer player) {
		RITUALS.remove(player.getUUID());
	}

	// ================================================================== linked artifacts

	/** The ids of the (up to 2) linked artifacts. */
	static List<UUID> linked(ServerPlayer player) {
		List<UUID> list = new ArrayList<>();
		for (Tag tag : Cultivators.get(player).extra().getCompoundOrEmpty(DUNIA).getListOrEmpty(LINKED)) {
			UUID id = tag.asString().map(ArtifactItem::parse).orElse(null);
			if (id != null) {
				list.add(id);
			}
		}
		return list;
	}

	private static void writeLinked(ServerPlayer player, List<UUID> ids) {
		ListTag list = new ListTag();
		for (UUID id : ids) {
			list.add(StringTag.valueOf(id.toString()));
		}
		dunia(Cultivators.get(player)).put(LINKED, list);
		Cultivators.dirty(player);
	}

	/** "Tautkan": links the held artifact to the player (bound to their soul imprint). */
	public static void link(ServerPlayer player) {
		ItemStack held = player.getMainHandItem();
		if (!ArtifactItem.is(held)) {
			Msg.chat(player, Component.translatable("kultivator.tempa.natal.hold").withStyle(ChatFormatting.RED));
			return;
		}
		CompoundTag art = ArtifactItem.data(held);
		UUID owner = ArtifactItem.bound(art);
		if (owner != null && !owner.equals(player.getUUID())) {
			Msg.chat(player, Component.translatable("kultivator.tempa.foreign").withStyle(ChatFormatting.RED));
			return;
		}
		UUID id = ArtifactItem.id(art);
		List<UUID> ids = linked(player);
		if (id == null || ids.contains(id)) {
			Msg.chat(player, Component.translatable("kultivator.tempa.link.already").withStyle(ChatFormatting.YELLOW));
			return;
		}
		if (ids.size() >= MAX_LINKED) {
			Msg.chat(player, Component.translatable("kultivator.tempa.link.full", MAX_LINKED).withStyle(ChatFormatting.RED));
			return;
		}
		art.putString("bound", player.getUUID().toString());
		art.putString("bound_name", player.getName().getString());
		ArtifactItem.write(held, art);
		ids.add(id);
		writeLinked(player, ids);
		Msg.chat(player, Component.translatable("kultivator.tempa.link.done", ArtifactItem.displayName(art)).withStyle(ChatFormatting.GREEN));
		Tempa.sync(player);
	}

	/** "Lepas Tautan": frees the held artifact's imprint (or, with no artifact in hand, the oldest link). */
	public static void unlink(ServerPlayer player) {
		ItemStack held = player.getMainHandItem();
		List<UUID> ids = linked(player);
		if (ArtifactItem.is(held)) {
			CompoundTag art = ArtifactItem.data(held);
			UUID owner = ArtifactItem.bound(art);
			if (owner != null && !owner.equals(player.getUUID())) {
				Msg.chat(player, Component.translatable("kultivator.tempa.foreign").withStyle(ChatFormatting.RED));
				return;
			}
			art.remove("bound");
			art.remove("bound_name");
			ArtifactItem.write(held, art);
			unlinkId(player, ArtifactItem.id(art));
		} else if (!ids.isEmpty()) {
			ids.remove(0);
			writeLinked(player, ids);
		}
		Msg.chat(player, Component.translatable("kultivator.tempa.link.freed").withStyle(ChatFormatting.YELLOW));
		Tempa.sync(player);
	}

	private static void unlinkId(ServerPlayer player, @Nullable UUID id) {
		if (id == null) {
			return;
		}
		List<UUID> ids = linked(player);
		if (ids.remove(id)) {
			writeLinked(player, ids);
		}
	}

	/** True when a held artifact's effects work for {@code player}: it carries their imprint (or no imprint at all). */
	static boolean usable(ServerPlayer player, CompoundTag art) {
		UUID owner = ArtifactItem.bound(art);
		return owner == null || owner.equals(player.getUUID());
	}

	private NatalTreasureSelf() {
	}
}
