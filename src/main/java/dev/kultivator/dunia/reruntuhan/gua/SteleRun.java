package dev.kultivator.dunia.reruntuhan.gua;

import dev.kultivator.dunia.DuniaAnchorEntity;
import dev.kultivator.dunia.reruntuhan.AnchorRun;
import dev.kultivator.dunia.reruntuhan.REntities;
import dev.kultivator.dunia.reruntuhan.TekaPartEntity;
import dev.kultivator.util.Msg;
import dev.kultivator.voice.Voice;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;

/**
 * The riddle stele of a Gua Leluhur's antechamber (anchor {@code reruntuhan/prasasti}, DESIGN 34.5.5): read with a
 * click, it tells the ruin's story and the shape of its trials (lang {@code kultivator.reruntuhan.gua.prasasti.<tier>.*}).
 * The first reader hears the ancestor's welcome ({@code elder.welcome}).
 */
public final class SteleRun extends AnchorRun {

	private final int tier;

	public SteleRun(DuniaAnchorEntity anchor, ServerLevel level) {
		super(anchor, level);
		this.tier = Mth.clamp(anchor.data().getIntOr("tier", 1), 1, 3);
	}

	@Override
	protected void spawn() {
		TekaPartEntity stele = make(REntities.TEKA_PLAT, frame.bottom(0, 0, -2), frame.yaw(0.0F));
		if (stele == null) {
			return;
		}
		stele.setRole(TekaPartEntity.STELE);
		stele.setCenter(centre());
		stele.setArg(100 + tier);
		stele.setListener((player, part, attack) -> read(player, part));
		add(stele);
	}

	private void read(ServerPlayer player, TekaPartEntity part) {
		String base = "kultivator.reruntuhan.gua.prasasti." + tier;
		Msg.chat(player, Component.translatable(base + ".title").withStyle(ChatFormatting.GOLD));
		Msg.chat(player, Component.translatable(base + ".text").withStyle(ChatFormatting.GRAY));
		Msg.chat(player, Component.translatable("kultivator.reruntuhan.gua.source").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
		level.playSound(null, part.getX(), part.getY(), part.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.8F, 0.7F);
		if (!state().getBooleanOr("welcomed", false)) {
			state().putBoolean("welcomed", true);
			Voice.inner(player, "elder.welcome", Component.translatable("entity.kultivator.sisa_jiwa.tier." + tier));
		}
	}

	@Override
	protected void tick(long now) {
	}
}
