package dev.kultivator.dunia.reruntuhan.gua;

import dev.kultivator.dunia.DuniaAnchorEntity;
import dev.kultivator.dunia.DuniaAnchors;
import dev.kultivator.dunia.reruntuhan.AnchorRun;
import dev.kultivator.dunia.reruntuhan.Frame;
import dev.kultivator.dunia.reruntuhan.REntities;
import dev.kultivator.dunia.reruntuhan.RFx;
import dev.kultivator.dunia.reruntuhan.RItems;
import dev.kultivator.dunia.reruntuhan.RRewards;
import dev.kultivator.dunia.reruntuhan.Reruntuhan;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import dev.kultivator.voice.Voice;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The Aula Warisan of a Gua Leluhur (anchor {@code reruntuhan/warisan}, DESIGN 34.5.5): before the seated bones of the
 * ancestor stands its remnant soul ({@link SisaJiwaEntity}). Speaking to it after defeating the guardian grants the
 * inheritance, once per player per ruin (kept in this anchor's state {@code claimed}): Kecil 10-30 NB, herbs of
 * 100-300 years and a Low or Mid core; Agung 80-200 NB, herbs of 300-1 000 years, a High core and, at 50 %, a Giok
 * Warisan of the ruin's Dao; Kuno 600-1 500 NB, a King core, a Giok Warisan and a Kitab Formasi Kuno. The chests of the
 * hall carry capped loot tables of their own.
 */
public final class HallRun extends AnchorRun {

	private static final String[][] THEMES = {{"sword", "life", "body"}, {"fire", "ice", "lightning", "sword"}, {"space", "shadow", "universal"}};
	private static final int[] SKINS = {3, 7, 5};
	private final int tier;
	private @Nullable SisaJiwaEntity soul;
	private int speak;

	public HallRun(DuniaAnchorEntity anchor, ServerLevel level) {
		super(anchor, level);
		this.tier = Mth.clamp(anchor.data().getIntOr("tier", 1), 1, 3);
	}

	/** The ruin's Dao (the theme of its jade), fixed by the site. */
	public String theme() {
		String[] list = THEMES[tier - 1];
		return list[Math.floorMod((int) (anchor.site() ^ (anchor.site() >>> 32)), list.length)];
	}

	@Override
	protected void spawn() {
		int[] seat = Frame.triple(data(), "seat", 0);
		Vec3 at = seat == null ? centre() : frame.bottom(seat[0], seat[1], seat[2] + 1);
		SisaJiwaEntity made = make(REntities.SISA_JIWA, at, frame.yaw(0.0F));
		if (made == null) {
			return;
		}
		made.setup(SKINS[tier - 1] + Math.floorMod((int) anchor.site(), 2), RuinLooks.robe(tier), tier);
		made.setListener((player, part, attack) -> talk(player));
		if (add(made)) {
			soul = made;
			RFx.at(level, at.add(0.0D, 1.0D, 0.0D), RFx.SOUL_APPEAR, RuinLooks.robe(tier), 0, 1.0F);
		}
	}

	@Override
	protected void tick(long now) {
		SisaJiwaEntity s = soul;
		if (s != null && speak > 0 && --speak == 0) {
			s.setGlow(0);
		}
	}

	private boolean claimed(UUID player) {
		for (Tag tag : state().getListOrEmpty("claimed")) {
			if (tag instanceof StringTag s && player.toString().equals(s.value())) {
				return true;
			}
		}
		return false;
	}

	private boolean victor(ServerPlayer player) {
		DuniaAnchorEntity guardian = DuniaAnchors.nearest(level, centre(), "reruntuhan/penjaga", 48.0D);
		return guardian == null || GuardianRun.isVictor(guardian, player.getUUID());
	}

	private void talk(ServerPlayer player) {
		SisaJiwaEntity s = soul;
		if (s == null) {
			return;
		}
		s.setGlow(1);
		speak = 60;
		String who = "entity.kultivator.sisa_jiwa.tier." + tier;
		if (claimed(player.getUUID())) {
			Voice.say(s, "elder.welcome", null);
			Msg.chat(player, line(who, "kultivator.reruntuhan.warisan.again"));
			return;
		}
		if (!victor(player)) {
			Voice.say(s, "elder.advice", null);
			Msg.chat(player, line(who, "kultivator.reruntuhan.warisan.unproven"));
			return;
		}
		grant(player);
	}

	private static Component line(String who, String key) {
		return Component.translatable(who).withStyle(ChatFormatting.LIGHT_PURPLE).append(Component.literal(": ").withStyle(ChatFormatting.DARK_GRAY))
				.append(Component.translatable(key).withStyle(ChatFormatting.GRAY));
	}

	/** The inheritance (once per player per ruin). */
	public void grant(ServerPlayer player) {
		ListTag claimed = state().getListOrEmpty("claimed").copy();
		claimed.add(StringTag.valueOf(player.getUUID().toString()));
		while (claimed.size() > 128) {
			claimed.remove(0);
		}
		state().put("claimed", claimed);
		SisaJiwaEntity s = soul;
		if (s != null) {
			s.setGlow(2);
			speak = 100;
			Voice.say(s, "elder.inheritance", null);
		}
		RandomSource random = player.getRandom();
		double scale = 1.0D;
		List<ItemStack> items = new ArrayList<>();
		int nb;
		switch (tier) {
			case 2 -> {
				nb = RRewards.roll(random, 80, 200, scale);
				items.add(RRewards.herb(random, 300, 1000));
				items.add(RRewards.herb(random, 300, 1000));
				items.add(RRewards.core(random, 3));
				if (random.nextBoolean()) {
					items.add(RRewards.jade(theme()));
				}
			}
			case 3 -> {
				nb = RRewards.roll(random, 600, 1500, scale);
				items.add(RRewards.herb(random, 1000, 3000));
				items.add(RRewards.herb(random, 1000, 3000));
				items.add(RRewards.core(random, 4));
				items.add(RRewards.jade(theme()));
				items.add(new ItemStack(RItems.KITAB_FORMASI_KUNO));
			}
			default -> {
				nb = RRewards.roll(random, 10, 30, scale);
				items.add(RRewards.herb(random, 100, 300));
				items.add(RRewards.core(random, random.nextBoolean() ? 1 : 2));
			}
		}
		RRewards.stones(player, nb);
		Msg.chat(player, Component.translatable("kultivator.reruntuhan.warisan.stones", nb).withStyle(ChatFormatting.YELLOW));
		RRewards.giveAll(player, items, "kultivator.reruntuhan.warisan.items");
		Msg.chat(player, line("entity.kultivator.sisa_jiwa.tier." + tier, "kultivator.reruntuhan.warisan.granted." + tier));
		RFx.at(level, centre().add(0.0D, 1.5D, 0.0D), RFx.INHERIT, tier, 0, 2.0F);
		level.playSound(null, centre().x, centre().y, centre().z, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.8F, 1.0F);
		level.playSound(null, centre().x, centre().y, centre().z, SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 1.0F, 0.7F);
		Advancements.grant(player, "dunia/ruin_cleared");
		Reruntuhan.markRuin(player, tier);
	}

	/** {@code uji gua reset}: forgets who received the inheritance. */
	public void testReset() {
		state().remove("claimed");
	}

	@Override
	protected void despawn() {
		soul = null;
		super.despawn();
	}
}
