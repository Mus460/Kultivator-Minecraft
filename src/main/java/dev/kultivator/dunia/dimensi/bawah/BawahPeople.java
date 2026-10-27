package dev.kultivator.dunia.dimensi.bawah;

import dev.kultivator.Kultivator;
import dev.kultivator.cultivation.Deviation;
import dev.kultivator.cultivation.Stats;
import dev.kultivator.dao.Comprehension;
import dev.kultivator.dao.DaoPaths;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Dao;
import dev.kultivator.data.Realm;
import dev.kultivator.data.Technique;
import dev.kultivator.data.TechniqueRegistry;
import dev.kultivator.data.TechniqueSpec;
import dev.kultivator.dunia.DuniaAnchorEntity;
import dev.kultivator.dunia.DuniaAnchors;
import dev.kultivator.dunia.DuniaBudget;
import dev.kultivator.dunia.dimensi.Dimensi;
import dev.kultivator.dunia.dimensi.DimensiChatter;
import dev.kultivator.dunia.dimensi.DimensiConfig;
import dev.kultivator.dunia.dimensi.DimensiData;
import dev.kultivator.dunia.dimensi.DimensiEntities;
import dev.kultivator.dunia.dimensi.DimensiItems;
import dev.kultivator.dunia.dimensi.DimensiNet;
import dev.kultivator.dunia.dimensi.DimensiSky;
import dev.kultivator.dunia.dimensi.DimensiVoice;
import dev.kultivator.technique.Techniques;
import dev.kultivator.technique.spec.run.Mastery;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import dev.kultivator.voice.Voice;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * The people of Dunia Bawah and what they do when spoken to (DESIGN 34.5.13): Meng Po's soup, the Ten Courts' judges
 * and guards ({@link Courts}), Raja Yama and the Book of Life and Death, the ghosts' errands ({@link GhostQuests}).
 * <p>
 * <b>Sup Meng Po</b> (Yuli Baochao: the soup of forgetting at the Naihe bridge), once per {@code soupCooldownDays}: forget
 * the Dao choice (half of that Dao's comprehension stays), or clear Heart Demon pressure and Qi Deviation, or forget the
 * selected technique and have its mastery refunded as insight (+1 comprehension of its Dao, else of one's own, per
 * {@link #REFUND_MASTERY} mastery points, at most {@link #REFUND_CAP}; the points are spent). Every bowl goes through the
 * same public paths the cheat commands use ({@code CultivationData}, {@code Comprehension.set}, {@code Deviation.clear},
 * {@code Techniques.refreshUnlocks}, {@code Mastery.setLevel}, {@code Stats.apply}). Refusing the soup is the way to the
 * courts.
 * <p>
 * <b>Bai and Hei Wuchang</b> on Teras Wangxiang show the way: the soul's progress (Meng Po, the courts, the Book) and the
 * direction of the next place, read from the loaded anchors of the court (the start piece may be turned any way).
 * <p>
 * <b>Buku Hidup dan Mati</b> (Journey to the West ch. 3: Sun Wukong strikes his name and every monkey's from the book):
 * after {@code courtsRequired} courts, below True Immortal a soul reward once (the Lentera Arwah, soul jade, Soul
 * Nurturing Wood); a True Immortal may once strike their own name, <b>Coret Namamu</b>. The mod's cheat-death charges
 * ({@code ImmortalPerks}) expose no capacity setter, so the perk is the documented fallback: a permanent +5 % max health
 * (attribute modifier {@code kultivator:coret_nama}, re-applied on join and respawn).
 */
public final class BawahPeople {

	public static final Identifier CORET_MODIFIER = Kultivator.id("coret_nama");
	public static final double CORET_HEALTH = 0.05D;
	/** Mastery points per point of comprehension Meng Po's soup refunds for a forgotten technique. */
	public static final int REFUND_MASTERY = 50;
	public static final double REFUND_CAP = 4.0D;

	// ================================================================== interaction

	static boolean interact(ServerPlayer player, ArwahEntity npc) {
		switch (npc.role()) {
			case ArwahEntity.MENG_PO -> openMengPo(player, npc);
			case ArwahEntity.HAKIM -> Courts.openCourt(player, npc);
			case ArwahEntity.RAJA_YAMA -> openBook(player, npc.getId());
			case ArwahEntity.KEPALA_LEMBU, ArwahEntity.MUKA_KUDA -> {
				DimensiVoice.say(npc, DimensiVoice.OX_HEAD, "disciple.m1.careful", "kultivator.dimensi.voice.ox_head");
				Msg.actionBar(player, Component.translatable("kultivator.dimensi.penjaga.bicara").withColor(0xC9B8FF));
			}
			case ArwahEntity.ARWAH -> GhostQuests.talk(player, npc);
			case ArwahEntity.WUCHANG_PUTIH, ArwahEntity.WUCHANG_HITAM -> openWuchang(player, npc);
			default -> {
				return false;
			}
		}
		return true;
	}

	// ================================================================== Meng Po

	private static void openMengPo(ServerPlayer player, ArwahEntity npc) {
		CultivationData data = Cultivators.get(player);
		CompoundTag bawah = SoulJourney.data(player);
		long day = DimensiData.day(player.level().getServer());
		long last = bawah.getLongOr("soup_day", -999L);
		long wait = Math.max(0L, last + DimensiConfig.get().soupCooldownDays - day);
		CompoundTag tag = new CompoundTag();
		tag.putLong("wait", wait);
		tag.putBoolean("visited", bawah.getBooleanOr("mengpo", false));
		Dao dao = data.dao();
		tag.putString("dao", dao == null ? "" : dao.id());
		tag.putDouble("comp", dao == null ? 0.0D : data.comprehension(dao));
		tag.putDouble("pressure", data.heartDemonPressure());
		tag.putInt("deviation", data.deviationTicks());
		String selected = data.selectedId();
		TechniqueSpec spec = selected == null || selected.isEmpty() ? null : TechniqueRegistry.byId(selected);
		tag.putString("tech", spec == null ? "" : spec.id());
		DimensiNet.open(player, DimensiNet.SCREEN_MENG_PO, npc.getId(), tag);
		DimensiVoice.say(npc, DimensiVoice.MENG_PO_OFFER, "disciple.f3.greet", "kultivator.dimensi.voice.meng_po_offer");
	}

	/** {@code soup:<dao|hati|teknik|tolak>}. */
	public static void soup(ServerPlayer player, ArwahEntity npc, String choice, boolean free) {
		CompoundTag bawah = SoulJourney.data(player);
		long day = DimensiData.day(player.level().getServer());
		if ("tolak".equals(choice)) {
			bawah.putBoolean("mengpo", true);
			DimensiData.dirty(player);
			Msg.chat(player, Component.translatable("kultivator.dimensi.mengpo.tolak").withColor(0xC9B8FF));
			if (npc != null) {
				DimensiVoice.say(npc, DimensiVoice.MENG_PO_REFUSE, "disciple.f3.farewell", "kultivator.dimensi.mengpo.tolak_kata");
			}
			return;
		}
		long last = bawah.getLongOr("soup_day", -999L);
		if (!free && day - last < DimensiConfig.get().soupCooldownDays) {
			Msg.chat(player, Component.translatable("kultivator.dimensi.mengpo.tunggu", last + DimensiConfig.get().soupCooldownDays - day)
					.withStyle(ChatFormatting.GRAY));
			return;
		}
		CultivationData data = Cultivators.get(player);
		boolean done;
		switch (choice) {
			case "dao" -> {
				Dao dao = data.dao();
				if (dao == null) {
					Msg.chat(player, Component.translatable("kultivator.dimensi.mengpo.tanpa_dao").withStyle(ChatFormatting.GRAY));
					return;
				}
				double kept = data.comprehension(dao) * 0.5D;
				data.setDao(null);
				for (Technique technique : Technique.values()) {
					if (technique.dao() != null && (technique.unlock() == Technique.Unlock.DAO || technique.unlock() == Technique.Unlock.BOOK)) {
						data.forget(technique);
					}
				}
				Comprehension.set(player, dao, kept);
				Techniques.refreshUnlocks(player);
				Stats.apply(player);
				DaoPaths.sendChoiceHint(player);
				Msg.chat(player, Component.translatable("kultivator.dimensi.mengpo.dao", dao.displayName(), Comprehension.format(kept))
						.withColor(0xC9B8FF));
				done = true;
			}
			case "hati" -> {
				data.setHeartDemonPressure(0.0D);
				Deviation.clear(player);
				Msg.chat(player, Component.translatable("kultivator.dimensi.mengpo.hati").withColor(0xC9B8FF));
				done = true;
			}
			case "teknik" -> {
				String id = data.selectedId();
				TechniqueSpec spec = id == null || id.isEmpty() ? null : TechniqueRegistry.byId(id);
				if (spec == null || !data.knows(spec.id())) {
					Msg.chat(player, Component.translatable("kultivator.dimensi.mengpo.tanpa_teknik").withStyle(ChatFormatting.GRAY));
					return;
				}
				int points = Mastery.points(data, spec.id());
				data.forget(spec.id());
				Mastery.setLevel(player, spec.id(), 0);
				Techniques.refreshUnlocks(player);
				Dao refundTo = spec.dao() != null ? spec.dao() : data.dao();
				double refund = Math.min(REFUND_CAP, points / (double) REFUND_MASTERY);
				if (refundTo != null && refund >= 0.05D) {
					Comprehension.grant(player, refundTo, refund);
					Msg.chat(player, Component.translatable("kultivator.dimensi.mengpo.teknik_kembali", spec.displayName(), points,
							refundTo.displayName(), Comprehension.format(refund)).withColor(0xC9B8FF));
				} else {
					Msg.chat(player, Component.translatable("kultivator.dimensi.mengpo.teknik", spec.displayName()).withColor(0xC9B8FF));
				}
				done = true;
			}
			default -> done = false;
		}
		if (!done) {
			return;
		}
		bawah.putLong("soup_day", day);
		bawah.putBoolean("mengpo", true);
		DimensiData.dirty(player);
		Cultivators.syncNow(player);
		DimensiNet.fx(player.level(), DimensiNet.FX_SOUP, player.position(), 0, 32.0D);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GENERIC_DRINK.value(), SoundSource.PLAYERS, 1.0F, 0.8F);
		Advancements.grant(player, "dunia/meng_po_soup");
	}

	// ================================================================== Raja Yama and the Book

	public static void openBook(ServerPlayer player, int entity) {
		int passed = Courts.passed(player);
		int required = DimensiConfig.get().courtsRequired;
		if (passed < required) {
			Msg.chat(player, Component.translatable("kultivator.dimensi.yama.belum", passed, required).withColor(0xD0A0A0));
			return;
		}
		CompoundTag bawah = SoulJourney.data(player);
		CultivationData data = Cultivators.get(player);
		CompoundTag tag = new CompoundTag();
		tag.putString("name", player.getName().getString());
		tag.putInt("realm", data.realm().ordinal());
		tag.putBoolean("immortal", data.realm() == Realm.TRUE_IMMORTAL);
		tag.putBoolean("rewarded", bawah.getBooleanOr("buku_hadiah", false));
		tag.putBoolean("struck", bawah.getBooleanOr("coret", false));
		tag.putBoolean("perk", DimensiConfig.get().yamaPerk);
		tag.putLong("days", player.getStats().getValue(net.minecraft.stats.Stats.CUSTOM.get(net.minecraft.stats.Stats.PLAY_TIME)) / 24000L);
		tag.putInt("passed", passed);
		DimensiNet.open(player, DimensiNet.SCREEN_BOOK, entity, tag);
		DimensiNet.fx(player.level(), DimensiNet.FX_BOOK, player.position(), 0, 32.0D);
		Advancements.grant(player, "dunia/ten_courts");
		if (player.level().getEntity(entity) instanceof ArwahEntity yama && yama.role() == ArwahEntity.RAJA_YAMA) {
			yama.talk(60);
			DimensiVoice.say(yama, DimensiVoice.YAMA_BOOK, "boss.awaken", "kultivator.dimensi.voice.yama_book");
		}
	}

	/** {@code book:hadiah} (below True Immortal, once) or {@code book:coret} (True Immortal, once). */
	public static void book(ServerPlayer player, String what, boolean free) {
		if (!free && Courts.passed(player) < DimensiConfig.get().courtsRequired) {
			return;
		}
		CompoundTag bawah = SoulJourney.data(player);
		CultivationData data = Cultivators.get(player);
		if ("hadiah".equals(what)) {
			if (bawah.getBooleanOr("buku_hadiah", false) && !free) {
				return;
			}
			bawah.putBoolean("buku_hadiah", true);
			DimensiData.give(player, new ItemStack(DimensiItems.LENTERA_ARWAH));
			DimensiData.give(player, new ItemStack(DimensiItems.GIOK_ARWAH, 8));
			ItemStack wood = Dimensi.itemById("kultivator:soul_nurturing_wood", 2);
			if (!wood.isEmpty()) {
				DimensiData.give(player, wood);
			}
			DimensiData.giveNb(player, 9L * 20L);
			Msg.chat(player, Component.translatable("kultivator.dimensi.buku.hadiah").withColor(0xE8D9A8));
		} else if ("coret".equals(what)) {
			if (!DimensiConfig.get().yamaPerk || (!free && data.realm() != Realm.TRUE_IMMORTAL) || (bawah.getBooleanOr("coret", false) && !free)) {
				return;
			}
			bawah.putBoolean("coret", true);
			applyPerk(player);
			Msg.chat(player, Component.translatable("kultivator.dimensi.buku.coret").withColor(0xFFE9A8));
			Voice.heaven(player, "heaven.true_immortal");
			Advancements.grant(player, "dunia/name_struck");
		} else {
			return;
		}
		DimensiData.dirty(player);
		DimensiNet.fx(player.level(), DimensiNet.FX_BOOK, player.position(), 1, 48.0D);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 0.7F);
	}

	/** The Coret Namamu perk on this body (join, respawn, the moment it is struck). */
	public static void applyPerk(ServerPlayer player) {
		AttributeInstance health = player.getAttribute(Attributes.MAX_HEALTH);
		if (health == null) {
			return;
		}
		if (DimensiData.extra(player, DimensiData.BAWAH).getBooleanOr("coret", false)) {
			// permanent: saved and loaded before Health, so a full bar is not clamped to the unboosted max on relog
			health.addOrReplacePermanentModifier(new AttributeModifier(CORET_MODIFIER, CORET_HEALTH, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		} else {
			health.removeModifier(CORET_MODIFIER);
		}
	}

	// ================================================================== spawning

	/** Makes an Arwah of {@code role} at {@code at} facing {@code yaw}; null when the budget refuses. */
	static @Nullable ArwahEntity spawn(ServerLevel level, Vec3 at, float yaw, int role, int variant, int realm, @Nullable UUID anchor,
									   boolean budget) {
		if (budget && !DuniaBudget.mayAdd(level, DuniaBudget.NPC, at)) {
			return null;
		}
		ArwahEntity npc = DimensiEntities.ARWAH.create(level, EntitySpawnReason.EVENT);
		if (npc == null) {
			return null;
		}
		npc.setup(role, lookFor(role, level.getRandom().nextInt(9)), variant, realm);
		npc.snapTo(at.x, at.y, at.z, yaw, 0.0F);
		npc.setYHeadRot(yaw);
		npc.setYBodyRot(yaw);
		int leash = role == ArwahEntity.HANTU_LAPAR || role == ArwahEntity.ARWAH ? 14
				: role == ArwahEntity.WUCHANG_PUTIH || role == ArwahEntity.WUCHANG_HITAM ? 8 : 4;
		npc.setHome(BlockPos.containing(at), leash);
		npc.setAnchor(anchor);
		if (!level.addFreshEntity(npc)) {
			return null;
		}
		DuniaBudget.track(DuniaBudget.NPC, npc);
		return npc;
	}

	/** Meng Po and the judges keep their own faces (the client keys the look on the role). */
	private static int lookFor(int role, int random) {
		return switch (role) {
			case ArwahEntity.MENG_PO -> 3;
			case ArwahEntity.RAJA_YAMA -> 1;
			case ArwahEntity.WUCHANG_PUTIH -> 5;
			case ArwahEntity.WUCHANG_HITAM -> 2;
			default -> random;
		};
	}

	/** The realm ordinal monsters scale to near {@code at} (the strongest player within 48 b; Nascent Soul at least). */
	static int realmNear(ServerLevel level, Vec3 at) {
		int best = Realm.NASCENT_SOUL.ordinal();
		for (ServerPlayer player : level.players()) {
			if (player.position().distanceToSqr(at) < 48.0D * 48.0D) {
				best = Math.max(best, Cultivators.get(player).realm().ordinal());
			}
		}
		return best;
	}

	/** A hungry ghost was slain: sometimes it leaves its jade. */
	static void ghostSlain(ServerLevel level, ServerPlayer killer, ArwahEntity ghost) {
		boolean jade = level.getRandom().nextFloat() < 0.2F;
		if (jade) {
			DimensiData.give(killer, new ItemStack(DimensiItems.GIOK_ARWAH));
		}
		DimensiNet.fx(level, DimensiNet.FX_GHOST_FADE, ghost.position(), jade ? 1 : 0, 48.0D);
	}

	// ================================================================== Bai and Hei Wuchang

	/** The guidance scroll: the soul's progress and the direction of its next place. */
	static void openWuchang(ServerPlayer player, ArwahEntity npc) {
		CompoundTag bawah = SoulJourney.data(player);
		ServerLevel level = player.level();
		int passed = Courts.passed(player);
		int required = DimensiConfig.get().courtsRequired;
		boolean mengpo = bawah.getBooleanOr("mengpo", false);
		CompoundTag tag = new CompoundTag();
		tag.putBoolean("hitam", npc.role() == ArwahEntity.WUCHANG_HITAM);
		tag.putBoolean("soul", SoulJourney.isSoul(player));
		tag.putLong("left", SoulJourney.soulLeft(player));
		tag.putLong("luka", SoulJourney.lukaLeft(player));
		tag.putBoolean("mengpo", mengpo);
		tag.putInt("passed", passed);
		tag.putInt("required", required);
		tag.putBoolean("eclipse", DimensiSky.eclipse(level.getServer()));
		tag.putInt("tier", dev.kultivator.dunia.dimensi.DimensiChatter.tier(player));
		// the next place: Meng Po first, then the next court, then King Yama
		String next;
		DuniaAnchorEntity target;
		if (!mengpo) {
			next = "mengpo";
			target = DuniaAnchors.nearest(level, player.position(), "bawah/meng_po", 400.0D);
		} else if (passed < required) {
			next = "court";
			target = null;
			int n = passed + 1;
			for (DuniaAnchorEntity anchor : DuniaAnchors.loaded(level, "bawah/pengadilan")) {
				if (anchor.data().getIntOr("court", 0) == n
						&& (target == null || anchor.distanceToSqr(player) < target.distanceToSqr(player))) {
					target = anchor;
				}
			}
			tag.putInt("court", n);
		} else {
			next = "yama";
			target = DuniaAnchors.nearest(level, player.position(), "bawah/yama", 400.0D);
		}
		tag.putString("next", next);
		if (target != null) {
			double dx = target.getX() - player.getX();
			double dz = target.getZ() - player.getZ();
			tag.putString("dir", GhostQuests.compass(dx, dz));
			tag.putInt("dist", (int) Math.round(Math.sqrt(dx * dx + dz * dz)));
		}
		DimensiNet.open(player, DimensiNet.SCREEN_WUCHANG, npc.getId(), tag);
		DimensiVoice.sayReused(npc, npc.role() == ArwahEntity.WUCHANG_HITAM ? "disciple.m3.careful" : "disciple.m2.greet_alt",
				npc.role() == ArwahEntity.WUCHANG_HITAM ? "kultivator.dimensi.voice.wuchang_hitam" : "kultivator.dimensi.voice.wuchang_putih");
		if (DimensiChatter.immortal(player)) {
			npc.bow(40);
		}
	}

	private BawahPeople() {
	}
}
